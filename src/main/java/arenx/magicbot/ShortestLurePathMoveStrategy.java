package arenx.magicbot;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.fort.Pokestop;

import arenx.magicbot.bean.Location;

public class ShortestLurePathMoveStrategy implements MoveStrategy{

	private static Logger logger = LoggerFactory.getLogger(ShortestLurePathMoveStrategy.class);

	private HierarchicalConfiguration<ImmutableNode> config;
	private double speedMeterPerSecond;
	private Location defaultLoaction;

	private Location lastLocation;
	private long lastTimeMoved = System.currentTimeMillis();
	private Pokestop curTargetStop;

	@Autowired
	public void setConfig(@Autowired HierarchicalConfiguration<ImmutableNode> config){
		this.config = config.configurationAt("shortestLurePathMoveStrategy");

		speedMeterPerSecond = this.config.getDouble("speedMeterPerSecond");

		lastLocation = new Location();
		lastLocation.setLatitude(this.config.getDouble("defaultLoaction.latitude"));
		lastLocation.setLongitude(this.config.getDouble("defaultLoaction.longitude"));
		lastLocation.setAltitude(this.config.getDouble("defaultLoaction.altitude"));

		defaultLoaction = new Location();
		defaultLoaction.setLatitude(this.config.getDouble("defaultLoaction.latitude"));
		defaultLoaction.setLongitude(this.config.getDouble("defaultLoaction.longitude"));
		defaultLoaction.setAltitude(this.config.getDouble("defaultLoaction.altitude"));

		logger.info("[MoveStrategy] set lastLocation to {}", lastLocation);
	}

	@Autowired
	private AtomicReference<PokemonGo> go;

	@Override
	public Location nextLocation() {

		Optional<Pokestop> pokestop = Utils.getMapObjects(go.get())
			.getPokestops()
			.stream()
			.filter(stop->stop.canLoot(true))
			.sorted((s1,s2)->Double.compare(Utils.distance(lastLocation, s1), Utils.distance(lastLocation, s2)))
			.findFirst()
			;

		Location target_location;

		if (pokestop.isPresent()) {
			target_location = new Location(pokestop.get().getLatitude(), pokestop.get().getLongitude(), RandomUtils.nextDouble(5, 10));
			logger.debug("[MoveStrategy] heading to pokestop {} {}m ({}, {})", Utils.getName(pokestop.get()), pokestop.get().getDistance(),pokestop.get().getLatitude(), pokestop.get().getLongitude());

			if (curTargetStop==null || !curTargetStop.getId().equals(pokestop.get().getId())) {
				curTargetStop = pokestop.get();
				logger.info("[MoveStrategy] heading to {} with distance {}m", Utils.getName(curTargetStop), curTargetStop.getDistance());
			}

		} else {
			target_location = defaultLoaction;
			logger.debug("[MoveStrategy] heading to default location {}", defaultLoaction);
		}

		double target_distance = Utils.distance(lastLocation, target_location);
		double move_distance = speedMeterPerSecond * (System.currentTimeMillis() - lastTimeMoved) / 1000;
		double ratio = move_distance/target_distance;

		logger.debug("[MoveStrategy] distance {} {} {}", move_distance, target_distance, ratio);

		Location move_location = new Location();
		if (target_distance > move_distance) {
			move_location.setLatitude((target_location.getLatitude() - lastLocation.getLatitude()) * ratio + lastLocation.getLatitude());
			move_location.setLongitude((target_location.getLongitude() - lastLocation.getLongitude()) * ratio + lastLocation.getLongitude());
			move_location.setAltitude((target_location.getAltitude() - lastLocation.getAltitude()) * ratio + lastLocation.getAltitude());
		} else {
			move_location.setLatitude(target_location.getLatitude());
			move_location.setLongitude(target_location.getLongitude());
			move_location.setAltitude(target_location.getAltitude());
		}

		logger.debug("[MoveStrategy] move to {} with distance {}", move_location, Utils.distance(move_location, lastLocation));

		lastLocation = move_location;
		lastTimeMoved = System.currentTimeMillis();

		return move_location;

	}


	@Override
	public Location getCurrentLocation() {
		return new Location(lastLocation.getLatitude(),lastLocation.getLongitude(), lastLocation.getAltitude());
	}


	@Override
	public void setCurrentLocation(Location location) {
		lastTimeMoved = System.currentTimeMillis();
		lastLocation = new Location(location.getLatitude(),location.getLongitude(), location.getAltitude());

		logger.info("[MoveStrategy] set current location to {}", lastLocation);
	}

}
