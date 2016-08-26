package arenx.magicbot.bean;

import java.util.ArrayList;
import java.util.List;

public class PokeRadar {

	private List<Pokemon>data;
	private List<String>errors;
	private Boolean success;

	public List<Pokemon> getData() {
		return new ArrayList<>(data);
	}

	public void setData(List<Pokemon> data) {
		this.data = new ArrayList<>(data);
	}

	public List<String> getErrors() {
		return new ArrayList<>(errors);
	}

	public void setErrors(List<String> errors) {
		this.errors = new ArrayList<>(errors);
	}

	public Boolean getSuccess() {
		return new Boolean(success);
	}

	public void setSuccess(Boolean success) {
		this.success = new Boolean(success);
	}

	public static class Pokemon{
		private Long created;
		private String deviceId;
		private Integer downvotes;
		private String id;
		private Double latitude;
		private Double longitude;
		private Integer pokemonId;
		private String trainerName;
		private Integer upvotes;
		private String userId;

		public Long getCreated() {
			return new Long(created);
		}
		public void setCreated(Long created) {
			this.created = new Long(created);
		}
		public String getDeviceId() {
			return new String(deviceId);
		}
		public void setDeviceId(String deviceId) {
			this.deviceId = new String(deviceId);
		}
		public Integer getDownvotes() {
			return new Integer(downvotes);
		}
		public void setDownvotes(Integer downvotes) {
			this.downvotes = new Integer(downvotes);
		}
		public String getId() {
			return new String(id);
		}
		public void setId(String id) {
			this.id = new String(id);
		}
		public Double getLatitude() {
			return new Double(latitude);
		}
		public void setLatitude(Double latitude) {
			this.latitude = new Double(latitude);
		}
		public Double getLongitude() {
			return new Double(longitude);
		}
		public void setLongitude(Double longitude) {
			this.longitude = new Double(longitude);
		}
		public Integer getPokemonId() {
			return new Integer(pokemonId);
		}
		public void setPokemonId(Integer pokemonId) {
			this.pokemonId = new Integer(pokemonId);
		}
		public String getTrainerName() {
			return new String(trainerName);
		}
		public void setTrainerName(String trainerName) {
			this.trainerName = new String(trainerName);
		}
		public Integer getUpvotes() {
			return new Integer(upvotes);
		}
		public void setUpvotes(Integer upvotes) {
			this.upvotes = new Integer(upvotes);
		}
		public String getUserId() {
			return new String(userId);
		}
		public void setUserId(String userId) {
			this.userId = new String(userId);
		}

		public int remainingSecond(){
			int sec = (15*60) - (int) (System.currentTimeMillis()/1000 - created);
			return sec > 0 ? sec : 1;
		}

	}
}
