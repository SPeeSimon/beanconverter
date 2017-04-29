package org.spee.commons.utils;

public class ObjectUtils {

	
	public static boolean isNull(Object o){
		return o == null;
	}
	
	public static String toString(Object nullableValue, String nullMessage, String notNullMessage){
		return nullableValue == null ? nullMessage : notNullMessage;
	}
	
	
}
