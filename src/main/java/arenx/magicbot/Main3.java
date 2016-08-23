package arenx.magicbot;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import arenx.magicbot.bean.Account;

public class Main3 {

	private static Logger logger = LoggerFactory.getLogger(Main3.class);

	public static void main(String[] args) {
		try {
			startBots();
		} catch (Throwable e) {
			logger.error("[Main] Some thing goes wrong", e);
		}
	}

	public static void startBots() throws Exception {

		HierarchicalConfiguration<ImmutableNode> config = getconfig();

		int minutesToPlay = config.getInt("minutesToPlay");
		int minutesToRest = config.getInt("minutesToRest");
		int numberOfThread = config.getInt("numberOfThread");

		List<Account>accounts=getAccounts(config);
		List<Bot> bots = accounts
				.stream()
				.map(account->{

					AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(BotConfig.class);
					Bot b = context.getBean(Bot.class);

					b.setAccount(account);
					b.setMinutesToPlay(minutesToPlay);

					return b;
				})
				.collect(Collectors.toList());

		ScheduledThreadPoolExecutor ste = new ScheduledThreadPoolExecutor(numberOfThread);

		bots.forEach(bot->{
			ste.scheduleAtFixedRate(bot, 0, minutesToPlay + minutesToRest, TimeUnit.MINUTES);
		});

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				bots.forEach(bot->bot.storeState());
			}
		});


	}

	public static HierarchicalConfiguration<ImmutableNode> getconfig(){
		try {
			return new Configurations().xml("config.xml");
		} catch (ConfigurationException e) {
			String m = "Failed to parse config file";
			logger.error("[Main] " + m, e);
			throw new RuntimeException(m, e);
		}
	}

	public static List<Account> getAccounts(HierarchicalConfiguration<ImmutableNode> config){
		return config.configurationsAt("accounts.account")
			.stream()
			.map(a->{
				Account account = new Account();
				account.setUsername(a.getString("username"));
				account.setPassword(a.getString("password"));
				return account;
			})
			.collect(Collectors.toList());
	}

}
