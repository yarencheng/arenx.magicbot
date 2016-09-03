package arenx.magicbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.annimon.stream.IntStream;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.api.pokemon.PokemonDetails;
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

		config.configurationsAt("pokemonGroups.pokemonGroup").forEach(c->{

			String name = (String) c.getProperty("[@name]");
			logger.debug("[SimplePokebankStrategy] group {}", name);

			HierarchicalConfiguration<ImmutableNode> config_group_keep = c.configurationAt("keepPolicy");
			HierarchicalConfiguration<ImmutableNode> config_group_envolve = c.configurationAt("envolvePolicy");

			KeepPolicy group_keep = parseKeepPolicy(config_group_keep, null);
			EnvolvePolicy group_envolve = parseEnvolvePolicy(config_group_envolve, null);

			c.configurationsAt("pokemons.pokemon").forEach(cp->{
				int number = Integer.parseInt((String) cp.getProperty("[@number]"));
				logger.debug("[SimplePokebankStrategy] pokemon #{} {}", number, Utils.getPokemonFullName(number));

				PokemonRule p = new PokemonRule();
				p.number = number;

				if (cp.getProperty("[@envolveTarget]") != null) {
					String[] tokens = ((String) cp.getProperty("[@envolveTarget]")).split(",");
					p.envolveTarget = Arrays.asList(tokens).stream().map(s->Integer.parseInt(s)).collect(Collectors.toList());
				} else {
					p.envolveTarget = new ArrayList<>();
				}

				try{
					HierarchicalConfiguration<ImmutableNode> config_keep = cp.configurationAt("keepPolicy");
					p.keepPolicy = parseKeepPolicy(config_keep, group_keep);
				} catch (ConfigurationRuntimeException e) {
					p.keepPolicy = group_keep;
				}

				try{
					HierarchicalConfiguration<ImmutableNode> config_envolve = cp.configurationAt("envolvePolicy");
					p.envolvePolicy = parseEnvolvePolicy(config_envolve, group_envolve);
				} catch (ConfigurationRuntimeException e) {
					p.envolvePolicy = group_envolve;
				}

				if (rules.containsKey(number)) {
					String m = "duplicated rules for number:" + number;
					logger.error("[SimplePokebankStrategy] {}", m);
					throw new RuntimeException(m);
				}
				rules.put(number, p);
			});
		});

		if (logger.isDebugEnabled()){
			rules.values().stream().forEach(p->logger.debug("[SimplePokebankStrategy] {}", p));
		}

		int total = PokemonMetaRegistry.getMeta().size();
		long miss = IntStream.rangeClosed(1, total)
			.filter(n->!rules.containsKey(n))
			.peek(n->logger.warn("[SimplePokebankStrategy] no rule of {} is defined", Utils.getPokemonFullName(n)))
			.count();
		if (miss>0){
			String m = "Some pokemon rules are not defined";
			logger.error("[SimplePokebankStrategy] {}", m);
			throw new RuntimeException(m);
		}
	}

	private KeepPolicy parseKeepPolicy(HierarchicalConfiguration<ImmutableNode> config, KeepPolicy defaultValue){
		KeepPolicy keep = new KeepPolicy();

		String minCp = (String) config.getProperty("[@minCp]");
		if (minCp==null) {
			Validate.notNull(defaultValue, "need default value");
			keep.minCp = new Double(defaultValue.minCp);
			keep.minCpUnit = defaultValue.minCpUnit;
		} else if (minCp.matches("[0-9]+([\\.][0-9]+){0,1}[%]")) {
			keep.minCp = Double.parseDouble(minCp.substring(0, minCp.length()-1));
			keep.minCpUnit = DataUnit.PERCENTAGE;
		} else if (minCp.matches("[0-9]+([\\.][0-9]+){0,1}")) {
			keep.minCp = Double.parseDouble(minCp.substring(0, minCp.length()));
			keep.minCpUnit = DataUnit.NUMBER;
		} else {
			String m = "invalide value of minCp:"+minCp;
			logger.error("[SimplePokebankStrategy] {}", m);
			throw new RuntimeException(m);
		}

		if (config.getProperty("[@skill]") == null) {
			Validate.notNull(defaultValue, "need default value");
			keep.skill = defaultValue.skill;
		} else {
			keep.skill = KeepPolicy.Skill.valueOf((String) config.getProperty("[@skill]"));
		}

		if (config.getProperty("[@orderBy]") == null) {
			Validate.notNull(defaultValue, "need default value");
			keep.orderBy = defaultValue.orderBy;
		} else {
			keep.orderBy = KeepPolicy.OrderBy.valueOf((String) config.getProperty("[@orderBy]"));
		}

		if (config.getProperty("[@maxNumber]") == null) {
			Validate.notNull(defaultValue, "need default value");
			keep.maxNumber = defaultValue.maxNumber;
		} else {
			keep.maxNumber = Integer.parseInt((String) config.getProperty("[@maxNumber]"));
		}

		return keep;
	}

	private EnvolvePolicy parseEnvolvePolicy(HierarchicalConfiguration<ImmutableNode> config, EnvolvePolicy defaultValue){
		EnvolvePolicy envolve = new EnvolvePolicy();

		if (config.getProperty("[@condition]") == null) {
			Validate.notNull(defaultValue, "need default value");
			envolve.condition = defaultValue.condition;
		} else {
			envolve.condition = EnvolvePolicy.Condition.valueOf((String) config.getProperty("[@condition]"));
		}

		if (envolve.condition == EnvolvePolicy.Condition.NEVER) {
			return envolve;
		}

		String minCp = (String) config.getProperty("[@minCp]");
		if (minCp==null) {
			Validate.notNull(defaultValue, "need default value");
			envolve.minCp = new Double(defaultValue.minCp);
			envolve.minCpUnit = defaultValue.minCpUnit;
		} else if (minCp.matches("[0-9]+([\\.][0-9]+){0,1}[%]")) {
			envolve.minCp = Double.parseDouble(minCp.substring(0, minCp.length()-1));
			envolve.minCpUnit = DataUnit.PERCENTAGE;
		} else if (minCp.matches("[0-9]+([\\.][0-9]+){0,1}")) {
			envolve.minCp = Double.parseDouble(minCp.substring(0, minCp.length()));
			envolve.minCpUnit = DataUnit.NUMBER;
		} else {
			String m = "invalide value of minCp:"+minCp;
			logger.error("[SimplePokebankStrategy] {}", m);
			throw new RuntimeException(m);
		}

		String minIv = (String) config.getProperty("[@minIv]");
		if (minIv==null) {
			Validate.notNull(defaultValue, "need default value");
			envolve.minIv = new Double(defaultValue.minIv);
			envolve.minIvUnit = defaultValue.minIvUnit;
		} else if (minIv.matches("[0-9]+([\\.][0-9]+){0,1}[%]")) {
			envolve.minIv = Double.parseDouble(minIv.substring(0, minIv.length()-1));
			envolve.minIvUnit = DataUnit.PERCENTAGE;
		} else {
			String m = "invalide value of minIv:"+minCp;
			logger.error("[SimplePokebankStrategy] {}", m);
			throw new RuntimeException(m);
		}



		return envolve;
	}

	private enum DataUnit{
		NUMBER,
		PERCENTAGE
	}

	private static class KeepPolicy{

		@Override
		public String toString() {
			String s = "{";

			switch(minCpUnit){
			case NUMBER:
				s += "CP:" + minCp + " ";
				break;
			case PERCENTAGE:
				s += "CP:" + minCp + "% ";
				break;
			default:
				logger.warn("unknown unit {}", minCpUnit);
			}

			s += "skill:" + skill + " ";
			s += "order:" + orderBy + " ";
			s += "max:" + maxNumber + " ";

			s += "}";

			return s;
		}
		public enum Skill{
			NO_MATCH,
			SKILL_1_MATCH,
			SKILL_2_MATCH,
			BOTH_MATCH
		}

		public enum OrderBy{
			CP,
			IV
		}

		public Double minCp;
		public DataUnit minCpUnit;
		public Skill skill;
		public OrderBy orderBy;
		public Integer maxNumber;


	}

	private static class EnvolvePolicy{

		@Override
		public String toString() {

			String cp = null;
			if (minCpUnit != null) {
				switch(minCpUnit){
				case NUMBER:
					cp = "CP:" + minCp + " ";
					break;
				case PERCENTAGE:
					cp = "CP:" + minCp + "% ";
					break;
				default:
					logger.warn("unknown unit {}", minCpUnit);
				}
			}

			String iv = null;
			if (minIvUnit != null) {
				switch(minIvUnit){
				case NUMBER:
					iv = "IV" + minIv + " ";
					break;
				case PERCENTAGE:
					iv = "IV:" + minIv + "% ";
					break;
				default:
					logger.warn("unknown unit {}", minIvUnit);
				}
			}

			String s = "{";

			s += "condition:" + condition + " ";

			switch(condition){
			case ENOUGH_CANDY:
			case IV_MORE_THEN_TARGET:
				s += "CP:" + cp + " ";
				s += "IV:" + iv + " ";
				break;
			case NEVER:
				break;
			default:
				logger.warn("[SimplePokebankStrategy] unknown condition {}", condition);
				break;

			}

			s += "}";

			return s;
		}
		public enum Condition{
			NEVER,
			ENOUGH_CANDY,
			IV_MORE_THEN_TARGET
		}

		public Double minCp;
		public DataUnit minCpUnit;
		public Double minIv;
		public DataUnit minIvUnit;
		public Condition condition;
	}

	private static class PokemonRule{
		@Override
		public String toString() {
			String s = "PokemonRule " + Utils.getPokemonFullName(number) + " keep:" + keepPolicy.toString() + " envolve:" + envolvePolicy;

			if (!envolveTarget.isEmpty()){
				s += " target:" + envolveTarget.stream().map(n->Utils.getPokemonFullName(n)).collect(Collectors.joining(","));
			}

			return s;
		}
		public KeepPolicy keepPolicy;
		public EnvolvePolicy envolvePolicy;
		public Integer number;
		public List<Integer> envolveTarget;
	}

	@PostConstruct
	public void init(){

	}

	@Override
	public List<Pokemon> getToBeEnvolvePokemons() {
		List<Pokemon> pokemons = Utils.getPokeBank(go.get()).getPokemons();

		return pokemons
			.stream()
			.filter(mon->rules.get(mon.getMeta().getNumber()).envolvePolicy.condition != EnvolvePolicy.Condition.NEVER)
			.filter(mon->{
				PokemonRule rule = rules.get(mon.getMeta().getNumber());
				switch(rule.envolvePolicy.minCpUnit){
				case NUMBER:
					return mon.getCp() >= rule.envolvePolicy.minCp;
				case PERCENTAGE:
					try {
						return ((double)mon.getCp() * 100 / mon.getAbsoluteMaxCp()) >= rule.envolvePolicy.minCp;
					} catch (Exception e) {
						logger.warn("failed to get max CP");
						return false;
					}
				default:
					logger.warn("[SimplePokebankStrategy] unknown unit {}", rule.envolvePolicy.minCpUnit);
					return false;
				}
			})
			.filter(mon->{
				PokemonRule rule = rules.get(mon.getMeta().getNumber());
				switch(rule.envolvePolicy.minIvUnit){
				case PERCENTAGE:
					return (mon.getIvRatio() * 100) >= rule.envolvePolicy.minIv;
				default:
					logger.warn("[SimplePokebankStrategy] unknown unit {}", rule.envolvePolicy.minCpUnit);
					return false;
				}
			})
			.filter(mon->Utils.getCandy(mon) >= mon.getCandiesToEvolve())
			.filter(mon->{
				PokemonRule rule = rules.get(mon.getMeta().getNumber());

				if (rule.envolvePolicy.condition!=EnvolvePolicy.Condition.IV_MORE_THEN_TARGET) {
					return true;
				}

				if (rule.envolveTarget.isEmpty()){
					String m = "envolve target is not definded for " + Utils.getPokemonFullName(mon);
					logger.error("[SimplePokebankStrategy] {}", m);
					throw new RuntimeException(m);
				}

				return 0 < rule.envolveTarget
					.stream()
					.filter(i->{
						PokemonRule r = rules.get(i);

						long count = pokemons
							.stream()
							.filter(m->m.getMeta().getNumber()==i)
							.map(m->m.getIvRatio())
							.filter(iv->mon.getIvRatio()<iv)
							.count()
							;
						return r.keepPolicy.maxNumber > count;
					})
					.count();
			}).collect(Collectors.toList())
			;
	}

	@Override
	public List<Pokemon> getToBeTransferedPokemons() {
		List<Pokemon> pokemons = Utils.getPokeBank(go.get()).getPokemons();

		Set<Long> transferIds = new TreeSet<>();

		// get pokemons below minimum CP
		pokemons
			.stream()
			.filter(mon->{
				PokemonRule rule = rules.get(mon.getMeta().getNumber());
				switch(rule.keepPolicy.minCpUnit){
				case NUMBER:
					if (mon.getCp() < rule.keepPolicy.minCp){
						logger.debug("[SimplePokebankStrategy] {}'s CP is {} below {}", Utils.getPokemonFullName(mon), mon.getCp(), rule.keepPolicy.minCp);
						return true;
					} else {
						return false;
					}
				case PERCENTAGE:
					try {
						if (((double)mon.getCp() * 100 / mon.getAbsoluteMaxCp()) < rule.keepPolicy.minCp) {
							logger.debug("[SimplePokebankStrategy] {}'s CP is {}({}%) below {}%",
									Utils.getPokemonFullName(mon),
									mon.getCp(),
									((double)mon.getCp() * 100 / mon.getAbsoluteMaxCp()),
									rule.keepPolicy.minCp);
							return true;
						} else {
							return false;
						}
					} catch (Exception e) {
						logger.warn("failed to get max CP");
						return false;
					}
				default:
					logger.warn("[SimplePokebankStrategy] unknown unit {}", rule.keepPolicy.minCpUnit);
					return false;
				}
			})
			.forEach(mon->transferIds.add(mon.getId()));

		// get pokemons skill not match
		pokemons
			.stream()
			.filter(mon->{
				PokemonRule rule = rules.get(mon.getMeta().getNumber());

				boolean skill_1 = PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType() == mon.getMeta().getType1() ||
						PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType() == mon.getMeta().getType2();
				boolean skill_2 = PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType() == mon.getMeta().getType1() ||
						PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType() == mon.getMeta().getType2();

				switch (rule.keepPolicy.skill){
				case BOTH_MATCH:
					if (!(skill_1 && skill_2)) {
						logger.debug("[SimplePokebankStrategy] {}'s skill 1 {} or skill 2 {} are not match",
								Utils.getPokemonFullName(mon),
								PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType(),
								PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType());
						return true;
					} else {
						return false;
					}
				case NO_MATCH:
					return false;
				case SKILL_1_MATCH:
					if (!skill_1) {
						logger.debug("[SimplePokebankStrategy] {}'s skill 1 {} is not match", Utils.getPokemonFullName(mon), PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType());
						return true;
					} else {
						return false;
					}
				case SKILL_2_MATCH:
					if (!skill_2) {
						logger.debug("[SimplePokebankStrategy] {}'s skill 2 {} is not match", Utils.getPokemonFullName(mon), PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType());
						return true;
					} else {
						return false;
					}
				default:
					logger.warn("[SimplePokebankStrategy] unknown skill {}", rule.keepPolicy.skill);
					return false;
				}
			})
			.forEach(mon->transferIds.add(mon.getId()));

		// get pokemons exceed max number
		rules.keySet()
			.stream()
			.map(i->pokemons
					.stream()
					.filter(mon->mon.getMeta().getNumber()==i)
					.filter(mon->!transferIds.contains(mon.getId()))
					.sorted((a,b)->Double.compare(b.getIvRatio(), a.getIvRatio()))
					.collect(Collectors.toList()))
			.filter(list->!list.isEmpty())
			.filter(list->{
				PokemonRule rule = rules.get(list.get(0).getMeta().getNumber());
				return list.size() > rule.keepPolicy.maxNumber;
			})
			.map(list->{
				PokemonRule rule = rules.get(list.get(0).getMeta().getNumber());

				if (logger.isDebugEnabled()){
					list.subList(0, rule.keepPolicy.maxNumber)
						.stream()
						.forEach(mon->logger.debug("[SimplePokebankStrategy] keep   {} CP:{} IV:{}", Utils.getPokemonFullName(mon), mon.getCp(), mon.getIvRatio()));
					list.subList(rule.keepPolicy.maxNumber, list.size())
						.stream()
						.forEach(mon->logger.debug("[SimplePokebankStrategy] remove {} CP:{} IV:{}", Utils.getPokemonFullName(mon), mon.getCp(), mon.getIvRatio()));
				}
				return list.subList(rule.keepPolicy.maxNumber, list.size());
			})
			.forEach(list->list.forEach(mon->transferIds.add(mon.getId())));

		return pokemons
				.stream()
				.filter(mon->transferIds.contains(mon.getId()))
				.collect(Collectors.toList());
	}

