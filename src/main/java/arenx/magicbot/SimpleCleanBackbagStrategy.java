package arenx.magicbot;

import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Inventories;
import com.pokegoapi.api.inventory.Item;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Responses.RecycleInventoryItemResponseOuterClass.RecycleInventoryItemResponse;

public class SimpleCleanBackbagStrategy implements Strategy{

	private static Logger logger = LoggerFactory.getLogger(SimpleCleanBackbagStrategy.class);
	private PokemonGo go;
	
	public SimpleCleanBackbagStrategy(PokemonGo go){
		this.go=go;
	}
	
	@Override
	public void execute() {

		refreshInventories();
		removeBackBagItems();
		
		
	}
	
	private void refreshInventories() {
		int retry = 1;

		Utils.sleep(1000);
		
		while (retry <= Config.instance.getMaxRetryWhenServerError()) {
			try {
				go.getInventories().updateInventories();
				break;
			} catch (Exception e) {
				logger.warn("[CleanBackbag] Faile to get Inventories; retry {}/{}", retry,
						Config.instance.getMaxRetryWhenServerError());
				Utils.sleep(RandomUtils.nextLong(2000, 5000));
			}
			retry++;
		}

		if (retry == Config.instance.getMaxRetryWhenServerError()) {
			String m = "[CleanBackbag] Failed to get Inventories";
			logger.error(m);
			throw new RuntimeException(m);
		}

	}
	
