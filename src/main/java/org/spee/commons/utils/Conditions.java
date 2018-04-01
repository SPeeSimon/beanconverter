package org.spee.commons.utils;

import java.util.Collection;

public class Conditions {

	
	public static boolean has(Object value){
		return value != null;
	}
	
	public static boolean has(boolean value) {
		return value;
	}
	
	public static boolean has(Collection<?> list) {
		return list != null && !list.isEmpty();
	}
	
	public static boolean has(Iterable<?> list) {
		return list != null && list.iterator().hasNext();
	}
	
}
