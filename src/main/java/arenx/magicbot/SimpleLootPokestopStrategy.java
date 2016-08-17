package arenx.magicbot;

import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Responses.FortSearchResponseOuterClass.FortSearchResponse;

public class SimpleLootPokestopStrategy implements OldStrategy{

	private static Logger logger = LoggerFactory.getLogger(SimpleLootPokestopStrategy.class);
	private PokemonGo go;
	private OldStrategy cleanBackbagStratege;

	public SimpleLootPokestopStrategy(PokemonGo go){
		this.go=go;
	}

	public void setCleanBackbagStrategy(OldStrategy s){
		this.cleanBackbagStratege=s;
	}

	@Override
	public void execute() {
		Pokestop stop = getNearestLootablePokestop();

		if (stop==null) {
			return;
		}

		if(logger.isDebugEnabled()){
			String name = OldUtils.getPokestopDetail(stop) != null ? OldUtils.getPokestopDetail(stop).getName()
					: stop.getId();
			logger.debug("[Moving] try loot [{}]", name);
		}

		OldUtils.sleep(RandomUtils.nextLong(1000, 2000));
		PokestopLootResult result = loot(stop);
		showPokestopLootResult(result);

		if (!result.wasSuccessful() && result.getResult() == FortSearchResponse.Result.OUT_OF_RANGE) {
			return;
		}

		if (!result.wasSuccessful()) {
			String name = OldUtils.getPokestopDetail(stop)!=null ? OldUtils.getPokestopDetail(stop).getName()
					: stop.getId();

			logger.warn("[Loot] Failed to loot [{}]; add it to error history. result:{}", name, result.getResult());
			pokestop_no_result_set_history.add(stop.getId());
			return;
		}

		if (result.getResult() == FortSearchResponse.Result.INVENTORY_FULL){
			logger.info("[Loot] backbag is full");
			cleanBackbagStratege.execute();
		}

		if (result.getResult() == FortSearchResponse.Result.IN_COOLDOWN_PERIOD){
			String name = OldUtils.getPokestopDetail(stop)!=null ? OldUtils.getPokestopDetail(stop).getName()
					: stop.getId();

			logger.warn("[Loot] at [{}] a cooling down pokestop. someting wrong between server and logal pokemonapi", name);

			pokestop_no_result_set_history.add(stop.getId());
		}
	}

	private Set<String> pokestop_no_result_set_history = new TreeSet<String>();

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

		logger.info("[Loot][Result] exp:{} items({}):{}", result.getExperience(), all, items);
	}

	private Pokestop previousStop;
	private int duplicate_loot_count=0;

	private PokestopLootResult loot(Pokestop stop) {

		PokestopLootResult result = null;

		int retry = 0;
		while(true){
			try {
				result = stop.loot();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Loot] Failed to loot";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}

				retry++;

				logger.warn("[Loot] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				OldUtils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
			}
		}

		if (previousStop!=null && previousStop.getId().equals(stop.getId())){
			if (duplicate_loot_count>=5) {
				logger.warn("[Loot] Loop at same pokestop 5 times. It could be a soft ban. Exit program.");
				System.exit(0);
			} else {
				duplicate_loot_count++;
			}
		}else{
			previousStop = stop;
			duplicate_loot_count=0;
		}

		return result;

	}

	private Pokestop getNearestLootablePokestop(){
		MapObjects mapObjects;

		int retry = 0;
		while (true) {
			try {
				mapObjects = go.getMap().getMapObjects();
				OldUtils.updateDetails(mapObjects.getPokestops());
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "Failed to get mapObjects";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}

				retry++;

				logger.warn("[Walking] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				OldUtils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
			}
		}

		Optional<Pokestop> stop = mapObjects.getPokestops().stream()
			.filter(a->a.canLoot())
			.filter(a->!pokestop_no_result_set_history.contains(a.getId()))
			.sorted((a,b)->Double.compare(a.getDistance(), b.getDistance()))
			.findFirst()
			;

		return stop.isPresent() ? stop.get() : null;
	}

}
