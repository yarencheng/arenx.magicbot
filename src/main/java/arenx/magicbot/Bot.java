package arenx.magicbot;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Component;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.EggIncubator;
import com.pokegoapi.api.inventory.Hatchery;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.map.pokemon.CatchResult;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.api.map.pokemon.EvolutionResult;
import com.pokegoapi.api.map.pokemon.encounter.EncounterResult;
import com.pokegoapi.api.player.PlayerLevelUpRewards;
import com.pokegoapi.api.pokemon.EggPokemon;
import com.pokegoapi.auth.CredentialProvider;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.AsyncPokemonGoException;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import POGOProtos.Data.PlayerDataOuterClass.PlayerData;
import POGOProtos.Inventory.Item.ItemAwardOuterClass.ItemAward;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus;
import POGOProtos.Networking.Responses.RecycleInventoryItemResponseOuterClass.RecycleInventoryItemResponse;
import POGOProtos.Networking.Responses.ReleasePokemonResponseOuterClass.ReleasePokemonResponse;
import POGOProtos.Networking.Responses.UseItemEggIncubatorResponseOuterClass.UseItemEggIncubatorResponse;
import arenx.magicbot.bean.Account;
import arenx.magicbot.bean.Location;
import okhttp3.OkHttpClient;

@Component
public class Bot implements Runnable{

	private static Logger logger = LoggerFactory.getLogger(Bot.class);
	public static final Object state_data_lock = new Object();

	private HierarchicalConfiguration<ImmutableNode> config;
	private long roundCount = 0;
	private long startTime;
	private int minutesToPlay;
	private int currentLevel;
	private Account account;


	@Autowired
	private MoveStrategy moveStrategy;

	@Autowired
	private BackbagStrategy backbagStrategy;

	@Autowired
	private InformationStrategy informationStrategy;

	@Autowired
	private PokebankStrategy pokebankStrategy;

	@Autowired
	private AtomicReference<PokemonGo> go;

	@Autowired
	@Qualifier("lootedPokestopCount")
	private AtomicLong lootedPokestopCount;

	@Autowired
	@Qualifier("catchedPokemonCount")
	private AtomicLong catchedPokemonCount;

	@Autowired
	public void setConfig(@Autowired HierarchicalConfiguration<ImmutableNode> config) {
		this.config = config.configurationAt("bot");
	}

	public void setMinutesToPlay(int minutesToPlay){
		this.minutesToPlay=minutesToPlay;
	}

	public void setAccount(Account account){
		Validate.notNull(account);

		this.account=new Account();

		this.account.setUsername(account.getUsername());
		this.account.setPassword(account.getPassword());
	}

	@Override
	public void run(){

		try {
			startBot();
		} catch (Throwable e) {
			logger.error("[Bot] error occurse", e);
			throw e;
		}
	}

	private void startBot(){

		Thread.currentThread().setName(account.getUsername());

		logger.info("[Bot] start");

		startTime = System.currentTimeMillis();

		go.set(login());

		restoreState();

		Location lastLocation = moveStrategy.getCurrentLocation();
		go.get().setLatitude(lastLocation.getLatitude());
		go.get().setLongitude(lastLocation.getLongitude());
		go.get().setAltitude(lastLocation.getAltitude());

		currentLevel = Utils.getStats(go.get()).getLevel();

		while(true){
			roundCount++;
			logger.debug("[Bot] ============= new round #{} =============", roundCount);

			informationStrategy.showStatus();
			checkLevelup();

			envolvePokemon();
			transferPokemon();
			encounterPokemons();

			Map<ItemId, Integer> items = backbagStrategy.getTobeRemovedItem();
			removeItems(items);

			checkEgg();

			Location l = moveStrategy.nextLocation();
			go.get().setLocation(l.getLatitude(), l.getLongitude(), l.getAltitude());

			lootPokestop();

			Utils.sleep(RandomUtils.nextLong(1000, 2000));

			if (isTimeout()) {
				logger.info("[Bot] timeout; stop bot");
				break;
			}
		}

		logger.info("[Bot] prepare to stop");

		storeState();
	}

	private boolean isTimeout(){
		return System.currentTimeMillis() - startTime > minutesToPlay * 60 * 1000;
	}

