package org.spee.commons.utils;

import java.util.Collection;
import java.util.Random;

import com.google.common.collect.Iterables;

public class RandomUtils {

	private RandomUtils() {
	}
	
	public static <E extends Enum<E>> E random(Class<E> enumClass){
		return random(enumClass.getEnumConstants());
	}
	
	public static <E> E random(E... array){
		if( array == null || array.length == 0 )
			return null;
		
		Random r = new Random();
		return array[r.nextInt(array.length)];
	}

	public static <E> E random(Collection<E> list) {
		int getIndex = new Random().nextInt(list.size());
		return Iterables.get(list, getIndex);
	}
	
}
