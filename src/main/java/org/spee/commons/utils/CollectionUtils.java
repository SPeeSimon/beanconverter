package org.spee.commons.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.spee.commons.convert.Converter;

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
	
	
	@Converter
	public static <T> List<T> toList(Collection<T> from){
		if( from instanceof List ){
			return (List<T>) from;
		}
		return new ArrayList<>(from);
	}
	
	
}