	private void checkEgg(){
		Utils.queryHatchedEggs(go.get()).forEach(egg->{
			logger.info("[Egg] {} is hatched", egg.getId());
		});

		Hatchery ha = Utils.getInventories(go.get()).getHatchery();

		List<EggIncubator> ins = Utils.getInventories(go.get()).getIncubators().stream()
			.filter(in->!Utils.isInUsed(in))
			.collect(Collectors.toList());

		if (ins.isEmpty()) {
			logger.debug("[Egg] no any incubator is available");
		} else {
			logger.debug("[Egg] {} incubator is available", ins.size());
		}

		List<EggPokemon> eggs = ha.getEggs().stream()
			.filter(egg->!egg.isIncubate())
			.sorted((a,b)->-Double.compare(a.getEggKmWalkedTarget(), b.getEggKmWalkedTarget()))
			.limit(ins.size())
			.collect(Collectors.toList());

		for(int i=0;i<eggs.size();i++){
			logger.debug("[Egg] try to hatch egg({}) in incubator({})", eggs.get(i).getId(), ins.get(i).getId());

			UseItemEggIncubatorResponse.Result r = Utils.incubate(eggs.get(i), ins.get(i));

			switch(r){
			case ERROR_INCUBATOR_ALREADY_IN_USE:
				logger.warn("[Egg] incubator is allready in used");
				break;
			case ERROR_INCUBATOR_NOT_FOUND:
				logger.warn("[Egg] no such incubator");
				break;
			case ERROR_INCUBATOR_NO_USES_REMAINING:
				logger.warn("[Egg] the incubator has no remaining");
				break;
			case ERROR_POKEMON_ALREADY_INCUBATING:
				logger.warn("[Egg] the egg is allready incubated");
				break;
			case ERROR_POKEMON_EGG_NOT_FOUND:
				logger.warn("[Egg] no such egg");
				break;
			case ERROR_POKEMON_ID_NOT_EGG:
				logger.warn("[Egg] not a egg");
				break;
			case SUCCESS:
				logger.info("[Egg] Start to hatch egg {}km", eggs.get(i).getEggKmWalkedTarget());
				break;
			case UNRECOGNIZED:
			case UNSET:
			default:
				logger.warn("[Egg] Gots erro while trying to put egg into incubator");
				break;

			}
		}
	}

	private void checkLevelup(){

		int newLevel = Utils.getStats(go.get()).getLevel();

		logger.debug("[Level] current lv:{}, new lv:{}", currentLevel, newLevel);

		if (currentLevel >= newLevel) {
			return;
		}

		PlayerLevelUpRewards rewards = Utils.acceptLevelUpRewards(go.get(), newLevel);

		switch(rewards.getStatus()){
		case ALREADY_ACCEPTED:
			logger.warn("[Level] rewarded items of level {} are allread accepted", newLevel);
			break;
		case NEW:
			currentLevel = newLevel;

			logger.info("[Level] Level up to {}", currentLevel);

			rewards.getRewards().stream().forEach(item->{
				logger.info("[Level] Got {} {}", item.getItemCount(), item.getItemId());

			});

			Utils.checkAndEquipBadges(go.get());

			break;
		case NOT_UNLOCKED_YET:
			logger.warn("[Level] rewarded items of level {} are not unlocked yet", newLevel);
			break;
		default:
			logger.warn("[Level] Got unknown error while acceptLevelUpRewards()");
			break;

		}


	}

	private void removeItems(Map<ItemId, Integer> items){
		items.forEach((id,quantity)->{

			logger.debug("[Bot] try to recycle {} {}", quantity, id);

			RecycleInventoryItemResponse.Result r = Utils.removeItem(go.get(), id, quantity);
			Utils.sleep(RandomUtils.nextLong(800, 1200));

			switch(r){
			case ERROR_CANNOT_RECYCLE_INCUBATORS:
				logger.warn("[Bot] Can't recycle incubators");
				break;
			case ERROR_NOT_ENOUGH_COPIES:
				logger.warn("[Bot] Thers are no {} {} to recycle", quantity, id);
				break;
			case SUCCESS:
				logger.info("[Bot] recycle {} {}", quantity, id);
				break;
			case UNRECOGNIZED:
			case UNSET:
			default:
				logger.error("[Bot] Failed to recycle {} {}", quantity, id);
				System.exit(-1);
				break;

			}
		});
	}

