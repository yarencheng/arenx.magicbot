package arenx.magicbot;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.map.fort.FortDetails;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.google.common.geometry.S2LatLng;

public class OldUtils {

	private static Logger logger = LoggerFactory.getLogger(OldUtils.class);

	public static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			logger.warn("intrupted while sleeping");
		}
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

	// key = Pokestop.getId(), value = Pokestop.getDetails
	private static Map<String, FortDetails> pokestopDetailCache = new TreeMap<String, FortDetails>();

	public static void addPokestopDetail(Pokestop stop, FortDetails detail){
		Validate.notNull(stop);
		Validate.notNull(detail);

		if (pokestopDetailCache.containsKey(stop.getId())) {
			return;
		}

		pokestopDetailCache.put(stop.getId(), detail);
		logger.info("[Utils] add detail of pokestop[{}] into cache[size:{}]", detail.getName(), pokestopDetailCache.size());

	}

	public static FortDetails getPokestopDetail(Pokestop stop){
		Validate.notNull(stop);

		return pokestopDetailCache.get(stop.getId());
	}

	public static void updateDetails(Collection<Pokestop> stops){
		stops.stream().filter(stop -> pokestopDetailCache.containsKey(stop.getId()) == false)
		.forEach(stop -> {
			int retry_stop = 1;

			while (retry_stop <= Config.instance.getMaxRetryWhenServerError()) {
				try {

					sleep(1000);

					FortDetails fd = stop.getDetails();
					if (fd != null){
						addPokestopDetail(stop, fd);
					} else {
						logger.warn("[Utils] Can't get detail of pokestop[{}]",stop.getId());
					}
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
		});
	}
}
