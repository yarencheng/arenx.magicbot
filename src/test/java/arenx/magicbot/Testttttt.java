package arenx.magicbot;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

public class Testttttt {

	public static void main(String[] argv) throws LoginFailedException, RemoteServerException, InterruptedException {

		List<Integer> list = Arrays.asList(1,2,3,4);

		combinations(list, 2)
			.forEach(a->{

				System.out.println("a="+a);

			});

	}

	public static <E> Stream<List<E>> combinations(List<E> l, int size) {
	    if (size == 0) {
	        return Stream.of(Collections.emptyList());
	    } else {
	        return IntStream.range(0, l.size()).boxed().
	            <List<E>> flatMap(i -> combinations(l.subList(i+1, l.size()), size - 1).map(t -> pipe(l.get(i), t)));
	    }
	}

	private static <E> List<E> pipe(E head, List<E> tail) {
	    List<E> newList = new ArrayList<>(tail);
	    newList.add(0, head);
	    return newList;
	}

}
