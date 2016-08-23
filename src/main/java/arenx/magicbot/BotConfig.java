package arenx.magicbot;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.pokegoapi.api.PokemonGo;

@Configuration
@ComponentScan(basePackages={"arenx.magicbot"})
public class BotConfig {

	private static Logger logger = LoggerFactory.getLogger(BotConfig.class);

	@Bean
	public HierarchicalConfiguration<ImmutableNode> getXMLConfiguration(){
		try {
			return new Configurations().xml("config.xml");
		} catch (ConfigurationException e) {
			String m = "Failed to parse config file";
			logger.error("[BotConfig] " + m, e);
			throw new RuntimeException(m, e);
		}

	}

	@Bean
	public AtomicReference<PokemonGo> getPokemonGo(){
		return new AtomicReference<>();
	}

	@Bean
	public MoveStrategy getMoveStrategy(){
		return new ShortestLurePathMoveStrategy();
	}

	@Bean
	public BackbagStrategy getBackbagStrategy(){
		return new SimpleBackbagStrategy();
	}

	@Bean
	public InformationStrategy getInformationStrategy(){
		return new SimpleInformationStrategy();
	}

	@Bean
	public PokebankStrategy getPokebankStrategy(){
		return new SimplePokebankStrategy();
	}

	@Bean
	@Qualifier("lootedPokestopCount")
	public AtomicLong getLootedPokestopCount(){
		return new AtomicLong(0);
	}

	@Bean
	@Qualifier("catchedPokemonCount")
	public AtomicLong getCatchedPokemonCount(){
		return new AtomicLong(0);
	}



}
