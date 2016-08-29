package arenx.magicbot;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.api.pokemon.PokemonCpUtils;
import com.pokegoapi.api.pokemon.PokemonMetaRegistry;
import com.pokegoapi.api.pokemon.PokemonMoveMetaRegistry;

import POGOProtos.Data.PokemonDataOuterClass.PokemonData;

public class SimplePokebankStrategy implements PokebankStrategy{

	private static Logger logger = LoggerFactory.getLogger(SimplePokebankStrategy.class);

	private Map<Integer, PokemonRule> rules = new TreeMap<>();

	@Autowired
	private AtomicReference<PokemonGo> go;

	@Autowired
	public void setConfig(@Autowired HierarchicalConfiguration<ImmutableNode> node) {
		HierarchicalConfiguration<ImmutableNode> config = node.configurationAt("simplePokemonBankStrategy");

		PokemonRule defaultRule = new PokemonRule();
		defaultRule.minAbsoluteCPpercentPercentToEnvole = config.getDouble("minAbsoluteCPpercentPercentToEnvole");
		defaultRule.minAbsoluteCPpercentPercentTokeep = config.getDouble("minAbsoluteCPpercentPercentTokeep");
		defaultRule.keepIfMoveTypeSame = config.getBoolean("keepIfMoveTypeSame");

		logger.debug("[SimplePokebankStrategy] default rule:{}", defaultRule);

		config.configurationsAt("pokemons.pokemon").forEach(c->{

			int number = Integer.parseInt((String)c.getProperty("[@number]"));

			PokemonRule rule = new PokemonRule();
			rule.minAbsoluteCPpercentPercentToEnvole = c.getDouble("minAbsoluteCPpercentPercentToEnvole", defaultRule.minAbsoluteCPpercentPercentToEnvole);
			rule.minAbsoluteCPpercentPercentTokeep = c.getDouble("minAbsoluteCPpercentPercentTokeep", defaultRule.minAbsoluteCPpercentPercentTokeep);
			rule.keepIfMoveTypeSame = c.getBoolean("keepIfMoveTypeSame", defaultRule.keepIfMoveTypeSame);

			logger.debug("[SimplePokebankStrategy] Customize for {} rule:{}", Utils.getPokemonFullName(number), rule);

			rules.put(number, rule);
		});

		PokemonMetaRegistry.getMeta().keySet().stream().map(id->id.getNumber()).forEach(number->{
			if (!rules.containsKey(number)){
				rules.put(number, defaultRule);
			}
		});
	}

	private static class PokemonRule{
		public double minAbsoluteCPpercentPercentToEnvole;
		public double minAbsoluteCPpercentPercentTokeep;
		public boolean keepIfMoveTypeSame;

		@Override
		public String toString(){
			return "EnvolveCP:" + minAbsoluteCPpercentPercentToEnvole +"%, "
					+ "TransferCP:" + minAbsoluteCPpercentPercentTokeep +"%, "
					+ " keepSameType:" + keepIfMoveTypeSame;
		}
	}

	@PostConstruct
	public void init(){

	}

	@Override
	public List<Pokemon> getToBeEnvolvePokemons() {
		return getToBeEnvolvePokemons(true);
	}

	private List<Pokemon> getToBeEnvolvePokemons(boolean filterCandy) {
		return Utils.getPokeBank(go.get())
				.getPokemons()
				.stream()
				.filter(mon->!mon.isFavorite())
				.filter(mon->{
					if (filterCandy) {
						return Utils.getCandy(mon) >= mon.getCandiesToEvolve();
					}
					return true;
				})
				.filter(mon->{
					return mon.getPokemonId() != PokemonMetaRegistry.getHighestForFamily(mon.getMeta().getFamily());
				})
				.filter(mon->{
					int max;
					try {
						max = mon.getAbsoluteMaxCp();
					} catch (Exception e) {
						logger.error("[SimplePokebankStrategy] no such pokemon "+Utils.getPokemonFullName(mon), e);
						return false;
					}
					return ((double)mon.getCp() * 100 / max) >= rules.get(mon.getMeta().getNumber()).minAbsoluteCPpercentPercentToEnvole;
				})
				.peek(mon->{
					logger.debug("[SimplePokebankStrategy] envolve {} CP:{}",
							Utils.getPokemonFullName(mon), mon.getCp());
				})
				.collect(Collectors.toList());
	}

	@Override
	public List<Pokemon> getToBeTransferedPokemons() {
		List<Pokemon> notTransfer = Utils.getPokeBank(go.get())
			.getPokemons()
			.stream()
			.filter(mon->!mon.isFavorite())
			.filter(mon->{
				int max;
				try {
					max = mon.getAbsoluteMaxCp();
				} catch (Exception e) {
					logger.error("[SimplePokebankStrategy] no such pokemon "+Utils.getPokemonFullName(mon), e);
					return false;
				}
				return ((double)mon.getCp() * 100 / max) > rules.get(mon.getMeta().getNumber()).minAbsoluteCPpercentPercentTokeep;
			})
			.filter(mon->{
				if (!rules.get(mon.getMeta().getNumber()).keepIfMoveTypeSame){
					return true;
				}

				if (
					(PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType() == mon.getMeta().getType1() ||
					PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType() == mon.getMeta().getType2()) &&

					(PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType() == mon.getMeta().getType1() ||
					PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType() == mon.getMeta().getType2())) {
					return true;
				}

				return false;
			})
			.peek(mon->{
				logger.debug("[SimplePokebankStrategy] not transfer {} CP:{} move1:{}({}) move2:{}({})",
						Utils.getPokemonFullName(mon), mon.getCp(),
						mon.getMove1(), PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType(),
						mon.getMove2(), PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType());
			})
			.collect(Collectors.toList());

		List<Pokemon> envolve = getToBeEnvolvePokemons(false);

		List<Pokemon> transfer = Utils.getPokeBank(go.get())
			.getPokemons()
			.stream()
			.filter(mon->{
				return !notTransfer.contains(mon);
			})
			.filter(mon->{
				return !envolve.contains(mon);
			})
			.peek(mon->{
				logger.debug("[SimplePokebankStrategy] transfer {} CP:{} move1:{}({}) move2:{}({})",
						Utils.getPokemonFullName(mon), mon.getCp(),
						mon.getMove1(), PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType(),
						mon.getMove2(), PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType());
			})
			.collect(Collectors.toList());

		return transfer;
	}

	@Override
	public CatchFlavor getCatchFlavor(PokemonData mon) {
		int max;

		try {
			max = PokemonCpUtils.getAbsoluteMaxCp(mon.getPokemonId());
		} catch (Exception e) {
			logger.error("[SimplePokebankStrategy] no such pokemon "+Utils.getPokemonFullName(mon.getPokemonId().getNumber()), e);
			return CatchFlavor.HOPE_TO_CATCH;
		}

		return ((double)mon.getCp() * 100 / max) > rules.get(mon.getPokemonId().getNumber()).minAbsoluteCPpercentPercentTokeep
				? CatchFlavor.HOPE_TO_CATCH : CatchFlavor.NO_DESIRE;
	}

}
