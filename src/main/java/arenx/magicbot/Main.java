package arenx.magicbot;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Inventories;
import com.pokegoapi.api.inventory.Item;
import com.pokegoapi.api.inventory.Stats;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.fort.FortDetails;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.player.PlayerProfile;
import com.pokegoapi.api.pokemon.HatchedEgg;
import com.pokegoapi.auth.GoogleUserCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.google.common.geometry.S2LatLng;
import com.pokegoapi.util.PokeNames;

import POGOProtos.Data.PlayerDataOuterClass.PlayerData;
import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Responses.FortSearchResponseOuterClass.FortSearchResponse;
import POGOProtos.Networking.Responses.RecycleInventoryItemResponseOuterClass.RecycleInventoryItemResponse;
import okhttp3.OkHttpClient;

public class Main {

	private static Logger logger = LoggerFactory.getLogger(Main.class);

	private static PokemonGo go;
	private static PlayerData playerData;
	private static Inventories inventories;
	private static MapObjects mapObjects;
	private static Stats stats;
	private static double longitude = Config.instance.getDefaultLongitude();
	private static double latitude = Config.instance.getDefaultLatitude();
	private static double altitude = Config.instance.getDefaultAltitude();

	// key = Pokestop.getId(), value = Pokestop.getDetails
	private static Map<String, FortDetails> pokestopMap = new TreeMap<String, FortDetails>();

	public static void main(String[] argv) throws LoginFailedException, RemoteServerException {

		// PokemonGo go = login();
		//
		// PlayerProfile pf = go.getPlayerProfile();
		//
		// PlayerData pd = pf.getPlayerData();
		//
		//
		// System.out.println("pd.getAvatar().toString()="+pd.getAvatar().toString());
		// System.out.println("pd.getUsername()="+pd.getUsername());

		start();
	}

