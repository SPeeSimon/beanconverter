package org.spee.commons.utils;

import java.util.Arrays;

public class IterableUtils {

	
	public static <T> Iterable<T> of(Iterable<T> orig) {
		return orig;
	}
	
	public static <T> Iterable<T> of(T... orig){
		return Arrays.asList(orig);
	}
	
}
