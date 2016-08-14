package arenx.magicbot;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.EggIncubator;
import com.pokegoapi.api.inventory.Hatchery;
import com.pokegoapi.api.inventory.Inventories;
import com.pokegoapi.api.inventory.ItemBag;
import com.pokegoapi.api.inventory.PokeBank;
import com.pokegoapi.api.inventory.Pokedex;
import com.pokegoapi.api.inventory.Stats;
import com.pokegoapi.api.map.fort.FortDetails;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.api.map.pokemon.encounter.EncounterResult;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.util.PokeNames;

import POGOProtos.Data.PlayerDataOuterClass.PlayerData;
import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Map.Pokemon.MapPokemonOuterClass.MapPokemon;

public class SimpleInformationStrategy implements Strategy{

	private static Logger logger = LoggerFactory.getLogger(SimpleInformationStrategy.class);
	private PokemonGo go;
	
	public SimpleInformationStrategy(PokemonGo go){
		this.go=go;
	}
	
	@Override
	public void execute() {
		// TODO Auto-generated method stub
		
	}
	
	public void showAll(){
		showPlayerData();
		showStats();
		showHatchery();
		showItemBag();
		showPokedex();
		showPokemon();
	}

	private boolean showPlayerData_entered = false;
	
	public void showPlayerData(){
		
		PlayerData data;
		
		if (!showPlayerData_entered) {
			showPlayerData_entered = true;
			Utils.sleep(1000);
		}
		
		int retry = 0;
		while (true) {
			try {
				data = go.getPlayerProfile().getPlayerData();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Information][PlayerData] Failed to get PlayerData";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}
				
				retry++;
				
				logger.warn("[Information][PlayerData] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());				
			}
		}
		
		logger.info("[Information][PlayerData] name:{},  team:{}, max item:{}, max pokemon:{}",
				data.getUsername(), data.getTeam(), data.getMaxItemStorage(),data.getMaxPokemonStorage());
	}
	
	private boolean showStats_entered = false;
	
	public void showStats(){
		
		Stats stats;
		
		if (!showStats_entered) {
			showStats_entered = true;
			Utils.sleep(1000);
		}
		
		int retry = 0;
		while (true) {
			try {
				stats = go.getPlayerProfile().getStats();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Information][Stats] Failed to get Stats";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}
				
				retry++;
				
				logger.warn("[Information][Stats] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());				
			}
		}
		
		int exp_percent = (int)((double)(stats.getExperience() - stats.getPrevLevelXp()) / (double)(stats.getNextLevelXp() - stats.getPrevLevelXp()) * 100);
		
		logger.info("[Information][Stats] level:{}({}%), exp:{}/{}",
				stats.getLevel(),
				exp_percent,
				stats.getExperience(),stats.getNextLevelXp());
		
		logger.info("[Information][Stats] attack won:{}/{}, defend won:{}, training won:{}/{}",
				stats.getBattleAttackWon(), stats.getBattleAttackTotal(),
				stats.getBattleDefendedWon(),
				stats.getBattleTrainingWon(), stats.getBattleTrainingTotal());
		
		logger.info("[Information][Stats] hatched egg:{}, walk:{}, evolution:{}",
				stats.getEggsHatched(),
				stats.getKmWalked(),
				stats.getEvolutions());
		
		logger.info("[Information][Stats] thrown ball:{}, captured pokemon:{}/{}, deploy pokemon:{}",
				stats.getPokeballsThrown(),
				stats.getPokemonsCaptured(),stats.getPokemonsEncountered(),
				stats.getPokemonDeployed());
		
		logger.info("[Information][Stats] pokestop:{}, dropped prestige:{}, raised prestige:{}",
				stats.getPokeStopVisits(),
				stats.getPrestigeDroppedTotal(),
				stats.getPrestigeRaisedTotal()
				);
	}
	
	private boolean showHatchery_entered = false;
	