	private void removeBackBagItems(){
		
		List<Entry<ItemId, Integer>> ids = new ArrayList();
		
		Inventories inventories;
		try {
			inventories = go.getInventories();
		} catch (LoginFailedException | RemoteServerException e2) {
			String m = "[CleanBackbag] Failed to get inventories";
			logger.error(m,e2);
			throw new RuntimeException(m);
		}
		
		Item berry = inventories.getItemBag().getItem(ItemId.ITEM_RAZZ_BERRY);		
		
		if (berry!=null && berry.getCount() > Config.instance.getBackBag().getMaxBerryToKeep()) {
			ids.add(new SimpleEntry<ItemId, Integer>(berry.getItemId(), berry.getCount() - Config.instance.getBackBag().getMaxBerryToKeep()));
		}
		
		Item revive = inventories.getItemBag().getItem(ItemId.ITEM_REVIVE);		
		
		if (revive!=null && revive.getCount() > Config.instance.getBackBag().getMaxReviveToKeep()) {
			ids.add(new SimpleEntry<ItemId, Integer>(revive.getItemId(), revive.getCount() - Config.instance.getBackBag().getMaxReviveToKeep()));
		}
		

		Item ball_poke = inventories.getItemBag().getItem(ItemId.ITEM_POKE_BALL);
		Item ball_great = inventories.getItemBag().getItem(ItemId.ITEM_GREAT_BALL);
		Item ball_ultra = inventories.getItemBag().getItem(ItemId.ITEM_ULTRA_BALL);
		Item ball_master = inventories.getItemBag().getItem(ItemId.ITEM_MASTER_BALL);
		
		int ball_count = 0;
		if (ball_poke != null) {
			ball_count += ball_poke.getCount();
		}
		if (ball_great != null) {
			ball_count += ball_great.getCount();
		}
		if (ball_ultra != null) {
			ball_count += ball_ultra.getCount();
		}
		if (ball_master != null) {
			ball_count += ball_master.getCount();
		}
		ball_count -= Config.instance.getBackBag().getMaxBallToKeep();
		
		if (ball_count > 0) {
			if (ball_poke != null && ball_count > 0) {
				if (ball_poke.getCount() > ball_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(ball_poke.getItemId(), ball_count));
					ball_count = 0;
				} else if (ball_poke.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(ball_poke.getItemId(), ball_poke.getCount()));
					ball_count -= ball_poke.getCount();
				}
			}
			if (ball_great != null && ball_count > 0) {
				if (ball_great.getCount() > ball_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(ball_great.getItemId(), ball_count));
					ball_count = 0;
				} else if (ball_great.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(ball_great.getItemId(), ball_great.getCount()));
					ball_count -= ball_great.getCount();
				}
			}
			if (ball_ultra != null && ball_count > 0) {
				if (ball_ultra.getCount() > ball_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(ball_ultra.getItemId(), ball_count));
					ball_count = 0;
				} else if (ball_ultra.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(ball_ultra.getItemId(), ball_ultra.getCount()));
					ball_count -= ball_ultra.getCount();
				}
			}
			if (ball_master != null && ball_count > 0) {
				if (ball_master.getCount() > ball_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(ball_master.getItemId(), ball_count));
					ball_count = 0;
				} else if (ball_master.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(ball_master.getItemId(), ball_master.getCount()));
					ball_count -= ball_master.getCount();
				}
			}
		}
		

		Item potion = inventories.getItemBag().getItem(ItemId.ITEM_POTION);
		Item potion_hyper = inventories.getItemBag().getItem(ItemId.ITEM_HYPER_POTION);
		Item potion_super = inventories.getItemBag().getItem(ItemId.ITEM_SUPER_POTION);
		Item potion_max = inventories.getItemBag().getItem(ItemId.ITEM_MAX_POTION);
		
		int potion_count = 0;
		if (potion != null) {
			potion_count += potion.getCount();
		}
		if (potion_hyper != null) {
			potion_count += potion_hyper.getCount();
		}
		if (potion_super != null) {
			potion_count += potion_super.getCount();
		}
		if (potion_max != null) {
			potion_count += potion_max.getCount();
		}
		potion_count -= Config.instance.getBackBag().getMaxPotionToKeep();
		
		if (potion_count > 0) {
			if (potion != null && potion_count > 0) {
				if (potion.getCount() > potion_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion.getItemId(), potion_count));
					potion_count = 0;
				} else if (potion.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(potion.getItemId(), potion.getCount()));
					potion_count -= potion.getCount();
				}
			}
			if (potion_hyper != null && potion_count > 0) {
				if (potion_hyper.getCount() > potion_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion_hyper.getItemId(), potion_count));
					potion_count = 0;
				} else if (potion_hyper.getCount() > 0) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion_hyper.getItemId(), potion_hyper.getCount()));
					potion_count -= potion_hyper.getCount();
				}
			}
			if (potion_super != null && potion_count > 0) {
				if (potion_super.getCount() > potion_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion_super.getItemId(), potion_count));
					potion_count = 0;
				} else if (potion_super.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(potion_super.getItemId(), potion_super.getCount()));
					potion_count -= potion_super.getCount();
				}
			}
			if (potion_max != null && potion_count > 0) {
				if (potion_max.getCount() > potion_count) {
					ids.add(new SimpleEntry<ItemId, Integer>(potion_max.getItemId(), potion_count));
					potion_count = 0;
				} else if (potion_max.getCount() > 0){
					ids.add(new SimpleEntry<ItemId, Integer>(potion_max.getItemId(), potion_max.getCount()));
					potion_count -= potion_max.getCount();
				}
			}
		}
		
		ids.forEach(e->{
			logger.info("[CleanBackbag] remove:{} count:{}", e.getKey(), e.getValue());			
			
			int retry = 1;
			
			PokestopLootResult result = null;

			while (retry <= Config.instance.getMaxRetryWhenServerError()) {
				try {
					RecycleInventoryItemResponse.Result r = inventories.getItemBag().removeItem(e.getKey(), e.getValue());
					switch (r) {
					case SUCCESS:
						break;
					case ERROR_CANNOT_RECYCLE_INCUBATORS:
					case ERROR_NOT_ENOUGH_COPIES:					
					case UNRECOGNIZED:
					case UNSET:
					default:
						logger.error("[CleanBackbag] Failed to recycle remove:{} count:{} {}", e.getKey(), e.getValue(), r);
						break;
					}
					Utils.sleep(Config.instance.getDelayMsBetweenApiRequestRetry());
					break;
				} catch (Exception e1) {
					logger.warn("[CleanBackbag] Faile to remove item:{} count: {}; retry {}/{}", e.getKey(), e.getValue(), retry,
							Config.instance.getMaxRetryWhenServerError());
					Utils.sleep(RandomUtils.nextLong(2000, 5000));
				}
				retry++;
			}

			if (retry == Config.instance.getMaxRetryWhenServerError()) {
				String m = "[CleanBackbag] Failed to remove item:" + e.getKey() + " count:" + e.getValue();
				logger.error(m);
				throw new RuntimeException(m);
			}
			
		});
	}

}
