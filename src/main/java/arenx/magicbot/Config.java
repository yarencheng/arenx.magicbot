package arenx.magicbot;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

public class Config {

	private static Logger logger = LoggerFactory.getLogger(Config.class);
	public static Config instance;
	private static final String filePath = "config.json";

	static {
		File f = new File(filePath);

		if (!f.exists()) {
			String message = "There is no config file[" + f.getAbsolutePath() + "]";
			logger.error(message);
			throw new RuntimeException(message);
		}

		JsonFactory jf = new JsonFactory();
		jf.enable(JsonParser.Feature.ALLOW_COMMENTS);

		ObjectMapper mapper = new ObjectMapper(jf);
		try {
			instance = mapper.readValue(new File(filePath), Config.class);
			logger.info("config[{}] loaded", f.getAbsolutePath());
		} catch (UnrecognizedPropertyException e) {
			String message = "unknown key[" + e.getPropertyName() + "] is found in " + f.getAbsolutePath();
			logger.error(message, e);
			throw new RuntimeException(message, e);
		} catch (InvalidFormatException e) {
			logger.error(e.getMessage(), e);
			throw new RuntimeException(e.getMessage(), e);
		} catch (IOException e) {
			String message = "Failed to convert config from json";
			logger.error(message, e);
			throw new RuntimeException(message, e);
		}

	}

	enum AuthType {
		GOOGLE, PTC
	}

	@JsonProperty(value = "AuthType", required = true)
	private AuthType authType;

	public AuthType getAuthType() {
		return authType;
	}
	
	@JsonProperty(value = "MaxRetryWhenServerError")
	private int maxRetryWhenServerError;

	public int getMaxRetryWhenServerError() {
		return maxRetryWhenServerError;
	}
	
	@JsonProperty(value = "DelayMsBetweenApiRequestRetry")
	private long delayMsBetweenApiRequestRetry;

	public long getDelayMsBetweenApiRequestRetry() {
		return delayMsBetweenApiRequestRetry;
	}
	
	@JsonProperty(value = "DefaultLongitude")
	private double defaultLongitude;

	public Double getDefaultLongitude() {
		return defaultLongitude;
	}
	
	@JsonProperty(value = "DefaultLatitude")
	private Double defaultLatitude;

	public Double getDefaultLatitude() {
		return defaultLatitude;
	}
	
	@JsonProperty(value = "DefaultAltitude")
	private Double defaultAltitude;

	public Double getDefaultAltitude() {
		return defaultAltitude;
	}
	
	@JsonProperty(value = "SpeedPerSecond")
	private Double speedPerSecond;

	public Double getSpeedPerSecond() {
		return speedPerSecond;
	}
	
	@JsonProperty(value = "BackBag")
	private BackBag backBag;

	public BackBag getBackBag() {
		return backBag;
	}

	public static class BackBag{
		@JsonProperty(value = "MaxReviveToKeep")
		private Integer maxReviveToKeep;

		public Integer getMaxReviveToKeep() {
			return maxReviveToKeep;
		}
		
		@JsonProperty(value = "MaxBallToKeep")
		private Integer maxBallToKeep;

		public Integer getMaxBallToKeep() {
			return maxBallToKeep;
		}
		
		@JsonProperty(value = "MaxPotionToKeep")
		private Integer maxPotionToKeep;

		public Integer getMaxPotionToKeep() {
			return maxPotionToKeep;
		}
		
		@JsonProperty(value = "MaxBerryToKeep")
		private Integer maxBerryToKeep;

		public Integer getMaxBerryToKeep() {
			return maxBerryToKeep;
		}
	}

}
