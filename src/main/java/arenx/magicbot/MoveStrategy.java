package arenx.magicbot;

import arenx.magicbot.bean.Location;

public interface MoveStrategy {

	public Location nextLocation();
	public Location getCurrentLocation();
	public void setCurrentLocation(Location location);
}
