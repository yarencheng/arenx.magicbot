package arenx.magicbot;

import java.util.List;

import com.pokegoapi.api.pokemon.Pokemon;

import POGOProtos.Data.PokemonDataOuterClass.PokemonData;

public interface PokebankStrategy {

	public List<Pokemon> getToBeEnvolvePokemons();
	public List<Pokemon> getToBeTransferedPokemons();

	public enum CatchFlavor{
		NO_DESIRE,
		HOPE_TO_CATCH
	}

	public CatchFlavor getCatchFlavor(PokemonData  mon);
}
