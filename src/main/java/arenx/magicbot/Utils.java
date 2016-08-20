package arenx.magicbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Inventories;
import com.pokegoapi.api.inventory.ItemBag;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.fort.FortDetails;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.exceptions.AsyncPokemonGoException;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.google.common.geometry.S2LatLng;

import POGOProtos.Data.PlayerDataOuterClass.PlayerData;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Responses.RecycleInventoryItemResponseOuterClass.RecycleInventoryItemResponse;
import arenx.magicbot.bean.Location;

public class Utils {

	private static Logger logger = LoggerFactory.getLogger(Utils.class);

	public static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			logger.warn("[Utils] intrupted while sleeping");
		}
	}

	public static <E> Stream<List<E>> combinations(List<E> l, int size) {
	    if (size == 0) {
	        return Stream.of(Collections.emptyList());
	    } else {
	        return IntStream.range(0, l.size()).boxed().
	            <List<E>> flatMap(i -> combinations(l.subList(i+1, l.size()), size - 1).map(t -> pipe(l.get(i), t)));
	    }
	}

	private static <E> List<E> pipe(E head, List<E> tail) {
	    List<E> newList = new ArrayList<>(tail);
	    newList.add(0, head);
	    return newList;
	}

	public static double distance(double la_1m, double lo_1, double la_2, double lo_2){
		Validate.isTrue(!Double.isNaN(la_1m));
		Validate.isTrue(!Double.isNaN(lo_1));
		Validate.isTrue(!Double.isNaN(la_2));
		Validate.isTrue(!Double.isNaN(lo_2));

		S2LatLng o_1 = S2LatLng.fromDegrees(la_1m, lo_1);
		S2LatLng o_2 = S2LatLng.fromDegrees(la_2, lo_2);
		return o_1.getEarthDistance(o_2);
	}

	public static double distance(Location l, Pokestop p){
		Validate.notNull(l);
		Validate.notNull(p);

		S2LatLng o_1 = S2LatLng.fromDegrees(l.getLatitude(), l.getLongitude());
		S2LatLng o_2 = S2LatLng.fromDegrees(p.getLatitude(), p.getLongitude());
		return o_1.getEarthDistance(o_2);
	}

	public static double distance(Pokestop p1, Pokestop p2){
		Validate.notNull(p1);
		Validate.notNull(p2);

		S2LatLng o_1 = S2LatLng.fromDegrees(p1.getLatitude(), p1.getLongitude());
		S2LatLng o_2 = S2LatLng.fromDegrees(p2.getLatitude(), p2.getLongitude());
		return o_1.getEarthDistance(o_2);
	}

	public static double distance(Location l1, Location l2){
		Validate.notNull(l1);
		Validate.notNull(l2);

		S2LatLng o_1 = S2LatLng.fromDegrees(l1.getLatitude(), l1.getLongitude());
		S2LatLng o_2 = S2LatLng.fromDegrees(l2.getLatitude(), l2.getLongitude());
		return o_1.getEarthDistance(o_2);
	}


	// key = Pokestop.getId(), value = Pokestop.getDetails
	private static Map<String, FortDetails> pokestopDetailCache =  Collections.synchronizedSortedMap(new TreeMap<String, FortDetails>());
	private static AtomicLong lastTime_getFortDetails = new AtomicLong(System.currentTimeMillis());

	public static String getName(Pokestop stop){
		if (pokestopDetailCache.containsKey(stop.getId())) {
			return pokestopDetailCache.get(stop.getId()).getName();
		}

		FortDetails fd = getFortDetails(stop);
		pokestopDetailCache.put(stop.getId(), fd);

		logger.info("[Utils] add {} into cache; cache size now is {}", fd.getName(), pokestopDetailCache.size());

		return fd.getName();
	}



	private static FortDetails getFortDetails(Pokestop stop){
		Validate.notNull(stop);

		if (System.currentTimeMillis() - lastTime_getFortDetails.get() < 1000) {
			sleep(1000);
		}

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {

					FortDetails fd = stop.getDetails();
					lastTime_getFortDetails.set(System.currentTimeMillis());

					return fd;

				} catch (AsyncPokemonGoException e) {
					if (!(e.getCause() instanceof RuntimeException)){
						throw e;
					}
					if (!(e.getCause().getCause() instanceof ExecutionException)){
						throw e;
					}
					if (e.getCause().getCause().getCause() instanceof LoginFailedException){
						throw (LoginFailedException)e.getCause().getCause().getCause();
					}
					if (e.getCause().getCause().getCause() instanceof RemoteServerException){
						throw (RemoteServerException)e.getCause().getCause().getCause();
					}
					throw e;
				}

			} catch (LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to get FortDetails after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;
				logger.warn("[Utils] Failed to get FortDetails; sleep 5 sec. and then retry {}/{}", retry, maxRetry);
				Utils.sleep(5000);

			}
		}

	}

	public static MapObjects getMapObjects(PokemonGo go){
		Validate.notNull(go);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return go.getMap().getMapObjects();
				} catch (AsyncPokemonGoException e) {
					if (!(e.getCause() instanceof RuntimeException)){
						throw e;
					}
					if (!(e.getCause().getCause() instanceof ExecutionException)){
						throw e;
					}
					if (e.getCause().getCause().getCause() instanceof LoginFailedException){
						throw (LoginFailedException)e.getCause().getCause().getCause();
					}
					if (e.getCause().getCause().getCause() instanceof RemoteServerException){
						throw (RemoteServerException)e.getCause().getCause().getCause();
					}
					throw e;
				}

			} catch (LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to get MapObjects after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;
				logger.warn("[Utils] Failed to get MapObjects; sleep 5 sec. and then retry {}/{}", retry, maxRetry);
				Utils.sleep(5000);

			}
		}

	}

	public static PlayerData getPlayerData(PokemonGo go){
		Validate.notNull(go);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return go.getPlayerProfile().getPlayerData();
				} catch (AsyncPokemonGoException e) {
					if (!(e.getCause() instanceof RuntimeException)){
						throw e;
					}
					if (!(e.getCause().getCause() instanceof ExecutionException)){
						throw e;
					}
					if (e.getCause().getCause().getCause() instanceof LoginFailedException){
						throw (LoginFailedException)e.getCause().getCause().getCause();
					}
					if (e.getCause().getCause().getCause() instanceof RemoteServerException){
						throw (RemoteServerException)e.getCause().getCause().getCause();
					}
					throw e;
				}

			} catch (LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to get PlayerData after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;
				logger.warn("[Utils] Failed to get PlayerData; sleep 5 sec. and then retry {}/{}", retry, maxRetry);
				Utils.sleep(5000);

			}
		}

	}

	public static Inventories getInventories(PokemonGo go){
		Validate.notNull(go);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					go.getInventories().updateInventories();
					return go.getInventories();
				} catch (AsyncPokemonGoException e) {
					if (!(e.getCause() instanceof RuntimeException)){
						throw e;
					}
					if (!(e.getCause().getCause() instanceof ExecutionException)){
						throw e;
					}
					if (e.getCause().getCause().getCause() instanceof LoginFailedException){
						throw (LoginFailedException)e.getCause().getCause().getCause();
					}
					if (e.getCause().getCause().getCause() instanceof RemoteServerException){
						throw (RemoteServerException)e.getCause().getCause().getCause();
					}
					throw e;
				}

			} catch (LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to get Inventories after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;
				logger.warn("[Utils] Failed to get Inventories; sleep 5 sec. and then retry {}/{}", retry, maxRetry);
				Utils.sleep(5000);

			}
		}

	}

	public static PokestopLootResult loot(Pokestop stop){
		Validate.notNull(stop);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return stop.loot();
				} catch (AsyncPokemonGoException e) {
					if (!(e.getCause() instanceof RuntimeException)){
						throw e;
					}
					if (!(e.getCause().getCause() instanceof ExecutionException)){
						throw e;
					}
					if (e.getCause().getCause().getCause() instanceof LoginFailedException){
						throw (LoginFailedException)e.getCause().getCause().getCause();
					}
					if (e.getCause().getCause().getCause() instanceof RemoteServerException){
						throw (RemoteServerException)e.getCause().getCause().getCause();
					}
					throw e;
				}

			} catch (LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to loot "+ Utils.getName(stop)+" after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;
				logger.warn("[Utils] Failed to loot {}; sleep 5 sec. and then retry {}/{}", Utils.getName(stop), retry, maxRetry);
				Utils.sleep(5000);

			}
		}

	}

	public static RecycleInventoryItemResponse.Result removeItem(PokemonGo go, ItemId id, int quantity){
		Validate.notNull(go);
		Validate.notNull(id);
		Validate.isTrue(quantity>0);

		ItemBag itemBag = Utils.getInventories(go).getItemBag();

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return itemBag.removeItem(id, quantity);
				} catch (AsyncPokemonGoException e) {
					if (!(e.getCause() instanceof RuntimeException)){
						throw e;
					}
					if (!(e.getCause().getCause() instanceof ExecutionException)){
						throw e;
					}
					if (e.getCause().getCause().getCause() instanceof LoginFailedException){
						throw (LoginFailedException)e.getCause().getCause().getCause();
					}
					if (e.getCause().getCause().getCause() instanceof RemoteServerException){
						throw (RemoteServerException)e.getCause().getCause().getCause();
					}
					throw e;
				}

			} catch (LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to remove "+id+" after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;
				logger.warn("[Utils] Failed to remove {}; sleep 5 sec. and then retry {}/{}", id,retry, maxRetry);
				Utils.sleep(5000);

			}
		}

	}
}
