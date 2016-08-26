package arenx.magicbot;

import java.util.Collections;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Pokedex;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.pokemon.PokemonClass;
import com.pokegoapi.api.pokemon.PokemonMetaRegistry;

import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import arenx.magicbot.bean.Location;
import arenx.magicbot.bean.PokeRadar;

public class ShortestPathMoveStrategy implements MoveStrategy{

	private static Logger logger = LoggerFactory.getLogger(ShortestPathMoveStrategy.class);

	private HierarchicalConfiguration<ImmutableNode> config;
	private double speedMeterPerSecond;
	private Location defaultLoaction;
	private boolean searchPokemonNotInPokeindex;
	private PokemonClass searchPokemonClass;

	private AtomicReference<Location> lastLocation = new AtomicReference<Location>(null);
	private long lastTimeMoved = System.currentTimeMillis();
	private Pokestop curTargetStop;
	private PokeRadar.Pokemon curTargetPokemon;
	private RestTemplate rest = new RestTemplate();
	private SortedMap<String,PokeRadar.Pokemon>radar = Collections.synchronizedSortedMap(new TreeMap<>());
	private SortedMap<String,PokeRadar.Pokemon>radar_visited = Collections.synchronizedSortedMap(new TreeMap<>());

	@Autowired
	public void setConfig(@Autowired HierarchicalConfiguration<ImmutableNode> config){
		this.config = config.configurationAt("shortestPathMoveStrategy");

		speedMeterPerSecond = this.config.getDouble("speedMeterPerSecond");
		searchPokemonNotInPokeindex = this.config.getBoolean("searchPokemonNotInPokeindex");
		searchPokemonClass = this.config.get(PokemonClass.class, "searchPokemonClass");

		Location l = new Location();
		l.setLatitude(this.config.getDouble("defaultLoaction.latitude"));
		l.setLongitude(this.config.getDouble("defaultLoaction.longitude"));
		l.setAltitude(this.config.getDouble("defaultLoaction.altitude"));
		lastLocation.set(l);

		defaultLoaction = new Location();
		defaultLoaction.setLatitude(this.config.getDouble("defaultLoaction.latitude"));
		defaultLoaction.setLongitude(this.config.getDouble("defaultLoaction.longitude"));
		defaultLoaction.setAltitude(this.config.getDouble("defaultLoaction.altitude"));

		logger.info("[MoveStrategy] set lastLocation to {}", lastLocation);
	}

	@Autowired
	private AtomicReference<PokemonGo> go;

	@PostConstruct
	public void init(){
		Thread t=new Thread(()->{
			final String deviceId = "0bc6acc0656911e689aa3bc8887363b5";

			int pokemonId = 0;

			while(true){
				if (lastLocation.get()==null) {
					Utils.sleep(1000);
					continue;
				}
				logger.debug("[PokeRadar] update ...");

				new TreeSet<>(radar.keySet())
					.forEach(id->{
						if (radar.get(id).remainingSecond()<2) {
							radar.remove(id);
						}
					});
				new TreeSet<>(radar_visited.keySet())
				.forEach(id->{
					if (radar_visited.get(id).remainingSecond()<2) {
						radar_visited.remove(id);
					}
				});

				double minLatitude = lastLocation.get().getLatitude()-0.03;
				double maxLatitude = lastLocation.get().getLatitude()+0.03;
				double minLongitude = lastLocation.get().getLongitude()-0.03;
				double maxLongitude = lastLocation.get().getLongitude()+0.03;

				try{
					rest.getForObject("https://www.pokeradar.io/api/v1/submissions?deviceId={deviceId}&minLatitude={minLatitude}&maxLatitude={maxLatitude}&"
							+ "minLongitude={minLongitude}&maxLongitude={maxLongitude}&pokemonId={pokemonId}", PokeRadar.class,
							deviceId,minLatitude,maxLatitude,minLongitude,maxLongitude,pokemonId)
					.getData()
					.stream()
					.filter(mon->mon.getTrainerName().equals("(Poke Radar Prediction)"))
					.forEach(mon->radar.put(mon.getId(), mon));
				}catch(Throwable e){
					logger.error("[PokeRadar] Failed to get radar data", e);
				}

				logger.debug("[PokeRadar] update ... done {}", radar.size());

				Utils.sleep(10000);

			}
		});
		t.setDaemon(true);
		t.start();

	}

