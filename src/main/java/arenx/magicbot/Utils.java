package arenx.magicbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
import com.pokegoapi.api.inventory.PokeBank;
import com.pokegoapi.api.inventory.Stats;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.fort.FortDetails;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.map.pokemon.CatchResult;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.api.map.pokemon.encounter.EncounterResult;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.exceptions.AsyncPokemonGoException;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.NoSuchItemException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.google.common.geometry.S2LatLng;
import com.pokegoapi.util.PokeNames;

import POGOProtos.Data.PlayerDataOuterClass.PlayerData;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Responses.RecycleInventoryItemResponseOuterClass.RecycleInventoryItemResponse;
import POGOProtos.Networking.Responses.ReleasePokemonResponseOuterClass.ReleasePokemonResponse;
import arenx.magicbot.bean.Location;

public class Utils {

	private static Logger logger = LoggerFactory.getLogger(Utils.class);
	private static final long secondToSleepWhileRemoteServerException = 60 * 1000;
	private static final long secondToSleepWhileLoginFailedException = 5 * 60 * 1000;

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

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to get FortDetails; sleep {} ms. and then retry {}/{}", secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to get FortDetails; sleep {} ms. and then retry {}/{}", secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

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

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to get MapObjects; sleep {} ms. and then retry {}/{}", secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to get MapObjects; sleep {} ms. and then retry {}/{}", secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

			}
		}

	}

	public static List<CatchablePokemon> getCatchablePokemon(PokemonGo go){
		Validate.notNull(go);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return go.getMap().getCatchablePokemon();
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
					String m = "Failed to get CatchablePokemon after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to get CatchablePokemon; sleep {} ms. and then retry {}/{}", secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to get CatchablePokemon; sleep {} ms. and then retry {}/{}", secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

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

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to get PlayerData; sleep {} ms. and then retry {}/{}", secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to get PlayerData; sleep {} ms. and then retry {}/{}", secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

			}
		}

	}

	public static Stats getStats(PokemonGo go){
		Validate.notNull(go);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return go.getPlayerProfile().getStats();
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
					String m = "Failed to get Stats after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to get Stats; sleep {} ms. and then retry {}/{}", secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to get Stats; sleep {} ms. and then retry {}/{}", secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

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

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to get Inventories; sleep {} ms. and then retry {}/{}", secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to get Inventories; sleep {} ms. and then retry {}/{}", secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

			}
		}

	}

	public static PokeBank getPokeBank(PokemonGo go){
		Validate.notNull(go);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					go.getInventories().updateInventories();
					return go.getInventories().getPokebank();
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
					String m = "Failed to get PokeBank after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to get PokeBank; sleep {} ms. and then retry {}/{}", secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to get PokeBank; sleep {} ms. and then retry {}/{}", secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

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

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to loot {}; sleep {} ms. and then retry {}/{}", getName(stop), secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to loot {}; sleep {} ms. and then retry {}/{}", getName(stop), secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

			}
		}

	}

	public static EncounterResult encouter(CatchablePokemon  mon){
		Validate.notNull(mon);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return mon.encounterPokemon();
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
					String m = "Failed to encounter "+ Utils.getPokemonFullName(mon)+" after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to encounter {}; sleep {} ms. and then retry {}/{}", getPokemonFullName(mon), secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to encounter {}; sleep {} ms. and then retry {}/{}", getPokemonFullName(mon), secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

			}
		}

	}

	public static ReleasePokemonResponse.Result transferPokemon(Pokemon mon){
		Validate.notNull(mon);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return mon.transferPokemon();
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
					String m = "Failed to transfer "+ Utils.getPokemonFullName(mon)+" after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to transfer {}; sleep {} ms. and then retry {}/{}", getPokemonFullName(mon), secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to transfer {}; sleep {} ms. and then retry {}/{}", getPokemonFullName(mon), secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

			}
		}

	}

	public static CatchResult catchPokemonEasy(CatchablePokemon  mon){
		Validate.notNull(mon);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return mon.catchPokemon();
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
				} catch (NoSuchItemException e) {
					logger.warn("[Utils] no item to catch "+getPokemonFullName(mon), e);
					return null;
				}

			} catch (LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to catch "+ Utils.getPokemonFullName(mon)+" after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to catch {}; sleep {} sec. and then retry {}/{}", Utils.getPokemonFullName(mon), secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to catch {}; sleep {} sec. and then retry {}/{}", Utils.getPokemonFullName(mon), secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

			}
		}

	}

	public static CatchResult catchPokemonHard(CatchablePokemon  mon){
		Validate.notNull(mon);

		int maxRetry=5;
		int retry=0;

		while(true){
			try {

				try {
					return mon.catchPokemonWithBestBall(true, 1, 1);
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
				} catch (NoSuchItemException e) {
					logger.warn("[Utils] no item to catch "+getPokemonFullName(mon), e);
					return null;
				}

			} catch (LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to catch "+ Utils.getPokemonFullName(mon)+" after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Utils] "+m, e);
					throw new RuntimeException(m,e);
				}

				retry ++;

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to catch {}; sleep {} sec. and then retry {}/{}", Utils.getPokemonFullName(mon), secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to catch {}; sleep {} sec. and then retry {}/{}", Utils.getPokemonFullName(mon), secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

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

				if (e instanceof LoginFailedException) {
					logger.warn("[Utils] Failed to remove {}; sleep {} sec. and then retry {}/{}", id, secondToSleepWhileLoginFailedException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileLoginFailedException);
				}

				if (e instanceof RemoteServerException) {
					logger.warn("[Utils] Failed to remove {}; sleep {} sec. and then retry {}/{}", id, secondToSleepWhileRemoteServerException, retry, maxRetry);
					Utils.sleep(secondToSleepWhileRemoteServerException);
				}

			}
		}
	}

	public static String getTranslatedPokemonName(int number){
		return PokeNames.getDisplayName(number,Locale.TAIWAN);
	}

	public static String getTranslatedPokemonName(CatchablePokemon mon){
		return getTranslatedPokemonName(mon.getPokemonId().getNumber());
	}

	public static String getTranslatedPokemonName(Pokemon mon){
		return getTranslatedPokemonName(mon.getPokemonId().getNumber());
	}

	public static String getPokemonName(int number){
		return PokeNames.getDisplayName(number, Locale.ENGLISH);
	}

	public static String getPokemonName(CatchablePokemon mon){
		return getTranslatedPokemonName(mon.getPokemonId().getNumber());
	}

	public static String getPokemonName(Pokemon mon){
		return getTranslatedPokemonName(mon.getPokemonId().getNumber());
	}

	public static String getPokemonFullName(int number){
		return "#"+number+getTranslatedPokemonName(number)+"(" + getPokemonName(number) +")";
	}

	public static String getPokemonFullName(CatchablePokemon mon){
		return getPokemonFullName(mon.getPokemonId().getNumber());
	}

	public static String getPokemonFullName(Pokemon mon){
		return getPokemonFullName(mon.getPokemonId().getNumber());
	}
}
