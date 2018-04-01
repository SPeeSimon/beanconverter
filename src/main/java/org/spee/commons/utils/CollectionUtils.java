package org.spee.commons.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.spee.commons.convert.Convert;
import org.spee.commons.convert.Converter;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class CollectionUtils {

	
	public static <T> void applyToAll(final Iterable<T> collection, final Consumer<T> action){
		if( collection != null ){
			for (T entry : collection) {
				action.accept(entry);
			}
		}
	}
	
	
	public static <F,T> void transformInto(final Iterable<F> from, final Collection<T> to, final Convert<F, T> transformer){
		Preconditions.checkArgument(transformer != null, "transformer is null");
		transformInto(from, to, new Function<F,T>() {
			@Override
			public T apply(F input) {
				return transformer.convert(input);
			}
		});
	}


	public static <F,T> void transformInto(final Iterable<F> from, final Collection<T> to, final Function<F, T> transformer){
		Preconditions.checkArgument(transformer != null, "transformer is null");
		if( from != null && to != null ){
			for (F f : from) {
				to.add( transformer.apply(f) );
			}
		}
	}
	
	
	@Converter
	public static <T> List<T> toList(final Collection<T> from){
		if( from instanceof List ){
			return (List<T>) from;
		}
		return new ArrayList<>(from);
	}
	
	
	/**
	 * Return a filtered {@link Iterable}.
	 * @param unfiltered can be <code>null</code>
	 * @param filter 
	 * @return The items only matching the filter
	 * @see Iterables#filter(Iterable, Predicate)
	 */
	public static <T> Iterable<T> filter(final Iterable<T> unfiltered, Predicate<T> filter){
		if( unfiltered == null ){
			return Collections.emptyList();
		}
		return Iterables.filter(unfiltered, filter);
	}
	
}
