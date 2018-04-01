package org.spee.commons.utils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class MapUtils {

	public static <K> boolean hasKey(final Map<K, ?> map, final K key) {
		return map == null ? false : map.containsKey(key);
	}

	
	public static <K> boolean hasKey(final Map<K, ?> map, final Supplier<K> key) {
		return key != null && hasKey(map, key.get());
	}

	
	public static <K, V> V putIfAbsent(final Map<K, V> map, final K key, final V value) {
		V v = map.get(key);
		if (v == null){
			map.put(key, value);
			v = map.get(key);
		}
		return v;
	}
	

	public static <K,V,T> List<T> toList(final Map<K, V> map, final Function<Map.Entry<K, V>, T> transform){
		return Lists.newArrayList( Iterables.transform(map.entrySet(), transform) );
	}
	
	
	public static <K,V> Optional<V> findValue(final Map<K,V> map, final Predicate<? super K> keyFinder){
		for (Entry<K, V> entry : map.entrySet()) {
			if( keyFinder.apply(entry.getKey()) ){
				return Optional.of(entry.getValue());
			}
		}
		return Optional.absent();
	}
	
	
	public static <A,B,C,D> void transformInto(Map<A,B> from, Map<C,D> to, Function<Map.Entry<A, B>, Map.Entry<C, D>> transformer){
		if( from != null && to != null && transformer != null ){ // TODO remove
			for (Entry<A, B> entry : from.entrySet()) {
				Entry<C, D> transformed = transformer.apply(entry);
				to.put(transformed.getKey(), transformed.getValue());
			}
		}
	}

	
	public static <A,B,C,D> void transformInto(Map<A,B> from, Map<C,D> to, Function<A,C> keyTransformer, Function<B,D> valueTransformer){
		if( from != null && to != null && keyTransformer != null && valueTransformer != null ){ // TODO remove
			for (Entry<A, B> entry : from.entrySet()) {
				to.put(keyTransformer.apply(entry.getKey()), valueTransformer.apply(entry.getValue()));
			}
		}
	}
	
	
	public static <A,B,C,D> void transformInto(Map<A,B> from, Collection<C> to, Function<Map.Entry<A, B>, C> entryTransformer){
		if( from != null && to != null && entryTransformer != null ){
			for (Entry<A, B> entry : from.entrySet()) {
				to.add(entryTransformer.apply(entry));
			}
		}
	}
	
	
	public static <A,B,C,D> void transformInto(Collection<A> from, Map<B,C> to, Function<A,B> keyTransformer, Function<A,C> valueTransformer){
		if( from != null && to != null && keyTransformer != null && valueTransformer != null ){
			for (A entry : from) {
				to.put(keyTransformer.apply(entry), valueTransformer.apply(entry));
			}
		}
	}


	public static <K,V> V get(Map<K, V> map, K key) {
		if( map == null ) return null;
		return map.get(key);
	}
	
}
