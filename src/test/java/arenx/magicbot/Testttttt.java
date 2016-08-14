package arenx.magicbot;


import java.util.Scanner;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.player.PlayerProfile;
import com.pokegoapi.auth.GoogleUserCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import POGOProtos.Data.PlayerDataOuterClass.PlayerData;
import okhttp3.OkHttpClient;

public class Testttttt {

	public static void main(String[] argv) throws LoginFailedException, RemoteServerException {
		
		
		OkHttpClient httpClient = new OkHttpClient();

		/** 
		* Google: 
		* You will need to redirect your user to GoogleUserCredentialProvider.LOGIN_URL
		* Afer this, the user must signin on google and get the token that will be show to him.
		* This token will need to be put as argument to login.
		*/
				
		GoogleUserCredentialProvider provider = new GoogleUserCredentialProvider(httpClient);

		// in this url, you will get a code for the google account that is logged
		System.out.println("Please go to " + GoogleUserCredentialProvider.LOGIN_URL);
		System.out.println("Enter authorisation code:");

		// Ask the user to enter it in the standart input
		Scanner sc = new Scanner(System.in);
		String access = sc.nextLine();

		// we should be able to login with this token
		provider.login(access);
		PokemonGo go = new PokemonGo(provider, httpClient);
		
		
		PlayerProfile pf = go.getPlayerProfile();
		
		PlayerData pd = pf.getPlayerData();
		
		
		System.out.println("pd.getAvatar().toString()="+pd.getAvatar().toString());
		System.out.println("pd.getUsername()="+pd.getUsername());

//		/**
//		* After this, if you do not want to re-authorize the google account every time, 
//		* you will need to store the refresh_token that you can get the first time with provider.getRefreshToken()
//		* ! The API does not store the refresh token for you !
//		* log in using the refresh token like this :
//		*/
//		PokemonGo go = new PokemonGo(new GoogleUserCredentialProvider(httpClient, refreshToken), httpClient);

//		/**
//		* PTC is much simpler, but less secure.
//		* You will need the username and password for each user log in
//		* This account does not currently support a refresh_token. 
//		* Example log in :
//		*/
//		PokemonGo go = new PokemonGo(new PtcCredentialProvider(httpClient,username,password),httpClient);
//
//		// After this you can access the api from the PokemonGo instance :
//		go.getPlayerProfile(); // to get the user profile
//		go.getInventories(); // to get all his inventories (Pokemon, backpack, egg, incubator)
//		go.setLocation(lat, long, alt); // set your position to get stuff around (altitude is not needed, you can use 1 for example)
//		go.getMap().getCatchablePokemon(); // get all currently Catchable Pokemon around you
//
//		// If you want to go deeper, you can directly send your request with our RequestHandler
//		// For example, here we are sending a request to get the award for our level
//		// This applies to any method defined in the protos file as Request/Response)
//
//		LevelUpRewardsMessage msg = LevelUpRewardsMessage.newBuilder().setLevel(yourLVL).build(); 
//		ServerRequest serverRequest = new ServerRequest(RequestType.LEVEL_UP_REWARDS, msg);
//		go.getRequestHandler().sendServerRequests(serverRequest);
//
//		// and get the response like this :
//
//		LevelUpRewardsResponse response = null;
//		try {
//		    response = LevelUpRewardsResponse.parseFrom(serverRequest.getData());
//		} catch (InvalidProtocolBufferException e) {
//		    // its possible that the parsing fail when servers are in high load for example.
//		    throw new RemoteServerException(e);
//		}
		
		
	}
	
}
