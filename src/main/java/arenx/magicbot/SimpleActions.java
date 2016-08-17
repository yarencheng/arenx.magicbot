package arenx.magicbot;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration2.XMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.pokegoapi.api.PokemonGo;

//@Component
public class SimpleActions implements Actions {

	private static Logger logger = LoggerFactory.getLogger(SimpleActions.class);

	@Autowired
	private XMLConfiguration config;

	@Autowired
	private AtomicReference<PokemonGo> go;

//	@Autowired
//	private LoginStrategy loginStrategy;

	@Override
	public void start() {
		logger.debug("[Action] init");

		while(true){
			login();





			break;
		}
	}

	@Override
	public void login() {
/*
		if(!loginStrategy.needLogin()){
			return;
		}

		logger.info("[Login] start...");

		Account account = loginStrategy.getAccount();

		logger.debug("[Login] username:{} password:{}", account.getUsername(), account.getPassword());

		InetAddress local;
		try {
			NetworkInterface net = NetworkInterface.getByName(config.getString("network.interface.name"));

			local = net.getInterfaceAddresses()
				.stream()
				.sorted((a,b)->Integer.compare(a.getAddress().getAddress().length, b.getAddress().getAddress().length))
				.findFirst()
				.get()
				.getAddress();

		} catch (SocketException e1) {
			logger.error(e1.getMessage(), e1);
			throw new RuntimeException(e1);
		}

		logger.info("[Login] username:{} IP:{}",account.getUsername(), local.getHostAddress());
		logger.debug("[Login] local address is {}", local);

		OkHttpClient httpClient = new OkHttpClient()
				.newBuilder()
				.socketFactory(new SocketFactory() {

					private SocketFactory sf = SocketFactory.getDefault();

					@Override
					public Socket createSocket() throws IOException{
						Socket s = sf.createSocket();
						s.bind(new InetSocketAddress(local, 0));
						return s;
					}

					@Override
					public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
							throws IOException {
						if (!localAddress.equals(local)) {
							String m = "Forbid to assign local address[" + localAddress +"]";
							logger.error("[Login] {}", m);
							throw new RuntimeException(m);
						}
						return sf.createSocket(address, port, localAddress, localPort);
					}

					@Override
					public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
							throws IOException, UnknownHostException {
						if (!localHost.equals(local)) {
							String m = "Forbid to assign local address[" + localHost +"]";
							logger.error("[Login] {}", m);
							throw new RuntimeException(m);
						}
						return sf.createSocket(host, port, localHost, localPort);
					}

					@Override
					public Socket createSocket(InetAddress host, int port) throws IOException {
						return sf.createSocket(host, port, local, 0);
					}

					@Override
					public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
						return sf.createSocket(host, port, local, 0);
					}
				})
				.build();


		try {

			CredentialProvider cp = new PtcCredentialProvider(httpClient, account.getUsername(), account.getPassword());
			PokemonGo go = new PokemonGo(cp, httpClient);

			logger.info("[Login] success");

			this.go.set(go);

		} catch (LoginFailedException | RemoteServerException e) {
			logger.error("[Login] Failed to loging", e);
			throw new RuntimeException(e);
		}
*/
	}

	@Override
	public void move(double latitude, double longitude, double altitude) {
		// TODO Auto-generated method stub

	}

}