//	@Override
//	public List<Pokemon> getToBeTransferedPokemons() {
//		List<Pokemon> notTransfer = Utils.getPokeBank(go.get())
//			.getPokemons()
//			.stream()
//			.filter(mon->!mon.isFavorite())
//			.filter(mon->{
//				int max;
//				try {
//					max = mon.getAbsoluteMaxCp();
//				} catch (Exception e) {
//					logger.error("[SimplePokebankStrategy] no such pokemon "+Utils.getPokemonFullName(mon), e);
//					return false;
//				}
//				return ((double)mon.getCp() * 100 / max) > rules.get(mon.getMeta().getNumber()).keepMinCpPercent;
//			})
//			.filter(mon->{
//				if (!rules.get(mon.getMeta().getNumber()).keepOnlyIfSameSkillType){
//					return true;
//				}
//
//				if (
//					(PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType() == mon.getMeta().getType1() ||
//					PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType() == mon.getMeta().getType2()) &&
//
//					(PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType() == mon.getMeta().getType1() ||
//					PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType() == mon.getMeta().getType2())) {
//					return true;
//				}
//
//				return false;
//			})
//			.peek(mon->{
//				logger.debug("[SimplePokebankStrategy] not transfer {} CP:{} move1:{}({}) move2:{}({})",
//						Utils.getPokemonFullName(mon), mon.getCp(),
//						mon.getMove1(), PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType(),
//						mon.getMove2(), PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType());
//			})
//			.collect(Collectors.toList());
//
//		List<Pokemon> envolve = getToBeEnvolvePokemons(false);
//
//		List<Pokemon> transfer = Utils.getPokeBank(go.get())
//			.getPokemons()
//			.stream()
//			.filter(mon->{
//				return !notTransfer.contains(mon);
//			})
//			.filter(mon->{
//				return !envolve.contains(mon);
//			})
//			.peek(mon->{
//				logger.debug("[SimplePokebankStrategy] transfer {} CP:{} move1:{}({}) move2:{}({})",
//						Utils.getPokemonFullName(mon), mon.getCp(),
//						mon.getMove1(), PokemonMoveMetaRegistry.getMeta(mon.getMove1()).getType(),
//						mon.getMove2(), PokemonMoveMetaRegistry.getMeta(mon.getMove2()).getType());
//			})
//			.collect(Collectors.toList());
//
//		return transfer;
//	}

	@Override
	public CatchFlavor getCatchFlavor(PokemonData mon) {
		int max;

		try {
			max = PokemonDetails.getAbsoluteMaxCp(mon.getPokemonId());
		} catch (Exception e) {
			logger.error("[SimplePokebankStrategy] no such pokemon "+Utils.getPokemonFullName(mon.getPokemonId().getNumber()), e);
			return CatchFlavor.HOPE_TO_CATCH;
		}

		switch (rules.get(mon.getPokemonId().getNumber()).keepPolicy.minCpUnit) {
		case NUMBER:
			break;
		case PERCENTAGE:
			return ((double)mon.getCp() * 100 / max) > rules.get(mon.getPokemonId().getNumber()).keepPolicy.minCp
					? CatchFlavor.HOPE_TO_CATCH : CatchFlavor.NO_DESIRE;
		default:
			break;
		}

		return mon.getCp() > rules.get(mon.getPokemonId().getNumber()).keepPolicy.minCp
				? CatchFlavor.HOPE_TO_CATCH : CatchFlavor.NO_DESIRE;
	}

}
