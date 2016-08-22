package arenx.magicbot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

	@Bean
	public InformationStrategy getInformationStrategy(){
		return new SimpleInformationStrategy();
	}

	@Bean
	public PokebankStrategy getPokebankStrategy(){
		return new SimplePokebankStrategy();
	}

	@Bean
	@Qualifier("lootedPokestopCount")
	public AtomicLong getLootedPokestopCount(){
		return new AtomicLong(0);
	}

	@Bean
	@Qualifier("catchedPokemonCount")
	public AtomicLong getCatchedPokemonCount(){
		return new AtomicLong(0);
	}

	public static void startBots() throws Exception {

		XMLConfiguration config = null;
		try {
			config = new Configurations().xml("config.xml");
		} catch (ConfigurationException e) {
			logger.error("[Main] Failed to parse config file", e);
			return;
		}

		List<Account>accounts=new ArrayList<>();
		config.configurationsAt("accounts.account")
			.forEach(a->{
				Account account = new Account();
				account.setUsername(a.getString("username"));
				account.setPassword(a.getString("password"));
				accounts.add(account);
			});

		int numberOfBot = accounts.size();
		Thread[] threads = new Thread[numberOfBot];
		Bot[] bots = new Bot[numberOfBot];

		for(int i=0;i<accounts.size();){

			final int i_ = i;
			threads[i] = new Thread(){
				@Override
				public void run(){
					AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main3.class);
					bots[i_] = context.getBean(Bot.class);

					int maxRestart=5;
					int restart=0;
					while (true) {

						try{
							PokemonGo go = login(accounts.get(i_));
							bots[i_].setPokemonGo(go);
							bots[i_].start();
						}catch (Throwable e){
							logger.error("[Main] Some thing goes wrong inside current bot", e);
							Utils.sleep(1000);

							if (restart<=maxRestart){
								logger.error("[Main] restart bot {}/{}", restart, maxRestart);
								restart++;
							} else {
								throw new RuntimeException("[Main] this bot got "+restart+" times of error", e);
							}
						}

						bots[i_].storeState();
					}
				}
			};

			threads[i].setUncaughtExceptionHandler((thread, e)->{
				logger.error("[Main] Some thing goes wrong inside bot thread", e);
				System.exit(-1);
			});
			threads[i].setName(accounts.get(i).getUsername());

			logger.info("[Main] start bot:{}", bots[i]);
			threads[i].start();

			i++;
		}


		for(int j=0;j<numberOfBot;j++){
			final int j_ = j;
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					bots[j_].storeState();
				}
			});
		}

		logger.info("[Main] wait bots to stop");

		for(int j=0;j<numberOfBot;j++){
			logger.info("[Main] wait bot:{} to stop", bots[j]);
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
