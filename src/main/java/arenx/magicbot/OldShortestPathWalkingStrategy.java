package arenx.magicbot;

import java.util.Optional;

import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

public class OldShortestPathWalkingStrategy implements OldStrategy{

	private static Logger logger = LoggerFactory.getLogger(OldShortestPathWalkingStrategy.class);
	private PokemonGo go;

	public OldShortestPathWalkingStrategy(PokemonGo go){
		this.go=go;

		double longitude = Config.instance.getDefaultLongitude();
		double latitude = Config.instance.getDefaultLatitude();
		double altitude = Config.instance.getDefaultAltitude();

		if (TmpData.instance.getLastAltitude() !=null) {
			altitude = TmpData.instance.getLastAltitude();
			logger.info("[Walking] restore altitude:{}", altitude);
		}

		if (TmpData.instance.getLastLongitude() !=null) {
			longitude = TmpData.instance.getLastLongitude();
			logger.info("[Walking] restore longitude:{}", longitude);
		}

		if (TmpData.instance.getLastLatitude() !=null) {
			latitude = TmpData.instance.getLastLatitude();
			logger.info("[Walking] restore latitude:{}", latitude);
		}

		go.setLocation(latitude, longitude, altitude);

	}

	private Pokestop distPokestop;

	@Override
	public void execute() {
		Pokestop nearPokestop = getNearestPokestop();

		double dist_longitude;
		double dist_latitude;
		double dist_altitude;
		double ratio;

		if (nearPokestop == null) {
			distPokestop = null;
			logger.info("[Walking] heading to default location");
		} else if(distPokestop == null){
			distPokestop = nearPokestop;
			String name = OldUtils.getPokestopDetail(distPokestop) == null ? distPokestop.getId()
					: OldUtils.getPokestopDetail(distPokestop).getName();
			logger.info("[Moving] heading [{}] distance={}", name, distPokestop.getDistance());
		} else if (distPokestop != null && !distPokestop.getId().equals(nearPokestop.getId())){
			distPokestop = nearPokestop;
			String name = OldUtils.getPokestopDetail(distPokestop) == null ? distPokestop.getId()
					: OldUtils.getPokestopDetail(distPokestop).getName();
			logger.info("[Moving] heading [{}] distance={}", name, distPokestop.getDistance());
		} else {
			if (logger.isDebugEnabled()){
				String name = distPokestop == null ? "default location" :
					OldUtils.getPokestopDetail(distPokestop) == null ? distPokestop.getId()
						: OldUtils.getPokestopDetail(distPokestop).getName();
				logger.debug("[Moving] heading [{}] distance={}", name, distPokestop.getDistance());
			}
		}

		if (distPokestop!=null) {
			dist_longitude = distPokestop.getLongitude();
			dist_latitude = distPokestop.getLatitude();
			dist_altitude = RandomUtils.nextDouble(5, 10);
			ratio = Config.instance.getSpeedPerSecond() / distPokestop.getDistance();
		} else {
			dist_longitude = Config.instance.getDefaultLongitude();
			dist_latitude = Config.instance.getDefaultLatitude();
			dist_altitude = Config.instance.getDefaultAltitude();
			ratio = Config.instance.getSpeedPerSecond() / OldUtils.distance(go.getLatitude(), go.getLongitude(), dist_latitude, dist_longitude);
		}

		double heading_longitude = (dist_longitude - go.getLongitude()) * ratio + go.getLongitude();
		double heading_latitude = (dist_latitude - go.getLatitude()) * ratio + go.getLatitude();
		double heading_altitude = (dist_altitude - go.getAltitude()) * ratio + go.getAltitude();

		if (Double.isNaN(heading_longitude) || Double.isNaN(heading_latitude) || Double.isNaN(heading_altitude)){
			logger.warn("[Walking] get NaN");
			heading_longitude = go.getLongitude() + 0.0001;
			heading_latitude = go.getLatitude() + 0.0001;
			heading_altitude = go.getAltitude() + 0.0001;
		}

		double travel_distance = OldUtils.distance(go.getLatitude(), go.getLongitude(), heading_latitude, heading_longitude);

		if (logger.isDebugEnabled()){
			String name = distPokestop == null ? "default location"
					: OldUtils.getPokestopDetail(distPokestop) == null ? distPokestop.getId()
					: OldUtils.getPokestopDetail(distPokestop).getName();

			logger.debug("[Moving] heading to [{}] distance={} ({},{}) -> ({},{})", name, travel_distance, go.getLatitude(), go.getLongitude(), heading_latitude, heading_longitude);
		}

		go.setLocation(heading_latitude, heading_longitude, heading_altitude);
		OldUtils.sleep(1000);
	}

	private Pokestop getNearestPokestop(){
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
			.filter(a->a.canLoot(true))
			.sorted((a,b)->Double.compare(a.getDistance(), b.getDistance()))
			.findFirst()
			;

		return stop.isPresent() ? stop.get() : null;
	}

}
