package arenx.magicbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.player.PlayerLevelUpRewards;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

public class SimpleLevelUpStrategy implements Strategy{

	private static Logger logger = LoggerFactory.getLogger(SimpleLevelUpStrategy.class);
	private PokemonGo go;

	public SimpleLevelUpStrategy(PokemonGo go){
		this.go=go;
	}



	@Override
	public void execute() {

		PlayerLevelUpRewards rewards = getPlayerLevelUpRewards();

		if (rewards==null){
			return;
		}

		switch (rewards.getStatus()){
		case ALREADY_ACCEPTED:
			logger.warn("[LevelUp] allready accept items");
			return;
		case NEW:
			break;
		case NOT_UNLOCKED_YET:
			logger.warn("[LevelUp] items is unlocked");
			return;
		default:
			logger.error("[LevelUp] unknown status:[]", rewards.getStatus());
			break;

		}

		rewards.getRewards().stream().forEach(item->{
			logger.info("[LevelUp] get {} {}", item.getItemCount(), item.getItemId());

		});

	}

	private int previousLevel = 0;

	private PlayerLevelUpRewards getPlayerLevelUpRewards(){

		PlayerLevelUpRewards rewards;

		int newLevel;

		int retry = 0;
		while (true) {
			try {

				newLevel = go.getPlayerProfile().getStats().getLevel();

				if (previousLevel == newLevel) {
					logger.debug("[LevelUp] level is not changed");
					return null;
				}

				logger.debug("[LevelUp] try to get items of level {}/{}", newLevel, previousLevel);
				rewards = go.getPlayerProfile().acceptLevelUpRewards(newLevel);

				break;
			} catch (LoginFailedException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e);
			} catch (RemoteServerException e) {
				if (retry >= Config.instance.getMaxRetryWhenServerError()) {
					String message = "[LevelUp] Failed to get PlayerLevelUpRewards";
					logger.error(message, e);
					throw new RuntimeException(message, e);
				}

				retry++;

				logger.warn("[LevelUp] Failed to get response from remote server. Retry {}/{}. Caused by: {}",
						retry, Config.instance.getMaxRetryWhenServerError(), e.getMessage());
				Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
			}
		}

		previousLevel = newLevel;

		return rewards;
	}

}