	public void showHatchery(){
		
		Hatchery hatchery;
		
		if (!showHatchery_entered) {
			showHatchery_entered = true;
			Utils.sleep(1000);
		}
		
		int retry = 0;
		while (true) {
			try {
				go.getInventories().updateInventories();
				hatchery = go.getInventories().getHatchery();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Information][Hatchery] Failed to get Hatchery";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}
				
				retry++;
				
				logger.warn("[Information][Hatchery] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());				
			}
		}
		
		long incubate = hatchery.getEggs().stream().filter(egg->egg.isIncubate()).count();
		long all = hatchery.getEggs().size();
		logger.info("[Information][Hatchery] hatching egg:{}/{}", incubate, all);		
	}
	
	private boolean showItemBag_entered = false;
	
	public void showItemBag(){
		
		ItemBag itemBag;
		
		if (!showItemBag_entered) {
			showItemBag_entered = true;
			Utils.sleep(1000);
		}
		
		int retry = 0;
		while (true) {
			try {
				go.getInventories().updateInventories();
				itemBag = go.getInventories().getItemBag();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Information][ItemBag] Failed to get ItemBag";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}
				
				retry++;
				
				logger.warn("[Information][ItemBag] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());				
			}
		}
		
		int ball_poke = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_POKE_BALL).mapToInt(item->item.getCount()).sum();
		int ball_great = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_GREAT_BALL).mapToInt(item->item.getCount()).sum();
		int ball_ultra = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_ULTRA_BALL).mapToInt(item->item.getCount()).sum();
		int ball_master = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_MASTER_BALL).mapToInt(item->item.getCount()).sum();
		int ball_all = ball_poke + ball_great + ball_ultra + ball_master;
		
		logger.info("[Information][ItemBag] ball({}):{}/{}/{}/{}", ball_all, ball_poke, ball_great, ball_ultra, ball_master);
		
		int potion = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_POTION).mapToInt(item->item.getCount()).sum();
		int potion_hyper = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_HYPER_POTION).mapToInt(item->item.getCount()).sum();
		int potion_super = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_SUPER_POTION).mapToInt(item->item.getCount()).sum();
		int potion_max = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_MAX_POTION).mapToInt(item->item.getCount()).sum();
		int potion_all = itemBag.getItems().stream().filter(item->item.isPotion()).mapToInt(item->item.getCount()).sum();
		
		logger.info("[Information][ItemBag] potion({}):{}/{}/{}/{}", potion_all, potion, potion_hyper, potion_super, potion_max);
				
		int berry_razz = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_RAZZ_BERRY).mapToInt(item->item.getCount()).sum();
		int berry_bluk = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_BLUK_BERRY).mapToInt(item->item.getCount()).sum();
		int berry_nanab = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_NANAB_BERRY).mapToInt(item->item.getCount()).sum();
		int berry_pinap = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_PINAP_BERRY).mapToInt(item->item.getCount()).sum();
		int berry_wepar = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_WEPAR_BERRY).mapToInt(item->item.getCount()).sum();
		int berry_all = berry_razz + berry_bluk + berry_nanab + berry_pinap + berry_wepar;
		
		logger.info("[Information][ItemBag] berry({}):{}/{}/{}/{}/{}", berry_all, berry_razz, berry_bluk, berry_nanab, berry_pinap, berry_wepar);
		
		int incense_cool = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_INCENSE_COOL).mapToInt(item->item.getCount()).sum();
		int incense_floral = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_INCENSE_FLORAL).mapToInt(item->item.getCount()).sum();
		int incense_ordinary = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_INCENSE_ORDINARY).mapToInt(item->item.getCount()).sum();
		int incense_spicy = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_INCENSE_SPICY).mapToInt(item->item.getCount()).sum();
		int incense_all = incense_cool + incense_floral + incense_ordinary + incense_spicy;
		
		logger.info("[Information][ItemBag] incense({}):{}/{}/{}/{}", incense_all, incense_cool, incense_floral, incense_ordinary, incense_spicy);
		
		int revive = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_REVIVE).mapToInt(item->item.getCount()).sum();
		int revive_max = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_MAX_REVIVE).mapToInt(item->item.getCount()).sum();
		int revive_all = itemBag.getItems().stream().filter(item->item.isRevive()).mapToInt(item->item.getCount()).sum();
		
		logger.info("[Information][ItemBag] revive({}):{}/{}", revive_all, revive, revive_max);
		
		int incubator = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_INCUBATOR_BASIC).mapToInt(item->item.getCount()).sum();
		int incubator_unlimited = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_INCUBATOR_BASIC_UNLIMITED).mapToInt(item->item.getCount()).sum();
		int incubator_all = incubator + incubator_unlimited;
		
		logger.info("[Information][ItemBag] incubator({}):{}/{}", incubator_all, incubator, incubator_unlimited);
		
		int luckyegg = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_LUCKY_EGG).mapToInt(item->item.getCount()).sum();
		int camera = itemBag.getItems().stream().filter(item->item.getItemId()==ItemId.ITEM_SPECIAL_CAMERA).mapToInt(item->item.getCount()).sum();
		
		logger.info("[Information][ItemBag] luckyegg:{}, camera:{}", luckyegg, camera);
		
	}
	
	private boolean showPokedex_entered = false;
	
	public void showPokedex(){
		
		Pokedex pokedex;
		
		if (!showPokedex_entered) {
			showPokedex_entered = true;
			Utils.sleep(1000);
		}
		
		int retry = 0;
		while (true) {
			try {
				go.getInventories().updateInventories();
				pokedex = go.getInventories().getPokedex();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Information][Pokedex] Failed to get Pokedex";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}
				
				retry++;
				
				logger.warn("[Information][Pokedex] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());				
			}
		}
		
		final Pokedex pokedex_ = pokedex;
		
		long all = PokemonId.values().length;
		long captured = Arrays.asList(PokemonId.values()).stream()
				.filter(id -> pokedex_.getPokedexEntry(id) != null)
				.filter(id -> pokedex_.getPokedexEntry(id).getTimesCaptured() > 0).count();
		long encountered = Arrays.asList(PokemonId.values()).stream()
				.filter(id -> pokedex_.getPokedexEntry(id) != null)
				.filter(id -> pokedex_.getPokedexEntry(id).getTimesEncountered() > 0).count();
		logger.info("[Information][Pokedex] captured: {}/{}, encountered: {}/{}", captured, all, encountered, all);
	}
	
	private boolean showPokemon_entered = false;
	
	public void showPokemon(){
		
		PokeBank pokeBank;
		
		if (!showPokemon_entered) {
			showPokemon_entered = true;
			Utils.sleep(1000);
		}
		
		int retry = 0;
		while (true) {
			try {
				go.getInventories().updateInventories();
				pokeBank = go.getInventories().getPokebank();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Information][PokeBank] Failed to get PokeBank";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}
				
				retry++;
				
				logger.warn("[Information][PokeBank] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());				
			}
		}
		
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		
		logger.info("[Information][PokeBank] count:{}", pokeBank.getPokemons().size());
		
		pokeBank.getPokemons()
		.stream()
		.sorted((a,b)->-Long.compare(a.getCreationTimeMs(), b.getCreationTimeMs()))
		.forEach(mon->{
			logger.info("[Information][PokeBank] {} #{}{}({}) lv:{} cp:{} iv:{}",
					format.format(new Date(mon.getCreationTimeMs())),
					mon.getMeta().getNumber(),
					PokeNames.getDisplayName(mon.getMeta().getNumber(), new Locale("zh", "CN")),
					PokeNames.getDisplayName(mon.getMeta().getNumber(), Locale.ENGLISH),
					mon.getLevel(),
					mon.getCp(),
					(int)(mon.getIvRatio()*100));
			
		});
	}
	
	private boolean showCatchablePokemon_entered = false;
	
	public void showCatchablePokemon() {
		List<CatchablePokemon> mons;
		
		if (!showCatchablePokemon_entered) {
			showCatchablePokemon_entered = true;
			Utils.sleep(1000);
		}
		
		int retry = 0;
		while (true) {
			try {
				mons = go.getMap().getCatchablePokemon();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Information][CatchablePokemon] Failed to get CatchablePokemon";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}
				
				retry++;
				
				logger.warn("[Information][CatchablePokemon] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());				
			}
		}

		mons.stream().forEach(mon -> {
			logger.info("[Information][CatchablePokemon] #{}{}({})", mon.getPokemonId().getNumber(),
					PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
					PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));			
		});

	}
}