	private static void start() {
		
		RestoreLastState();
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		    	SaveCurrentState();
		    }
		 });

		logger.info("login ...");
		go = login();
		go.setLocation(latitude, longitude, altitude);

		sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
		refreshPlayerData();
		showPlayData();
		
		sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
		refreshStats();
		showStatsData();

		sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
		refreshInventories();
		showInventories();

		sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
		refreshMapObjects();
		showMapObjects();
		
		Pokestop oldTargetStop = null;
		Pokestop newTargetStop;
		
		int round = 0;
		
		while(true){
			newTargetStop = getNextLoopPokestop();			
			
			if (newTargetStop==null) {
				oldTargetStop = null;
				logger.info("[Moving] target set to default location");
			} else if (oldTargetStop==null || !oldTargetStop.getId().equals(newTargetStop.getId())) {
				oldTargetStop = newTargetStop;
				String name = pokestopMap.containsKey(oldTargetStop.getId()) ? pokestopMap.get(oldTargetStop.getId()).getName()
						: oldTargetStop.getId();
				logger.info("[Moving] target set to [{}] distance={}", name, oldTargetStop.getDistance());
			}
			
			if (oldTargetStop!=null && oldTargetStop.canLoot()){
				if(logger.isDebugEnabled()){
					String name = pokestopMap.containsKey(oldTargetStop.getId()) ? pokestopMap.get(oldTargetStop.getId()).getName()
							: oldTargetStop.getId();
					logger.debug("[Moving] try loot [{}]", name);
				}
				
				sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
				PokestopLootResult result = loot(oldTargetStop);
				showPokestopLootResult(result);
				
				if (!result.wasSuccessful()) {
					String name = pokestopMap.containsKey(oldTargetStop.getId()) ? pokestopMap.get(oldTargetStop.getId()).getName()
							: oldTargetStop.getId();
					logger.error("[LootResult] failed to loot [{}] result:{}", name, result.getResult());
					break;
				}
				
				if (result.getResult() == FortSearchResponse.Result.INVENTORY_FULL) {
					logger.info("[LootResult] bag is full");
					
					sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
					refreshInventories();
					showInventories();
					
					sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
					removeBackBagItems();
					showInventories();
				}
				
				round = 0;
				sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
				refreshMapObjects();
				
				continue;
			}
			
			double dist_distance;
			double dist_longitude;
			double dist_latitude;
			
			if (oldTargetStop!=null) {				
				dist_distance = oldTargetStop.getDistance();				
				dist_longitude = oldTargetStop.getLongitude();
				dist_latitude = oldTargetStop.getLatitude();	
			} else {
				dist_longitude = Config.instance.getDefaultLongitude();
				dist_latitude = Config.instance.getDefaultLatitude();
				dist_distance = distance(Config.instance.getDefaultLatitude(), Config.instance.getDefaultLongitude(), latitude, longitude);
			}
			
			if (Double.isNaN(dist_latitude) || Double.isNaN(dist_longitude) || Double.isNaN(dist_distance)) {
				logger.warn("[Moving] detect NaN la={} lo={} di={}", dist_latitude, dist_longitude, dist_distance);
				dist_longitude = longitude + 0.0001;
				dist_latitude = latitude + 0.0001;
				dist_distance = distance(dist_latitude, dist_longitude, latitude, longitude);
			}
			
			double next_longitude;
			double next_latitude;
			
			if (dist_distance / Config.instance.getSpeedPerSecond() < 1.0) {
				next_longitude = dist_longitude;
				next_latitude = dist_latitude;
			} else {
				next_longitude = longitude + (dist_longitude - longitude) * Config.instance.getSpeedPerSecond() / dist_distance;
				next_latitude = latitude + (dist_latitude - latitude) * Config.instance.getSpeedPerSecond() / dist_distance;
			}
			
			double travel_distance = distance(latitude, longitude, next_latitude, next_longitude);
			
			if (logger.isDebugEnabled()){
				String name = oldTargetStop == null ? "default location"
						: pokestopMap.containsKey(oldTargetStop.getId()) ? pokestopMap.get(oldTargetStop.getId()).getName()
						: oldTargetStop.getId();
				
				logger.debug("[Moving] heading to [{}] distance={} ({},{}) -> ({},{})", name, travel_distance, latitude, longitude, next_latitude, next_longitude);
			}
			
			latitude = next_latitude;
			longitude = next_longitude;
			altitude = RandomUtils.nextDouble(2, 10);
			
			sleep(1000);
			go.setLocation(latitude, longitude, altitude);
			
			if (round>RandomUtils.nextInt(5, 10)) {
				refreshMapObjects();
				round = 0;
			}
			
			round++;
			
//			break;
		}
	}
	
	private static void removeBackBagItems(){
		
		List<Entry<ItemId, Integer>> ids = new ArrayList();
		
		Item berry = inventories.getItemBag().getItem(ItemId.ITEM_RAZZ_BERRY);		
		
		if (berry!=null && berry.getCount() > Config.instance.getBackBag().getMaxBerryToKeep()) {
			ids.add(new SimpleEntry<ItemId, Integer>(berry.getItemId(), berry.getCount() - Config.instance.getBackBag().getMaxBerryToKeep()));
		}
		
		Item revive = inventories.getItemBag().getItem(ItemId.ITEM_REVIVE);		
		
		if (revive!=null && revive.getCount() > Config.instance.getBackBag().getMaxReviveToKeep()) {
			ids.add(new SimpleEntry<ItemId, Integer>(revive.getItemId(), revive.getCount() - Config.instance.getBackBag().getMaxReviveToKeep()));
		}
		

		Item ball_poke = inventories.getItemBag().getItem(ItemId.ITEM_POKE_BALL);
		Item ball_great = inventories.getItemBag().getItem(ItemId.ITEM_GREAT_BALL);
		Item ball_ultra = inventories.getItemBag().getItem(ItemId.ITEM_ULTRA_BALL);
		Item ball_master = inventories.getItemBag().getItem(ItemId.ITEM_MASTER_BALL);
		
		int ball_count = 0;
		if (ball_poke != null) {
			ball_count += ball_poke.getCount();
		}
		if (ball_great != null) {
			ball_count += ball_great.getCount();
		}
		if (ball_ultra != null) {
			ball_count += ball_ultra.getCount();
		}
		if (ball_master != null) {
			ball_count += ball_master.getCount();
		}
		ball_count -= Config.instance.getBackBag().getMaxBallToKeep();
		
		if (ball_count > 0) {
			if (ball_poke != null && ball_count > 0) {
				if (ball_poke.getCount() > ball_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(ball_poke.getItemId(), ball_count));
					ball_count = 0;
				} else if (ball_poke.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(ball_poke.getItemId(), ball_poke.getCount()));
					ball_count -= ball_poke.getCount();
				}
			}
			if (ball_great != null && ball_count > 0) {
				if (ball_great.getCount() > ball_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(ball_great.getItemId(), ball_count));
					ball_count = 0;
				} else if (ball_great.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(ball_great.getItemId(), ball_great.getCount()));
					ball_count -= ball_great.getCount();
				}
			}
			if (ball_ultra != null && ball_count > 0) {
				if (ball_ultra.getCount() > ball_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(ball_ultra.getItemId(), ball_count));
					ball_count = 0;
				} else if (ball_ultra.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(ball_ultra.getItemId(), ball_ultra.getCount()));
					ball_count -= ball_ultra.getCount();
				}
			}
			if (ball_master != null && ball_count > 0) {
				if (ball_master.getCount() > ball_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(ball_master.getItemId(), ball_count));
					ball_count = 0;
				} else if (ball_master.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(ball_master.getItemId(), ball_master.getCount()));
					ball_count -= ball_master.getCount();
				}
			}
		}
		

		Item potion = inventories.getItemBag().getItem(ItemId.ITEM_POTION);
		Item potion_hyper = inventories.getItemBag().getItem(ItemId.ITEM_HYPER_POTION);
		Item potion_super = inventories.getItemBag().getItem(ItemId.ITEM_SUPER_POTION);
		Item potion_max = inventories.getItemBag().getItem(ItemId.ITEM_MAX_POTION);
		
		int potion_count = 0;
		if (potion != null) {
			potion_count += potion.getCount();
		}
		if (potion_hyper != null) {
			potion_count += potion_hyper.getCount();
		}
		if (potion_super != null) {
			potion_count += potion_super.getCount();
		}
		if (potion_max != null) {
			potion_count += potion_max.getCount();
		}
		potion_count -= Config.instance.getBackBag().getMaxPotionToKeep();
		
		if (potion_count > 0) {
			if (potion != null && potion_count > 0) {
				if (potion.getCount() > potion_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion.getItemId(), potion_count));
					potion_count = 0;
				} else if (potion.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(potion.getItemId(), potion.getCount()));
					potion_count -= potion.getCount();
				}
			}
			if (potion_hyper != null && potion_count > 0) {
				if (potion_hyper.getCount() > potion_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion_hyper.getItemId(), potion_count));
					potion_count = 0;
				} else if (potion_hyper.getCount() > 0) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion_hyper.getItemId(), potion_hyper.getCount()));
					potion_count -= potion_hyper.getCount();
				}
			}
			if (potion_super != null && potion_count > 0) {
				if (potion_super.getCount() > potion_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion_super.getItemId(), potion_count));
					potion_count = 0;
				} else if (potion_super.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(potion_super.getItemId(), potion_super.getCount()));
					potion_count -= potion_super.getCount();
				}
			}
			if (potion_max != null && potion_count > 0) {
				if (potion_max.getCount() > potion_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion_max.getItemId(), potion_count));
					potion_count = 0;
				} else if (potion_max.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(potion_max.getItemId(), potion_max.getCount()));
					potion_count -= potion_max.getCount();
				}
			}
		}
		
		ids.forEach(e->{
			logger.info("[Inventories][ItemBag] remove:{} count:{}", e.getKey(), e.getValue());			
			
			int retry = 1;
			
			PokestopLootResult result = null;

			while (retry <= Config.instance.getMaxRetryWhenServerError()) {
				try {
					RecycleInventoryItemResponse.Result r = inventories.getItemBag().removeItem(e.getKey(), e.getValue());
					switch (r) {
					case SUCCESS:
						break;
					case ERROR_CANNOT_RECYCLE_INCUBATORS:
					case ERROR_NOT_ENOUGH_COPIES:					
					case UNRECOGNIZED:
					case UNSET:
					default:
						logger.error("Failed to recycle remove:{} count:{} {}", e.getKey(), e.getValue(), r);
						break;
					}
					sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
					break;
				} catch (Exception e1) {
					logger.warn("Faile to remove item:{} count: {}; retry {}/{}", e.getKey(), e.getValue(), retry,
							Config.instance.getMaxRetryWhenServerError());
					sleep(RandomUtils.nextLong(2000, 5000));
				}
				retry++;
			}

			if (retry == Config.instance.getMaxRetryWhenServerError()) {
				String m = "Failed to remove item:" + e.getKey() + " count:" + e.getValue();
				logger.error(m);
				throw new RuntimeException(m);
			}
			
		});
	}
	
	private static double distance(double la_1m, double lo_1, double la_2, double lo_2){
		S2LatLng o_1 = S2LatLng.fromDegrees(la_1m, lo_1);
		S2LatLng o_2 = S2LatLng.fromDegrees(la_2, lo_2);
		return o_1.getEarthDistance(o_2);
	}
	
	private static void SaveCurrentState(){
		TmpData.instance.setLastAltitude(altitude);
		TmpData.instance.setLastLongitude(longitude);
		TmpData.instance.setLastLatitude(latitude);
		
		TmpData.instance.saveToFile();
	}
	
	private static void RestoreLastState(){
		
		if (TmpData.instance.getLastAltitude() !=null) {
			altitude = TmpData.instance.getLastAltitude();
			logger.info("[Restore] altitude:{}", altitude);
		}
		
		if (TmpData.instance.getLastLongitude() !=null) {
			longitude = TmpData.instance.getLastLongitude();
			logger.info("[Restore] longitude:{}", longitude);
		}
		
		if (TmpData.instance.getLastLatitude() !=null) {
			latitude = TmpData.instance.getLastLatitude();
			logger.info("[Restore] latitude:{}", latitude);
		}		
	}
	
	private static Pokestop getNextLoopPokestop(){
		Optional<Pokestop> near = mapObjects.getPokestops().stream()
			.filter(stop->stop.canLoot(true))
			.sorted((a,b)->Double.compare(a.getDistance(), b.getDistance()))
			.findFirst();
		
		return near.isPresent() ? near.get() : null;
	}
	
	private static PokestopLootResult loot(Pokestop stop) {
		int retry = 1;
		
		PokestopLootResult result = null;

		while (retry <= Config.instance.getMaxRetryWhenServerError()) {
			try {
				result = stop.loot();
				break;
			} catch (Exception e) {
				logger.warn("Faile to loot; retry {}/{}", retry,
						Config.instance.getMaxRetryWhenServerError());
				sleep(RandomUtils.nextLong(2000, 5000));
			}
			retry++;
		}

		if (retry == Config.instance.getMaxRetryWhenServerError()) {
			String m = "Failed to get player data";
			logger.error(m);
			throw new RuntimeException(m);
		}
		
		return result;

	}

	private static void refreshPlayerData() {
		int retry = 1;

		while (retry <= Config.instance.getMaxRetryWhenServerError()) {
			try {
				playerData = go.getPlayerProfile().getPlayerData();
				break;
			} catch (Exception e) {
				logger.warn("Faile to get PlayerData; retry {}/{}", retry,
						Config.instance.getMaxRetryWhenServerError());
				sleep(RandomUtils.nextLong(2000, 5000));
			}
			retry++;
		}

		if (retry == Config.instance.getMaxRetryWhenServerError()) {
			String m = "Failed to get player data";
			logger.error(m);
			throw new RuntimeException(m);
		}

	}
	
	private static void refreshStats() {
		int retry = 1;

		while (retry <= Config.instance.getMaxRetryWhenServerError()) {
			try {
				stats = go.getPlayerProfile().getStats();
				break;
			} catch (Exception e) {
				logger.warn("Faile to get stats; retry {}/{}", retry,
						Config.instance.getMaxRetryWhenServerError());
				sleep(RandomUtils.nextLong(2000, 5000));
			}
			retry++;
		}

		if (retry == Config.instance.getMaxRetryWhenServerError()) {
			String m = "Failed to get stats";
			logger.error(m);
			throw new RuntimeException(m);
		}

	}

	private static void refreshInventories() {
		int retry = 1;

		while (retry <= Config.instance.getMaxRetryWhenServerError()) {
			try {
				if (inventories==null) {
					inventories = go.getInventories();
				} else {
					inventories.updateInventories();
				}
				break;
			} catch (Exception e) {
				logger.warn("Faile to get Inventories; retry {}/{}", retry,
						Config.instance.getMaxRetryWhenServerError());
				sleep(RandomUtils.nextLong(2000, 5000));
			}
			retry++;
		}

		if (retry == Config.instance.getMaxRetryWhenServerError()) {
			String m = "Failed to get Inventories";
			logger.error(m);
			throw new RuntimeException(m);
		}

	}

	private static void refreshMapObjects() {
		int retry = 1;

		while (retry <= Config.instance.getMaxRetryWhenServerError()) {
			try {
				mapObjects = go.getMap().getMapObjects();
				break;
			} catch (Exception e) {
				logger.warn("Faile to get MapObjects; retry {}/{}", retry,
						Config.instance.getMaxRetryWhenServerError());
				sleep(RandomUtils.nextLong(2000, 5000));
			}
			retry++;
		}

		if (retry == Config.instance.getMaxRetryWhenServerError()) {
			String m = "Failed to get MapObjects";
			logger.error(m);
			throw new RuntimeException(m);
		}

		mapObjects.getPokestops().stream().filter(stop -> pokestopMap.containsKey(stop.getId()) == false)
				.forEach(stop -> {
					int retry_stop = 1;

					while (retry_stop <= Config.instance.getMaxRetryWhenServerError()) {
						try {
							FortDetails fd = stop.getDetails();
							pokestopMap.put(stop.getId(), fd);
							logger.info("[Pokestop] Cache details of [{}] in memory. pokestopMap.size: {}",
									fd.getName(), pokestopMap.size());
							break;
						} catch (Exception e) {
							logger.warn("Faile to get pokestop detail; retry {}/{}", retry_stop,
									Config.instance.getMaxRetryWhenServerError());
							sleep(RandomUtils.nextLong(2000, 5000));
						}
						retry_stop++;
					}

					if (retry_stop == Config.instance.getMaxRetryWhenServerError()) {
						logger.warn("Failed to get pokestop detail id[{}]", stop.getId());
					}

					sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
				});

	}

	private static void showPlayData() {
		logger.info("[PlayerData] user name:{}, max item:{}, max pokemon:{}", playerData.getUsername(),
				playerData.getMaxItemStorage(), playerData.getMaxPokemonStorage());
		

	}
	
	private static void showStatsData() {
		logger.info("[Stats] level:{} exp:{}/{} ", stats.getLevel(), stats.getExperience(), stats.getNextLevelXp());
	}
	
	

	private static void showInventories() {
		{
			long all = PokemonId.values().length;
			long captured = Arrays.asList(PokemonId.values()).stream()
					.filter(id -> inventories.getPokedex().getPokedexEntry(id) != null)
					.filter(id -> inventories.getPokedex().getPokedexEntry(id).getTimesCaptured() > 0).count();
			long encountered = Arrays.asList(PokemonId.values()).stream()
					.filter(id -> inventories.getPokedex().getPokedexEntry(id) != null)
					.filter(id -> inventories.getPokedex().getPokedexEntry(id).getTimesEncountered() > 0).count();
			logger.info("[Inventories][Pokedex] captured: {}/{}, encountered: {}/{}", captured, all, encountered, all);
		}

		{
			long all = inventories.getHatchery().getEggs().size();
			long hatching = inventories.getHatchery().getEggs().stream().filter(egg -> egg.isIncubate()).count();
			logger.info("[Inventories][Hatchery] hatching: {}/{}", hatching, all);
		}

		{
			int all = inventories.getItemBag().getItems().stream().mapToInt(item -> item.getCount()).sum();
			logger.info("[Inventories][ItemBag] {}/{}/", all, playerData.getMaxItemStorage());

			Optional<Item> op_ball_poke = inventories.getItemBag().getItems().stream()
					.filter(item -> item.getItemId() == ItemId.ITEM_POKE_BALL).findFirst();
			Optional<Item> op_ball_great = inventories.getItemBag().getItems().stream()
					.filter(item -> item.getItemId() == ItemId.ITEM_GREAT_BALL).findFirst();
			Optional<Item> op_ball_ultre = inventories.getItemBag().getItems().stream()
					.filter(item -> item.getItemId() == ItemId.ITEM_ULTRA_BALL).findFirst();
			Optional<Item> op_ball_master = inventories.getItemBag().getItems().stream()
					.filter(item -> item.getItemId() == ItemId.ITEM_MASTER_BALL).findFirst();

			int ball_poke = op_ball_poke.isPresent() ? op_ball_poke.get().getCount() : 0;
			int ball_great = op_ball_great.isPresent() ? op_ball_great.get().getCount() : 0;
			int ball_ultre = op_ball_ultre.isPresent() ? op_ball_ultre.get().getCount() : 0;
			int ball_master = op_ball_master.isPresent() ? op_ball_master.get().getCount() : 0;

			long ball_all = ball_poke + ball_great + ball_ultre + ball_master;
			logger.info("[Inventories][ItemBag][Ball] {}/{}/{}/{} {}", ball_poke, ball_great, ball_ultre, ball_master,
					ball_all);

			Optional<Item> op_potion = inventories.getItemBag().getItems().stream()
					.filter(item -> item.getItemId() == ItemId.ITEM_POTION).findFirst();
			Optional<Item> op_potion_hyper = inventories.getItemBag().getItems().stream()
					.filter(item -> item.getItemId() == ItemId.ITEM_HYPER_POTION).findFirst();
			Optional<Item> op_potionl_super = inventories.getItemBag().getItems().stream()
					.filter(item -> item.getItemId() == ItemId.ITEM_SUPER_POTION).findFirst();
			Optional<Item> op_potion_max = inventories.getItemBag().getItems().stream()
					.filter(item -> item.getItemId() == ItemId.ITEM_MAX_POTION).findFirst();

			int potion = op_potion.isPresent() ? op_potion.get().getCount() : 0;
			int potion_hyper = op_potion_hyper.isPresent() ? op_potion_hyper.get().getCount() : 0;
			int potionl_super = op_potionl_super.isPresent() ? op_potionl_super.get().getCount() : 0;
			int potion_max = op_potion_max.isPresent() ? op_potion_max.get().getCount() : 0;

			long potion_all = inventories.getItemBag().getItems().stream().filter(item -> item.isPotion())
					.mapToInt(item -> item.getCount()).sum();
			logger.info("[Inventories][ItemBag][Potion] {}/{}/{}/{} {}", potion, potion_hyper, potionl_super,
					potion_max, potion_all);

			long reviev_all = inventories.getItemBag().getItems().stream().filter(item -> item.isRevive())
					.mapToInt(item -> item.getCount()).sum();
			logger.info("[Inventories][ItemBag][Revive] {}", reviev_all);

			long berry_all = inventories.getItemBag().getItems().stream()
					.filter(item -> item.getItemId() == ItemId.ITEM_RAZZ_BERRY).mapToInt(item -> item.getCount()).sum();
			logger.info("[Inventories][ItemBag][Berry] {}", berry_all);
			
		}
		
		{
			inventories.getPokebank().getPokemons()
				.stream()
				.sorted((a,b)->Integer.compare(a.getMeta().getNumber(), b.getMeta().getNumber()))
				.forEach(mon->{
					logger.info("[Inventories][Pokemon] #{}{}({}) lv:{} cp:{} iv:{}",
							mon.getMeta().getNumber(),
							PokeNames.getDisplayName(mon.getMeta().getNumber(), new Locale("zh", "CN")),
							PokeNames.getDisplayName(mon.getMeta().getNumber(), Locale.ENGLISH),
							mon.getLevel(),
							mon.getCp(),
							(int)(mon.getIvRatio()*100));
					
				});
		}

	}

	private static void showMapObjects() {
		mapObjects.getPokestops().stream().sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
				.forEach(stop -> {
					FortDetails fd = pokestopMap.get(stop.getId());

					String name = fd == null ? stop.getId() : fd.getName();

					logger.info("[Map][Pokestop] {} canLoot: {}/{} distance: {}/m", name, stop.canLoot(), stop.canLoot(true),
							(int) stop.getDistance());
				});

		mapObjects.getCatchablePokemons().stream().forEach(mon -> {
			logger.info("[Map][Pokemon] #{}{}({})", mon.getPokemonIdValue(),
					PokeNames.getDisplayName(mon.getPokemonIdValue(), new Locale("zh", "CN")),
					PokeNames.getDisplayName(mon.getPokemonIdValue(), Locale.ENGLISH));
		});

	}
	
	private static void showPokestopLootResult(PokestopLootResult result) {
		StringBuilder sb = new StringBuilder();
		
		String items = result.getItemsAwarded().stream()
			.collect(
				() -> new TreeMap<ItemId, Integer>(),
                (map, item) -> {
                	if (map.containsKey(item.getItemId())) {
                		map.put(item.getItemId(), map.get(item.getItemId()) + item.getItemCount());
                	} else {
                		map.put(item.getItemId(), item.getItemCount());
                	}
                },
                (map1, map2) -> map1.putAll(map2)
				
				)
			.entrySet()
			.stream()
			.map(e->e.getKey()+"("+e.getValue()+")")
			.collect(Collectors.joining(", "));
		
		int all = result.getItemsAwarded().stream()
				.mapToInt(item->item.getItemCount())
				.sum();
			
		logger.info("[LootResult] exp:{} items({}):{}", result.getExperience(), all, items);
	}

	private static PokemonGo login() {
		switch (Config.instance.getAuthType()) {
		case GOOGLE:

			try {
				OkHttpClient httpClient = new OkHttpClient();

				String refreshToken = TmpData.instance.getGoogleRefreshToken();
				if (!StringUtils.isEmpty(refreshToken)) {

					logger.info("google login with refresh token:[{}]", refreshToken);
					try {
						return new PokemonGo(new GoogleUserCredentialProvider(httpClient, refreshToken), httpClient);
					} catch (Exception e1) {
						logger.warn("failed to login with refresh token; start over again");
					}
				}

				System.out.println("Please go to " + GoogleUserCredentialProvider.LOGIN_URL);
				System.out.println("Enter authorisation code:");

				Scanner sc = new Scanner(System.in);
				String access = sc.nextLine();
				GoogleUserCredentialProvider provider = new GoogleUserCredentialProvider(httpClient);
				provider.login(access);

				PokemonGo go = new PokemonGo(provider, httpClient);

				TmpData.instance.setGoogleRefreshToken(provider.getRefreshToken());

				return go;

			} catch (Exception e1) {
				String message = "Failed to login";
				logger.error(message, e1);
				throw new RuntimeException(message, e1);
			}

		case PTC:
			RuntimeException e = new RuntimeException("PTC is not support");
			logger.error(e.getMessage(), e);
			throw e;
		default:
			e = new RuntimeException("Unknown auth type");
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	public static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			logger.warn("intrupted while sleeping");
		}
	}
}
