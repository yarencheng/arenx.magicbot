package arenx.magicbot;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Stats;

import POGOProtos.Data.PlayerDataOuterClass.PlayerData;

public class SimpleInformationStrategy implements InformationStrategy{

	private static Logger logger = LoggerFactory.getLogger(SimpleInformationStrategy.class);

	@Autowired
	private AtomicReference<PokemonGo> go;

	private long lastShowTIme = 0;

	@Override
	public void showStatus() {

		if (System.currentTimeMillis() - lastShowTIme < 5 * 60 * 1000) {
			return;
		}

		Stats stats = Utils.getStats(go.get());
		PlayerData data = Utils.getPlayerData(go.get());

		int exp_percent = (int)((double)(stats.getExperience() - stats.getPrevLevelXp()) / (double)(stats.getNextLevelXp() - stats.getPrevLevelXp()) * 100);

		logger.info("[Status] {} lv:{}({}%) exp:{}/{}",
				data.getUsername(),
				stats.getLevel(), exp_percent,
				stats.getExperience(),stats.getNextLevelXp());

		lastShowTIme = System.currentTimeMillis();

	}

}