	@Override
	public Location nextLocation() {

		Optional<Pokestop> pokestop = Utils.getMapObjects(go.get())
			.getPokestops()
			.stream()
			.filter(stop->stop.canLoot(true))
			.sorted((s1,s2)->Double.compare(Utils.distance(lastLocation.get(), s1), Utils.distance(lastLocation.get(), s2)))
			.findFirst()
			;

		Pokedex pokedex = Utils.getInventories(go.get()).getPokedex();
		Location l=lastLocation.get();

		Optional<PokeRadar.Pokemon> mon_near = radar
			.values()
			.stream()
			.filter(mon->{
				boolean isCaptured = pokedex.getPokedexEntry(PokemonId.forNumber(mon.getPokemonId())) == null ? false:
					pokedex.getPokedexEntry(PokemonId.forNumber(mon.getPokemonId())).getTimesCaptured() > 0;
				boolean isClass = searchPokemonClass.ordinal() <= PokemonMetaRegistry.getMeta(PokemonId.forNumber(mon.getPokemonId())).getPokemonClass().ordinal();

				return isClass ? true : !isCaptured;
				})
			.filter(mon->{
				double distance = Utils.distance(mon.getLatitude(), mon.getLongitude(), l.getLatitude(), l.getLongitude());
				long remain_second = mon.remainingSecond();

				return distance/remain_second < speedMeterPerSecond;
			})
			.filter(mon->Utils.distance(mon.getLatitude(), mon.getLongitude(), l.getLatitude(), l.getLongitude())>10)
			.filter(mon->!radar_visited.containsKey(mon.getId()))
			.sorted((a,b)->Double.compare(
					Utils.distance(a.getLatitude(), a.getLongitude(), l.getLatitude(), l.getLongitude()),
					Utils.distance(b.getLatitude(), b.getLongitude(), l.getLatitude(), l.getLongitude())
					))
			.findFirst();

		Location target_location;

		if (mon_near.isPresent()) {
			target_location = new Location(mon_near.get().getLatitude(), mon_near.get().getLongitude(), RandomUtils.nextDouble(5, 10));
			logger.debug("[MoveStrategy] heading to pokemon {} {}m {}sec ({}, {})",
					Utils.getPokemonFullName(mon_near.get().getPokemonId()),
					Utils.distance(mon_near.get().getLatitude(), mon_near.get().getLongitude(), l.getLatitude(), l.getLongitude()),
					mon_near.get().remainingSecond(),
					mon_near.get().getLatitude(), mon_near.get().getLongitude());

			if (curTargetPokemon==null || !curTargetPokemon.getId().equals(mon_near.get().getId())) {
				curTargetPokemon = mon_near.get();
				logger.info("[MoveStrategy] heading to {} with distance {}m {}sec", Utils.getPokemonFullName(curTargetPokemon.getPokemonId()),
						Utils.distance(curTargetPokemon.getLatitude(), curTargetPokemon.getLongitude(), l.getLatitude(), l.getLongitude()),
						curTargetPokemon.remainingSecond());
			}

			if (Utils.distance(mon_near.get().getLatitude(), mon_near.get().getLongitude(), l.getLatitude(), l.getLongitude())<5) {
				radar_visited.put(mon_near.get().getId(), mon_near.get());
			}

		} else if (pokestop.isPresent()) {
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

		double target_distance = Utils.distance(l, target_location);
		double move_distance = speedMeterPerSecond * (System.currentTimeMillis() - lastTimeMoved) / 1000;
		double ratio = move_distance/target_distance;

		logger.debug("[MoveStrategy] distance {} {} {}", move_distance, target_distance, ratio);

		Location move_location = new Location();
		if (target_distance > move_distance) {
			move_location.setLatitude((target_location.getLatitude() - l.getLatitude()) * ratio + l.getLatitude());
			move_location.setLongitude((target_location.getLongitude() - l.getLongitude()) * ratio + l.getLongitude());
			move_location.setAltitude((target_location.getAltitude() - l.getAltitude()) * ratio + l.getAltitude());
		} else {
			move_location.setLatitude(target_location.getLatitude());
			move_location.setLongitude(target_location.getLongitude());
			move_location.setAltitude(target_location.getAltitude());
		}

		logger.debug("[MoveStrategy] move to {} with distance {}", move_location, Utils.distance(move_location, l));

		lastLocation.set(move_location);
		lastTimeMoved = System.currentTimeMillis();

		return move_location;

	}


	@Override
	public Location getCurrentLocation() {
		Location l = lastLocation.get();
		return new Location(l.getLatitude(),l.getLongitude(), l.getAltitude());
	}


	@Override
	public void setCurrentLocation(Location location) {
		lastTimeMoved = System.currentTimeMillis();
		lastLocation.set(new Location(location.getLatitude(),location.getLongitude(), location.getAltitude()));

		logger.info("[MoveStrategy] set current location to {}", lastLocation.get());
	}

}
