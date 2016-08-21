package arenx.magicbot;


import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

public class Testttttt {

	public static void main(String[] argv) throws LoginFailedException, RemoteServerException, InterruptedException {

		Thread t = new Thread(){
			@Override
			public void run(){
				throw new RuntimeException("ddddddddd");
			}
		};

		t.setUncaughtExceptionHandler((a,b)->{
			System.out.println("a "+Thread.currentThread().getName());
		});

		t.start();

		System.out.println("b "+Thread.currentThread().getName());
	}

}