	public void storeState(){
		logger.info("[Bot] store data");

		synchronized (state_data_lock) {
			FileBasedConfigurationBuilder<PropertiesConfiguration> configBuilder = null;
			Configuration config = null;

			File file = new File("state.data");
			if(!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					logger.error("Failed to create "+file, e);
					System.exit(-1);
				}
			}

			try {
				configBuilder = new Configurations().propertiesBuilder("state.data");
				config = configBuilder.getConfiguration();
			} catch (ConfigurationException e) {
				logger.error("Failed to build "+file, e);
				System.exit(-1);
			}

			if (go.get()==null) {
				return;
			}

			PlayerData pd = Utils.getPlayerData(go.get());

			Location l = moveStrategy.getCurrentLocation();
			config.setProperty(pd.getUsername()+".latitude", l.getLatitude());
			config.setProperty(pd.getUsername()+".longitude", l.getLongitude());
			config.setProperty(pd.getUsername()+".altitude", l.getAltitude());
		}

	}

	private void restoreState(){
		logger.info("[Bot] restore data");

		Properties properties = null;

		synchronized (Bot.state_data_lock) {
			try{
				File file = new File("state.data");
				if (!file.exists()){
					file.createNewFile();
				}
				properties = PropertiesLoaderUtils.loadProperties(
			        new FileSystemResource(file));
			}catch(IOException e){
				logger.error("Failed to read state.data", e);
				System.exit(-1);
			}
		}

		PlayerData pd = Utils.getPlayerData(go.get());

		String latitude = properties.getProperty(pd.getUsername()+".latitude");
		String longitude = properties.getProperty(pd.getUsername()+".longitude");
		String altitude = properties.getProperty(pd.getUsername()+".altitude");

		logger.debug("[Bot] latitude={} longitude={} altitude={}", latitude, longitude, altitude);

		if (latitude!=null && longitude!=null && altitude!=null) {
			Location l = new Location(Double.parseDouble(latitude),Double.parseDouble(longitude),Double.parseDouble(altitude));
			logger.info("[Bot] restore location to {} for player {}", l, pd.getUsername());
			moveStrategy.setCurrentLocation(l);
		}
	}

	// key=Pokestop.getId() value=time
	private TreeMap<String, Long> error_loot_stop = new TreeMap<>();
	private TreeMap<String, Long> success_loot_stop = new TreeMap<>();

	private void lootPokestop(){

		Utils.getMapObjects(go.get())
			.getPokestops()
			.stream()
			.filter(stop->{
				if (!error_loot_stop.containsKey(stop.getId())) {
					return true;
				}

				if (System.currentTimeMillis() - error_loot_stop.get(stop.getId()) < (10 * 60 * 1000)){
					logger.warn("[Pokestop] Skip to loot {}", Utils.getName(stop));
					return false;
				}

				error_loot_stop.remove(stop.getId());

				return true;
			})
			.filter(stop->stop.canLoot())
			.forEach(stop->{

				logger.debug("[Pokestop] try to loot {}", Utils.getName(stop));

				PokestopLootResult pr = Utils.loot(stop);
				Utils.sleep(RandomUtils.nextLong(800, 1200));

				if (pr.wasSuccessful()) {

					lootedPokestopCount.incrementAndGet();

					if (success_loot_stop.containsKey(stop.getId())) {
						if (System.currentTimeMillis() - success_loot_stop.get(stop.getId()) < 60 * 1000){
							logger.error("[Pokestop] loot successfully again at {} in 1 min. It could be a soft ban");
							System.exit(0);
						}
					}

					success_loot_stop.put(stop.getId(), System.currentTimeMillis());

					String items = pr.getItemsAwarded()
						.stream()
						.collect(Collectors.groupingBy(
								Function.identity(),
								Collectors.summingInt(ItemAward::getItemCount)
						))
						.entrySet()
						.stream()
						.map(e->e.getValue()+" "+e.getKey().getItemId())
						.collect(Collectors.joining(", "));
						;
					logger.info("[Pokestop] At {}, get {}", Utils.getName(stop), items);
				}

				switch (pr.getResult()) {
				case INVENTORY_FULL:
					logger.info("[Pokestop] Bag is full");
					break;
				case IN_COOLDOWN_PERIOD:
					logger.warn("[Pokestop] {} is in cooldown period", Utils.getName(stop));
					break;
				case NO_RESULT_SET:
					logger.error("[Pokestop] loot result is not set after lotting {}; skip it in future.", Utils.getName(stop));
//					error_loot_stop.put(stop.getId(), System.currentTimeMillis());
					break;
				case OUT_OF_RANGE:
					logger.warn("[Pokestop] {} is out of range", Utils.getName(stop));
					break;
				case SUCCESS:
					break;
				case UNRECOGNIZED:
				default:
					logger.error("[Pokestop] unknow status is set after looting {}; skip it in future.", Utils.getName(stop));
//					error_loot_stop.put(stop.getId(), System.currentTimeMillis());
					break;
				}

			});

	}

	private void encounterPokemons(){
		Utils.getCatchablePokemon(go.get())
			.stream()
			.map(mon->{

				logger.debug("[Pokemon] try to encouter {}", Utils.getPokemonFullName(mon));

				EncounterResult r = Utils.encouter(mon);

				return new SimpleEntry<CatchablePokemon, EncounterResult>(mon , r);
			})
			.filter(e->{

				if(e.getValue().wasSuccessful()){
					return true;
				}

				switch(e.getValue().getStatus()){
				case ENCOUNTER_ALREADY_HAPPENED:
					logger.warn("[Pokemon] {} is allready been encoutered", Utils.getPokemonFullName(e.getKey()));
					return false;
				case ENCOUNTER_CLOSED:
					logger.warn("[Pokemon] encouter is closed after try to encounter {}", Utils.getPokemonFullName(e.getKey()));
					return false;
				case ENCOUNTER_ERROR:
					logger.error("[Pokemon] got error after try to encounter {}", Utils.getPokemonFullName(e.getKey()));
					return false;
				case ENCOUNTER_NOT_FOUND:
					logger.warn("[Pokemon] not found after try to encounter {}", Utils.getPokemonFullName(e.getKey()));
					return false;
				case ENCOUNTER_NOT_IN_RANGE:
					logger.warn("[Pokemon] {} is too far", Utils.getPokemonFullName(e.getKey()));
					return false;
				case ENCOUNTER_POKEMON_FLED:
					logger.warn("[Pokemon] {} is fled", Utils.getPokemonFullName(e.getKey()));
					return false;
				case ENCOUNTER_SUCCESS:
					logger.debug("[Pokemon] sucess to encouter {}", Utils.getPokemonFullName(e.getKey()));
					return true;
				case POKEMON_INVENTORY_FULL:
					logger.warn("[Pokemon] invetory is full when try to encouter {}", Utils.getPokemonFullName(e.getKey()));
					return false;
				case UNRECOGNIZED:
				default:
					logger.error("[Pokemon] got unrecognized error after try to encounter {}", Utils.getPokemonFullName(e.getKey()));
					return false;
				}
			})
			.map(e->{
				logger.debug("[Pokemon] try to catch #{}{}({})", e.getKey().getPokemonId().getNumber(), Utils.getTranslatedPokemonName(e.getKey()), Utils.getPokemonName(e.getKey()));

				CatchResult r = Utils.catchPokemonEasy(e.getKey());
				Utils.sleep(1000);

				int escapeRetryMax = 10;
				int escapeRetry = 0;
				while(r.getStatus()==CatchStatus.CATCH_ESCAPE && escapeRetry<escapeRetryMax){
					escapeRetry++;
					r = Utils.catchPokemonEasy(e.getKey());
					Utils.sleep(1000);
				}

				return new SimpleEntry<CatchablePokemon, CatchResult>(e.getKey() , r);
			})
			.forEach(e->{
				switch(e.getValue().getStatus()){
				case CATCH_ERROR:
					logger.error("[Pokemon] got error after try to catch {}", Utils.getPokemonFullName(e.getKey()));
					return;
				case CATCH_ESCAPE:
					logger.info("[Pokemon] {} escaped", Utils.getPokemonFullName(e.getKey()));
					return;
				case CATCH_FLEE:
					logger.info("[Pokemon] {} fled", Utils.getPokemonFullName(e.getKey()));
					return;
				case CATCH_MISSED:
					logger.info("[Pokemon] missed to catch {}", Utils.getPokemonFullName(e.getKey()));
					return;
				case CATCH_SUCCESS:
					logger.info("[Pokemon] catch {}", Utils.getPokemonFullName(e.getKey()));
					catchedPokemonCount.incrementAndGet();
					return;
				case UNRECOGNIZED:
				default:
					logger.error("[Pokemon] got unrecognized error after try to catch {}", Utils.getPokemonFullName(e.getKey()));
					return;
				}
			})
			;
	}

	private void envolvePokemon(){
		pokebankStrategy.getToBeEnvolvePokemons()
			.forEach(mon->{

				logger.debug("[Pokemon] try to envlove {}", Utils.getPokemonFullName(mon));

				EvolutionResult r = Utils.envolvePokemon(mon);
				Utils.sleep(RandomUtils.nextLong(500, 1500));

				switch(r.getResult()){
				case FAILED_INSUFFICIENT_RESOURCES:
					logger.warn("[Pokemon] {} is out", Utils.getPokemonFullName(mon));
					break;
				case FAILED_POKEMON_CANNOT_EVOLVE:
					logger.warn("[Pokemon] {} can envolve", Utils.getPokemonFullName(mon));
					break;
				case FAILED_POKEMON_IS_DEPLOYED:
					logger.warn("[Pokemon] {} is deployed", Utils.getPokemonFullName(mon));
					break;
				case FAILED_POKEMON_MISSING:
					logger.warn("[Pokemon] {} is missing", Utils.getPokemonFullName(mon));
					break;
				case SUCCESS:
					logger.info("[Pokemon] {} is envolved successfully", Utils.getPokemonFullName(mon));
					break;
				case UNRECOGNIZED:
				case UNSET:
				default:
					logger.error("[Pokemon] failed to envolve {} r="+r, Utils.getPokemonFullName(mon));
					break;

				}

			});
	}

	private void transferPokemon(){
		pokebankStrategy.getToBeTransferedPokemons()
			.forEach(mon->{

				logger.debug("[Pokemon] try to transfer {}", Utils.getPokemonFullName(mon));

				ReleasePokemonResponse.Result r = Utils.transferPokemon(mon);
				Utils.sleep(RandomUtils.nextLong(500, 1500));

				switch(r){
				case ERROR_POKEMON_IS_EGG:
					logger.error("[Pokemon] {} is egg and can't be trnasferd", Utils.getPokemonFullName(mon));
					return;
				case FAILED:
					logger.error("[Pokemon] Failed to transfer {}", Utils.getPokemonFullName(mon));
					return;
				case POKEMON_DEPLOYED:
					logger.error("[Pokemon] {} is deployed and can't be trnasferd", Utils.getPokemonFullName(mon));
					return;
				case SUCCESS:
					logger.info("[Pokemon] {} is transferd successfully", Utils.getPokemonFullName(mon));
					return;
				case UNRECOGNIZED:
				case UNSET:
				default:
					logger.error("[Pokemon] Gots unrecognized erro after transfering {}", Utils.getPokemonFullName(mon));
					return;

				}

			});
	}

	private PokemonGo login(){
		OkHttpClient httpClient = new OkHttpClient();
		CredentialProvider cp;
		PokemonGo go;

		int maxRetry = 5;
		int retry = 0;

		logger.info("[Login] start login:{}", account.getUsername());

		while(true){
			try {
				logger.debug("[Login] username:{} password:{}", account.getUsername(), account.getPassword());
				cp = new PtcCredentialProvider(httpClient, account.getUsername(), account.getPassword());
				go = new PokemonGo(cp, httpClient);
				break;
			} catch (AsyncPokemonGoException | LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to login after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Login] {}", m);
					throw new RuntimeException(m, e);
				}

				retry ++;
				logger.warn("[Login] Failed to login; sleep 5 sec. and then retry {}/{}", retry, maxRetry);
				Utils.sleep(5000);
			}
		}

		logger.info("[Login] success");

		return go;
	}

}
