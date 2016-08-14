package arenx.magicbot;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

public class TmpData {
	
	private static Logger logger = LoggerFactory.getLogger(Main.class);
	public static TmpData instance;
	private static final String filePath = "tmpdata.json" ;
	
	static {
		File f = new File(filePath);
		
		if (!f.exists()){
			instance = new TmpData();
		} else {
			JsonFactory jf = new JsonFactory ();
			jf.enable(JsonParser.Feature.ALLOW_COMMENTS);
			
			ObjectMapper mapper = new ObjectMapper(jf);
			try {
				instance = mapper.readValue(f, TmpData.class);
				logger.info("tmpdata[{}] loaded", f.getAbsolutePath());
			} catch (UnrecognizedPropertyException e) {
				String message = "unknown key[" + e.getPropertyName() + "] is found in " + f.getAbsolutePath();
				logger.error(message, e);
				throw new RuntimeException(message, e);
			} catch (InvalidFormatException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException(e.getMessage(), e);
			} catch (IOException e) {
				String message = "Failed to convert tmpdata from json[" + f.getAbsolutePath() +"]";
				logger.error(message,e);
				throw new RuntimeException(message, e);
			}
		}		
	}
	
	public void saveToFile(){
		ObjectMapper mapper = new ObjectMapper();
		mapper.setSerializationInclusion(Include.NON_NULL);
    	try {
    		File f = new File(filePath);
			mapper.writerWithDefaultPrettyPrinter().writeValue(f, instance);
			logger.info("tmpdata[{}] saved", f.getAbsolutePath());
		} catch (IOException e) {
			logger.error("Failed to convert tmpdata to json",e);
			throw new RuntimeException("Failed to convert tmpdata to json", e);
		}
	}

	private TmpData(){
		
	}
	
	@JsonProperty("GoogleRefreshToken")
	private String googleRefreshToken;
	

	public String getGoogleRefreshToken() {
		return googleRefreshToken;
	}

	public void setGoogleRefreshToken(String googleRefreshToken) {
		this.googleRefreshToken = googleRefreshToken;
	}
	
	@JsonProperty(value = "LastLongitude")
	private Double lastLongitude;

	public Double getLastLongitude() {
		return lastLongitude;
	}
	
	@JsonProperty(value = "LastLatitude")
	private Double lastLatitude;

	public Double getLastLatitude() {
		return lastLatitude;
	}
	
	@JsonProperty(value = "LastAltitude")
	private Double lastAltitude;

	public Double getLastAltitude() {
		return lastAltitude;
	}

	public void setLastLongitude(Double lastLongitude) {
		this.lastLongitude = lastLongitude;
	}

	public void setLastLatitude(Double lastLatitude) {
		this.lastLatitude = lastLatitude;
	}

	public void setLastAltitude(Double lastAltitude) {
		this.lastAltitude = lastAltitude;
	}
	
}
