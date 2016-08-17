package arenx.magicbot.bean;

public class Location {

	public Location(){

	}

	public Location(Double latitude, Double longitude, Double altitude) {
		super();
		this.latitude = latitude;
		this.longitude = longitude;
		this.altitude = altitude;
	}

	private Double latitude;
	private Double longitude;
	private Double altitude;
	public Double getLatitude() {
		return latitude;
	}
	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}
	public Double getLongitude() {
		return longitude;
	}
	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}
	public Double getAltitude() {
		return altitude;
	}
	public void setAltitude(Double altitude) {
		this.altitude = altitude;
	}

	@Override
	public String toString(){
		return "(lat:" + latitude + ", long:" + longitude + ", alt:" + altitude + ")";
	}

}
