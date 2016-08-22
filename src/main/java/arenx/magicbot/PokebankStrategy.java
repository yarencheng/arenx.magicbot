package arenx.magicbot;

import java.util.List;

import com.pokegoapi.api.pokemon.Pokemon;

public interface PokebankStrategy {

	public List<Pokemon> getToBeEnvolvePokemons();
	public List<Pokemon> getToBeTransferedPokemons();
}
