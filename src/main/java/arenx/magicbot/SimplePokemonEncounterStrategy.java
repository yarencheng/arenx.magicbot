package arenx.magicbot;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.pokemon.CatchResult;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.api.map.pokemon.encounter.EncounterResult;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.NoSuchItemException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.util.PokeNames;

import POGOProtos.Data.PokemonDataOuterClass.PokemonData;

public class SimplePokemonEncounterStrategy implements Strategy{

	private static Logger logger = LoggerFactory.getLogger(SimplePokemonEncounterStrategy.class);
	private PokemonGo go;
	private Strategy informationStrategy;

	public SimplePokemonEncounterStrategy(PokemonGo go){
		this.go=go;
	}

	public void setInformationStrategy(Strategy s){
		informationStrategy = s;
	}

	@Override
	public void execute() {

		List<CatchablePokemon> mons = getCatchablePokemon();

		if (mons.isEmpty()){
			return;
		}

		((SimpleInformationStrategy)informationStrategy).showCatchablePokemon();

		mons.forEach(mon->{

			logger.debug("[Encounter] #{}{}({}) - try to encounter", mon.getPokemonId().getNumber(),
					PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
					PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));

			if (catchPokemon_errorStatus_history.contains(mon.getEncounterId())) {
				logger.warn("[Encounter] skip to catch #{}{}({}) since this encounter({}) has an error record in the history.", mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH),
						mon.getEncounterId());
				return;
			}

			EncounterResult er = encounter(mon);

			if (!er.wasSuccessful()) {
				logger.warn("[Encounter] Failed to encounter");
				return;
			}

			logger.debug("[Encounter] #{}{}({}) - probability={}", mon.getPokemonId().getNumber(),
					PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
					PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH),
					er.getCaptureProbability().getCaptureProbabilityList().stream().map(f->""+f).collect(Collectors.joining(",")));

			catchPokemon(er.getPokemonData(), mon);

			Utils.sleep(1000);
		});

	}

	private EncounterResult encounter(CatchablePokemon mon){
		EncounterResult er;

		int retry = 0;
		while(true){
			try {
				er = mon.encounterPokemon();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Encounter] Failed to get EncounterResult";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}

				retry++;

				logger.warn("[Encounter] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
			}
		}
		return er;
	}

	private Set<Long> catchPokemon_errorStatus_history= new TreeSet<Long>();

	public void catchPokemon(PokemonData data, CatchablePokemon mon){

		CatchResult cr;

		int catch_count = 1;

		while(true){

			int retry = 0;

			while(true){
				try {

					logger.debug("[Encounter] #{}{}({}) - try to catch", mon.getPokemonId().getNumber(),
							PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
							PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));

					Utils.sleep(1000);

					if (data.getCp()>1000){
						cr = mon.catchPokemonWithRazzBerry();
					} else {
						cr = mon.catchPokemon();
					}



					break;
				} catch (LoginFailedException e) {
					logger.error(e.getMessage(), e);
					throw new RuntimeException(e);
				} catch (RemoteServerException e) {
					if (retry >= Config.instance.getMaxRetryWhenServerError()) {
						String message = "[Encounter] Failed to get EncounterResult";
						logger.error(message, e);
						throw new RuntimeException(message, e);
					}

					retry++;

					logger.warn("[Encounter] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
							retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
					Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
				} catch (NoSuchItemException e) {
					logger.warn("[Encounter] Failed to catch since out of balls. {}", e.getMessage());
					return;
				}
			}

			switch (cr.getStatus()) {
			case CATCH_ERROR:
				logger.warn("[Encounter] get error status after trying to catch #{}{}({}); record this encouter({}) in error history",
						mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH),
						mon.getEncounterId());

				catchPokemon_errorStatus_history.add(mon.getEncounterId());

				return;
			case CATCH_ESCAPE:
				logger.debug("[Encounter] #{}{}({}) - escape", mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));
				break;
			case CATCH_FLEE:
				logger.debug("[Encounter] #{}{}({}) - flee", mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));
				return;
			case CATCH_MISSED:
				logger.debug("[Encounter] #{}{}({}) - missed", mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));
				break;
			case CATCH_SUCCESS:
				logger.debug("[Encounter] #{}{}({}) - success", mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));

				String exp = cr.getXpList().stream().map(i->""+i).collect(Collectors.joining("/"));

				logger.info("[Encounter] #{}{}({}) CP:{} catch(#{}) exp:{}", mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH),
						data.getCp(),
						catch_count,
						exp);

				return;
			case UNRECOGNIZED:
				logger.error("[Encounter] #{}{}({}) - get inrecognized status", mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));
				return;
			default:
				logger.error("[Encounter] #{}{}({}) - get unknown status", mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));
				return;
			}

			if (catch_count <= 10){
				break;
			}else {
				catch_count++;
			}
		}

		logger.info("[Encounter] #{}{}({}) CP:{} missed(#{})", mon.getPokemonId().getNumber(),
				PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
				PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH),
				data.getCp(),
				catch_count);



		return;


	}


	private List<CatchablePokemon> getCatchablePokemon() {
		List<CatchablePokemon> mons;

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
					String message = "[Encounter] Failed to get CatchablePokemon";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}

				retry++;

				logger.warn("[Encounter] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
			}
		}

		return mons;

	}

}
