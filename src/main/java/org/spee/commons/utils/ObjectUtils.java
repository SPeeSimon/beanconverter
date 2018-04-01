package org.spee.commons.utils;

import java.lang.invoke.MethodHandle;

import com.google.common.base.Function;

public class ObjectUtils {

	public static String toString(Object nullableValue, String nullMessage, String notNullMessage){
		return nullableValue == null ? nullMessage : notNullMessage;
	}

	
	public static <F,T> Function<F, T> getValue(final MethodHandle mh){
		return new Function<F, T>() {
			@Override
			public T apply(F input) {
				try {
					return (T)mh.invokeExact(input);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
	
}
