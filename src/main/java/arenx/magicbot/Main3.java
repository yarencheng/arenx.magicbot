package arenx.magicbot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.CredentialProvider;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.AsyncPokemonGoException;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import arenx.magicbot.bean.Account;
import okhttp3.OkHttpClient;

@Configuration
@ComponentScan(basePackages={"arenx.magicbot"})
public class Main3 {

	private static Logger logger = LoggerFactory.getLogger(Main3.class);

	public static void main(String[] args) {
		try {
			startBots();
		} catch (Throwable e) {
			logger.error("Some thing goes wrong", e);
			System.exit(-1);
		}
	}

	@Bean
	public HierarchicalConfiguration<ImmutableNode> getXMLConfiguration(){
		try {
			return new Configurations().xml("config.xml");
		} catch (ConfigurationException e) {
			String m = "Failed to parse config file";
			logger.error("[Main] " + m, e);
			throw new RuntimeException(m, e);
		}

	}

	@Bean
	public AtomicReference<PokemonGo> getPokemonGo(){
		return new AtomicReference<>();
	}

	@Bean
	public MoveStrategy getMoveStrategy(){
		return new ShortestLurePathMoveStrategy();
	}

	@Bean
	public BackbagStrategy getBackbagStrategy(){
		return new SimpleBackbagStrategy();
	}

	public static void startBots() throws Exception {

		XMLConfiguration config = null;
		try {
			config = new Configurations().xml("config.xml");
		} catch (ConfigurationException e) {
			logger.error("[Main] Failed to parse config file", e);
			return;
		}

		int minutesToPlay = config.getInt("globaleSettings.minutesToPlay");
		int numberOfBot = config.getInt("globaleSettings.numberOfBot");

		List<Account>accounts=new ArrayList<>();
		config.configurationsAt("accounts.account")
			.forEach(a->{
				Account account = new Account();
				account.setUsername(a.getString("username"));
				account.setPassword(a.getString("password"));
				accounts.add(account);
			});

		Thread[] threads = new Thread[numberOfBot];
		Bot[] bots = new Bot[numberOfBot];
		long[] botStartTime = new long[numberOfBot];

		for(int i=0;i<accounts.size();){

			int slotIndex=-1;
			for(int j=0;j<numberOfBot;j++){
				if (bots[j] != null && bots[j].isShutdown()) {
					slotIndex = j;
					break;
				}

				if (System.currentTimeMillis() - botStartTime[j] > minutesToPlay * 60 * 1000) {
					slotIndex = j;
					break;
				}
			}

			if (slotIndex==-1){
				Utils.sleep(10000);
				continue;
			}

			if (bots[slotIndex]!=null) {

				logger.info("[Main] stop and wait bot:{}", bots[slotIndex]);

				bots[slotIndex].stop();

				threads[slotIndex].join();
			}

			PokemonGo go = login(accounts.get(i));

			AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main3.class);
			bots[slotIndex] = context.getBean(Bot.class);
			bots[slotIndex].setPokemonGo(go);

			botStartTime[slotIndex] = System.currentTimeMillis();

			final int slotIndex_ = slotIndex;
			threads[slotIndex] = new Thread(){
				@Override
				public void run(){
					bots[slotIndex_].start();
				}
			};

			threads[slotIndex].setUncaughtExceptionHandler((thread, e)->{
				logger.error("Some thing goes wrong", e);
				System.exit(-1);
			});
			threads[slotIndex].setName(accounts.get(i).getUsername());

			logger.info("[Main] start bot:{}", bots[slotIndex]);
			threads[slotIndex].start();

			i++;
		}

		for(int j=0;j<numberOfBot;){
			if (!bots[j].isShutdown() && System.currentTimeMillis() - botStartTime[j] < minutesToPlay * 60 * 1000) {
				j = 0;
				Utils.sleep(1000);
				continue;
			}
			j++;
		}

		logger.info("[Main] stop bots");

		for(int j=0;j<numberOfBot;j++){
			if(threads[j].isAlive()){
				logger.info("[Main] stop bot:{}", bots[j]);
				bots[j].stop();
			}
		}

		for(int j=0;j<numberOfBot;j++){
			logger.info("[Main] wait bot:{}", bots[j]);
			threads[j].join();
		}
	}

	private static PokemonGo login(Account account) throws Exception{
		OkHttpClient httpClient = new OkHttpClient();
		CredentialProvider cp;
		PokemonGo go;

		int maxRetry = 5;
		int retry = 0;

		logger.info("[Login] start login:{}", account.getUsername());

		while(true){
			try {
				logger.debug("[Login] username:{} password:{}", account.getUsername(), account.getPassword());
				cp = new PtcCredentialProvider(httpClient, account.getUsername(), account.getPassword());
				go = new PokemonGo(cp, httpClient);
				break;
			} catch (AsyncPokemonGoException | LoginFailedException | RemoteServerException e) {

				if (retry>=maxRetry) {
					String m = "Failed to login after retry " + retry + "/" + maxRetry+" times";
					logger.error("[Login] {}", m);
					throw e;
				}

				retry ++;
				logger.warn("[Login] Failed to login; sleep 5 sec. and then retry {}/{}", retry, maxRetry);
				Utils.sleep(5000);
			}
		}

		logger.info("[Login] success");

		return go;
	}

}
