package arenx.magicbot;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.PokeBank;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.util.PokeNames;

import POGOProtos.Networking.Responses.ReleasePokemonResponseOuterClass.ReleasePokemonResponse;

public class SimplePokemonTransferStrategy implements Strategy{

	private static Logger logger = LoggerFactory.getLogger(SimplePokemonTransferStrategy.class);
	private PokemonGo go;

	public SimplePokemonTransferStrategy(PokemonGo go){
		this.go=go;
	}

	@Override
	public void execute() {
		PokeBank pokeBank = getPokeBank();

		if (pokeBank.getPokemons().size() < 200) {
			return;
		}

		pokeBank.getPokemons()
			.stream()
			.filter(mon->mon.getCp()<1500)
			.filter(mon->mon.getLevel()<23)
			.forEach(mon->{

				logger.debug("[Transfer] #{}{}({}) - try to transfer", mon.getPokemonId().getNumber(),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
						PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH));

				Utils.sleep(1000);
				ReleasePokemonResponse.Result r = transferPokemon(mon);

				if (r == ReleasePokemonResponse.Result.SUCCESS) {
					logger.info("[Transfer] #{}{}({}) LV:{} CP:{} IV:{} success", mon.getPokemonId().getNumber(),
							PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
							PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH),
							mon.getLevel(), mon.getCp(),mon.getIvRatio());
				} else {
					logger.warn("[Transfer] #{}{}({}) LV:{} CP:{} IV:{} fail result:{}", mon.getPokemonId().getNumber(),
							PokeNames.getDisplayName(mon.getPokemonId().getNumber(), new Locale("zh", "CN")),
							PokeNames.getDisplayName(mon.getPokemonId().getNumber(), Locale.ENGLISH),
							mon.getLevel(), mon.getCp(),mon.getIvRatio(),
							r);
				}
			});
			;

	}

	private ReleasePokemonResponse.Result transferPokemon(Pokemon mon){
		ReleasePokemonResponse.Result result;

		int retry = 0;
		while (true) {
			try {
				result = mon.transferPokemon();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Transfer] Failed to get result of transfer";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}

				retry++;

				logger.warn("[Transfer] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
			}
		}

		return result;
	}

	private PokeBank getPokeBank(){
		PokeBank pokeBank;

		int retry = 0;
		while (true) {
			try {
				go.getInventories().updateInventories();
				pokeBank = go.getInventories().getPokebank();
				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[Transfer] Failed to get PokeBank";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}

				retry++;

				logger.warn("[Transfer] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
			}
		}

		return pokeBank;
	}

}
