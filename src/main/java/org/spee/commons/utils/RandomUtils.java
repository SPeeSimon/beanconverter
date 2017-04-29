package org.spee.commons.utils;

import java.util.Random;

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
	
	
}
