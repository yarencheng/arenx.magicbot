package arenx.magicbot;

import java.util.Map;

import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;

public interface BackbagStrategy {

	public Map<ItemId, Integer> getTobeRemovedItem();
}
