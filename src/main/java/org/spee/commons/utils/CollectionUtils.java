package org.spee.commons.utils;

import java.util.Collection;
import java.util.function.Consumer;

import com.google.common.base.Function;

public class CollectionUtils {

	
	public static <T> void applyToAll(Iterable<T> collection, Consumer<T> action){
		if( collection != null ){
			for (T entry : collection) {
				action.accept(entry);
			}
		}
	}
	
	
	public static <F,T> void transformInto(Iterable<F> from, Collection<T> to, Function<F, T> transformer){
		if( from != null && to != null && transformer != null ){
			for (F f : from) {
				to.add( transformer.apply(f) );
			}
		}
	}
	
}
