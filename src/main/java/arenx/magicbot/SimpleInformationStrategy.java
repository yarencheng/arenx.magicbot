package arenx.magicbot;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.time.DurationFormatUtils;
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

	private long startTime;
	private long startExp;


	@Override
	public void showStatus() {



		showExp();

		DurationFormatUtils s;

	}

	private long lastTimeshowExp = 0;

	private void showExp() {

		if (System.currentTimeMillis() - lastTimeshowExp < 1 * 60 * 1000) {
			return;
		}

		Stats stats = Utils.getStats(go.get());
		PlayerData data = Utils.getPlayerData(go.get());

		int exp_percent = (int)((double)(stats.getExperience() - stats.getPrevLevelXp()) / (double)(stats.getNextLevelXp() - stats.getPrevLevelXp()) * 100);

		long exp_hourly = 0;

		if (startTime==0) {
			startTime=System.currentTimeMillis();
			startExp=stats.getExperience();
		} else {
			exp_hourly = (int)((stats.getExperience() - startExp) / ((double)(System.currentTimeMillis()-startTime) / (60 * 60 * 1000) ));
		}



		logger.info("[Status] {} lv:{}({}%) exp:{}/{} exp/h:{} time:{}",
				data.getUsername(),
				stats.getLevel(), exp_percent,
				stats.getExperience(),stats.getNextLevelXp(),
				exp_hourly,
				DurationFormatUtils.formatDurationHMS(System.currentTimeMillis()-startTime));

		lastTimeshowExp = System.currentTimeMillis();

	}

}
