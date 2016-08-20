package arenx.magicbot;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.ItemBag;

import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;

public class SimpleBackbagStrategy implements BackbagStrategy {

	private static Logger logger = LoggerFactory.getLogger(SimpleBackbagStrategy.class);

	private HierarchicalConfiguration<ImmutableNode> config;

	@Autowired
	public void setConfig(@Autowired HierarchicalConfiguration<ImmutableNode> config) {
		this.config = config.configurationAt("simpleBackbagStrategy");
	}

	@Autowired
	private AtomicReference<PokemonGo> go;

	@Override
	public Map<ItemId, Integer> getTobeRemovedItem() {

		Map<ItemId, Integer> removeItems = new TreeMap<>();

		ItemBag itemBag = Utils.getInventories(go.get()).getItemBag();

		int all = itemBag.getItems().stream()
				.mapToInt(item -> item.getCount()).sum();

		logger.debug("[BackbagStrategy] {}/{} items in backbag", all, Utils.getPlayerData(go.get()).getMaxItemStorage());

		if (all < 340) {
			return removeItems;
		}

		List<ItemId> ids_ball = Arrays.asList(ItemId.ITEM_POKE_BALL, ItemId.ITEM_GREAT_BALL, ItemId.ITEM_ULTRA_BALL, ItemId.ITEM_MASTER_BALL);
		List<ItemId> ids_potion = Arrays.asList(ItemId.ITEM_POTION, ItemId.ITEM_HYPER_POTION, ItemId.ITEM_SUPER_POTION, ItemId.ITEM_MAX_POTION);
		List<ItemId> ids_berry = Arrays.asList(ItemId.ITEM_RAZZ_BERRY);
		List<ItemId> ids_revive = Arrays.asList(ItemId.ITEM_REVIVE);

		itemBag.getItems().stream()
				.filter(item -> !ids_ball.contains(item.getItemId()))
				.filter(item -> !ids_potion.contains(item.getItemId()))
				.filter(item -> !ids_berry.contains(item.getItemId()))
				.filter(item -> !ids_revive.contains(item.getItemId()))
				.filter(item -> item.getItemId() != ItemId.ITEM_LUCKY_EGG)
				.filter(item -> item.getItemId() != ItemId.ITEM_INCUBATOR_BASIC_UNLIMITED)
				.filter(item -> item.getItemId() != ItemId.ITEM_INCUBATOR_BASIC_UNLIMITED)
				.filter(item -> item.getItemId() != ItemId.ITEM_SPECIAL_CAMERA)
				.filter(item -> item.getItemId() != ItemId.ITEM_INCENSE_ORDINARY)
				.forEach(item -> {
					logger.warn("[SimpleBackbagStrategy] {} {} is not handled", item.getCount(), item.getItemId());
				});
		;

		removeItems.putAll(getTobeRemoveditems(
				itemBag, config.getInt("maxBallToKeep"),
				ids_ball));

		removeItems.putAll(getTobeRemoveditems(
				itemBag, config.getInt("maxPotionToKeep"),
				ids_potion));

		removeItems.putAll(getTobeRemoveditems(
				itemBag, config.getInt("maxBerryToKeep"),
				ids_berry));

		removeItems.putAll(getTobeRemoveditems(
				itemBag, config.getInt("maxReviveToKeep"),
				ids_revive));

		logger.debug("[SimpleBackbagStrategy] remove items:{}", removeItems);

		return removeItems;
	}

	private Map<ItemId, Integer> getTobeRemoveditems(ItemBag itemBag, int max, List<ItemId> ids) {

		Map<ItemId, Integer> removeItems = new TreeMap<>();

		int all = itemBag.getItems().stream().filter(item -> ids.contains(item.getItemId()))
				.mapToInt(item -> item.getCount()).sum();

		all -= max;

		if(all <= 0) {
			return removeItems;
		}

		for (ItemId id : ids) {
			if (all > 0) {
				int count = itemBag.getItems().stream().filter(item -> item.getItemId() == id)
						.mapToInt(item -> item.getCount()).sum();
				if (count > 0) {
					if (all > count) {
						all -= count;
						removeItems.put(id, count);
						logger.debug("[SimpleBackbagStrategy] remove {} {}", id, count);
					} else {
						removeItems.put(id, all);
						logger.debug("[SimpleBackbagStrategy] remove {} {}",  id, all);
						all = 0;
					}
				}
			}
		}

		return removeItems;
	}



}
