package arenx.magicbot;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Component;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.fort.PokestopLootResult;

import POGOProtos.Data.PlayerDataOuterClass.PlayerData;
import POGOProtos.Inventory.Item.ItemAwardOuterClass.ItemAward;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Responses.RecycleInventoryItemResponseOuterClass.RecycleInventoryItemResponse;
import arenx.magicbot.bean.Location;

@Component
public class Bot {

	private static Logger logger = LoggerFactory.getLogger(Bot.class);

	private boolean isStoped = false;
	private boolean isShutdown = false;
	private long roundCount = 0;

	@Autowired
	private MoveStrategy moveStrategy;

	@Autowired
	private BackbagStrategy backbagStrategy;

	@Autowired
	private AtomicReference<PokemonGo> go;

	public void stop(){
		isStoped = true;
	}

	public boolean isShutdown(){
		return isShutdown;
	}

	public void setPokemonGo(PokemonGo go){
		this.go.set(go);
	}

	public void start(){

		restoreState();

		Location lastLocation = moveStrategy.getCurrentLocation();
		go.get().setLatitude(lastLocation.getLatitude());
		go.get().setLongitude(lastLocation.getLongitude());
		go.get().setAltitude(lastLocation.getAltitude());

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				storeState();
			}
		});

		while(!isStoped){
			roundCount++;
			logger.debug("[Bot] ============= new round #{} =============", roundCount);

			Map<ItemId, Integer> items = backbagStrategy.getTobeRemovedItem();
			removeItems(items);

			Location l = moveStrategy.nextLocation();
			go.get().setLocation(l.getLatitude(), l.getLongitude(), l.getAltitude());

			loot();

			Utils.sleep(RandomUtils.nextLong(1000, 2000));

//			break;
		}

		logger.info("[Bot] prepare to stop");

		storeState();

		isShutdown= true;
	}

	public void removeItems(Map<ItemId, Integer> items){
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

	public static final Object state_data_lock = new Object();

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

			PlayerData pd = Utils.getPlayerData(go.get());

			Location l = moveStrategy.getCurrentLocation();
			config.setProperty(pd.getUsername()+".latitude", l.getLatitude());
			config.setProperty(pd.getUsername()+".longitude", l.getLongitude());
			config.setProperty(pd.getUsername()+".altitude", l.getAltitude());

			try {
				configBuilder.save();
			} catch (ConfigurationException e) {
				logger.error("Failed to save "+file, e);
				System.exit(-1);
			}
		}

	}

	public void restoreState(){
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

	private void loot(){

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
					error_loot_stop.put(stop.getId(), System.currentTimeMillis());
					break;
				case OUT_OF_RANGE:
					logger.warn("[Pokestop] {} is out of range", Utils.getName(stop));
					break;
				case SUCCESS:
					break;
				case UNRECOGNIZED:
				default:
					logger.error("[Pokestop] unknow status is set after looting {}; skip it in future.", Utils.getName(stop));
					error_loot_stop.put(stop.getId(), System.currentTimeMillis());
					break;
				}

			});

	}

}
