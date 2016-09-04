package arenx.magicbot;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Stats;
import com.pokegoapi.api.pokemon.Pokemon;

import POGOProtos.Data.PlayerDataOuterClass.PlayerData;
import arenx.magicbot.bean.Account;

public class SimpleInformationStrategy implements InformationStrategy{

	private static Logger logger = LoggerFactory.getLogger(SimpleInformationStrategy.class);

	@Autowired
	private AtomicReference<Account> account;

	@Autowired
	private AtomicReference<PokemonGo> go;

	@Autowired
	@Qualifier("lootedPokestopCount")
	private AtomicLong lootedPokestopCount;

	@Autowired
	@Qualifier("catchedPokemonCount")
	private AtomicLong catchedPokemonCount;

	@Autowired
	public void setConfig(@Autowired HierarchicalConfiguration<ImmutableNode> node) {
		HierarchicalConfiguration<ImmutableNode> config = node.configurationAt("simpleInformationStrategy");

		enableGoogleSheet = config.getBoolean("googleSheet[@enable]");
		googleSheetId = config.getString("googleSheet[@sheet]");
	}

	private long startTime;
	private long startExp;
	private double startKm;
	private boolean enableGoogleSheet;
	private String googleSheetId;


	@Override
	public void showStatus() {
		showExp();
		updateGoogleSheet();


		System.exit(0);
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
			startKm=stats.getKmWalked();
		} else {
			exp_hourly = (int)((stats.getExperience() - startExp) / ((double)(System.currentTimeMillis()-startTime) / (60 * 60 * 1000) ));
		}

		double km = stats.getKmWalked() - startKm;

		logger.info("[Status] {} lv:{}({}%) exp:{}/{} exp/h:{} pokestop:{}/{} pokemon:{}/{} km:{}/{} time:{}",
				data.getUsername(),
				stats.getLevel(), exp_percent,
				stats.getExperience(),stats.getNextLevelXp(),
				exp_hourly,
				lootedPokestopCount.get(),stats.getPokeStopVisits(),
				catchedPokemonCount.get(),stats.getPokemonsCaptured(),
				String.format("%.1f", km),String.format("%.1f", stats.getKmWalked()),
				DurationFormatUtils.formatDuration(System.currentTimeMillis()-startTime, "HH'h':mm'm'"));

		lastTimeshowExp = System.currentTimeMillis();

	}

	private List<List<Object>> getRowData(){
		List<List<Object>> arrData = new ArrayList<>();

		arrData.addAll(getRowData_summary());

		arrData.add(Arrays.asList("","","","","","","","","","","",""));
		arrData.addAll(getRowData_pokemon());

		arrData.add(Arrays.asList("","","","","","","","","","","",""));

		return arrData;
	}

	private List<List<Object>> getRowData_summary(){
		List<List<Object>> arrData = new ArrayList<>();

		PlayerData playerData = Utils.getPlayerData(go.get());
		Stats stats = Utils.getStats(go.get());

		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		arrData.add(Arrays.asList("name","level", "exp", "pokestop", "pokemon", "walked(KM)", "last update"));
		arrData.add(Arrays.asList(playerData.getUsername(), stats.getLevel(), stats.getExperience()+"/"+stats.getNextLevelXp(),
				stats.getPokeStopVisits(), stats.getPokemonsCaptured(), String.format("%.1f", stats.getKmWalked()),
				format.format(new Date())));

		return arrData;
	}

	private List<List<Object>> getRowData_pokemon(){
		List<List<Object>> data = new ArrayList<>();

		List<Pokemon> pokemons = Utils.getPokeBank(go.get()).getPokemons();

		data.add(Arrays.asList("number", "name", "CP", "level", "IV", "skill 1", "skill 2",  "attack", "defence", "stamina", "captured date"));

		pokemons
			.stream()
			.sorted((a,b)->Integer.compare(a.getMeta().getNumber(), b.getMeta().getNumber()))
			.forEach(mon->{
				data.add(Arrays.asList(
					mon.getMeta().getNumber(),
					Utils.getTranslatedPokemonName(mon)+"("+Utils.getPokemonName(mon)+")",
					mon.getCp(),
					mon.getLevel(),
					String.format("%.1f%%", mon.getIvRatio()*100),
					mon.getMove1().toString(), mon.getMove2().toString(),
					mon.getIndividualAttack(),mon.getIndividualDefense(),mon.getIndividualStamina(),
					new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(mon.getCreationTimeMs()))
				));
			});;

		return data;
	}

	/** Application name. */
	private static final String APPLICATION_NAME = "Google Sheets API Java Quickstart";

	/** Directory to store user credentials for this application. */
	private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),
			".credentials/sheets.googleapis.com-java-quickstart");

	/** Global instance of the {@link FileDataStoreFactory}. */
	private static FileDataStoreFactory DATA_STORE_FACTORY;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	private static HttpTransport HTTP_TRANSPORT;

	/**
	 * Global instance of the scopes required by this quickstart.
	 *
	 * If modifying these scopes, delete your previously saved credentials at
	 * ~/.credentials/sheets.googleapis.com-java-quickstart
	 */
	private static final List<String> SCOPES = Arrays.asList(SheetsScopes.SPREADSHEETS);

	static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

	/**
	 * Creates an authorized Credential object.
	 *
	 * @return an authorized Credential object.
	 * @throws IOException
	 */
	private static Credential authorize() throws IOException {
		// Load client secrets.
		InputStream in = SimpleInformationStrategy.class.getResourceAsStream("/client_secret.json");
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecrets, SCOPES).setDataStoreFactory(DATA_STORE_FACTORY).setAccessType("offline").build();
		Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		logger.debug("[Status] Credentials saved to {}", DATA_STORE_DIR);
		return credential;
	}

	private static Sheets getSheetsService() throws IOException {
		Credential credential = authorize();
		return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME)
				.build();
	}

	private long lastTimeUpdateGoogleSheet = 0;

	private void updateGoogleSheet() {

		if (enableGoogleSheet) {
			return;
		}

		if (System.currentTimeMillis() - lastTimeUpdateGoogleSheet < 10 * 60 * 1000) {
			return;
		}

		logger.debug("[Status] update google sheet");

		try{
		// Build a new authorized API client service.
		Sheets service = getSheetsService();

		ValueRange oRange = new ValueRange();
		oRange.setRange(account.get().getUsername()+"!A1:Z1000"); // I NEED THE NUMBER OF THE LAST ROW
		oRange.setValues(getRowData());

		List<ValueRange> oList = new ArrayList<>();
		oList.add(oRange);

		BatchUpdateValuesRequest bu = new BatchUpdateValuesRequest();
		bu.setValueInputOption("RAW");
		bu.setData(oList);

		service.spreadsheets().values().batchUpdate(googleSheetId, bu).execute();
		} catch (IOException e){
			logger.warn("[Status] Failed to update google sheet", e);
		}

		lastTimeUpdateGoogleSheet = System.currentTimeMillis();
    }


}
