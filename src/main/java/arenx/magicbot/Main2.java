package arenx.magicbot;

import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.GoogleUserCredentialProvider;
import com.pokegoapi.auth.PtcCredentialProvider;

import okhttp3.OkHttpClient;

public class Main2 {

	private static Logger logger = LoggerFactory.getLogger(Main2.class);

	private PokemonGo go;
	private Strategy walkingStrategy;
	private Strategy lootStrategy;
	private Strategy cleanBackbagStrategy;
	private Strategy infoStrategy;
	private Strategy pokemonEncounterStrategy;
	private Strategy transferStrategy;
	private Strategy levelUpStrategy;

	public static void main(String[] argv) {
		try {
			new Main2();
		} catch (Throwable e) {
			logger.error("Someting gose wrong", e);
		}

	}

	private Main2(){
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    @Override
			public void run() {
		    	SaveCurrentState();
		    }
		 });

		logger.info("login ...");
		go = login();

		walkingStrategy = new ShortestPathWalkingStrategy(go);
		lootStrategy = new SimpleLootPokestopStrategy(go);
		cleanBackbagStrategy = new SimpleCleanBackbagStrategy(go);
		infoStrategy = new SimpleInformationStrategy(go);
		pokemonEncounterStrategy = new SimplePokemonEncounterStrategy(go);
		transferStrategy = new SimplePokemonTransferStrategy(go);
		levelUpStrategy = new SimpleLevelUpStrategy(go);

		((SimpleLootPokestopStrategy)lootStrategy).setCleanBackbagStrategy(cleanBackbagStrategy);
		((SimplePokemonEncounterStrategy)pokemonEncounterStrategy).setInformationStrategy(infoStrategy);

		((SimpleInformationStrategy)infoStrategy).showAll();

		while(true){
			levelUpStrategy.execute();
			transferStrategy.execute();

			walkingStrategy.execute();
			pokemonEncounterStrategy.execute();
			lootStrategy.execute();

//			break;
		}
	}

	private void SaveCurrentState() {
		TmpData.instance.setLastAltitude(go.getAltitude());
		TmpData.instance.setLastLongitude(go.getLongitude());
		TmpData.instance.setLastLatitude(go.getLatitude());

		TmpData.instance.saveToFile();
	}

	private static PokemonGo login() {
		switch (Config.instance.getAuth().getAuthType()) {
		case GOOGLE:

			try {
				OkHttpClient httpClient = new OkHttpClient();

				String refreshToken = TmpData.instance.getGoogleRefreshToken();
				if (!StringUtils.isEmpty(refreshToken)) {

					logger.info("google login with refresh token:[{}]", refreshToken);
					try {
						return new PokemonGo(new GoogleUserCredentialProvider(httpClient, refreshToken), httpClient);
					} catch (Exception e1) {
						logger.warn("failed to login with refresh token; start over again");
					}
				}

				System.out.println("Please go to " + GoogleUserCredentialProvider.LOGIN_URL);
				System.out.println("Enter authorisation code:");

				Scanner sc = new Scanner(System.in);
				String access = sc.nextLine();
				GoogleUserCredentialProvider provider = new GoogleUserCredentialProvider(httpClient);
				provider.login(access);

				PokemonGo go = new PokemonGo(provider, httpClient);

				TmpData.instance.setGoogleRefreshToken(provider.getRefreshToken());

				return go;

			} catch (Exception e1) {
				String message = "Failed to login";
				logger.error(message, e1);
				throw new RuntimeException(message, e1);
			}

		case PTC:

			try {
				OkHttpClient httpClient = new OkHttpClient();

				String username = Config.instance.getAuth().getPtcUsername();
				String password = Config.instance.getAuth().getPtcPassword();

				PokemonGo go = new PokemonGo(new PtcCredentialProvider(httpClient,username,password),httpClient);

				return go;
			} catch (Exception e1) {
				String message = "Failed to login";
				logger.error(message, e1);
				throw new RuntimeException(message, e1);
			}
		default:
			RuntimeException e = new RuntimeException("Unknown auth type");
			logger.error(e.getMessage(), e);
			throw e;
		}
	}
}
