package org.spee.commons.utils;

import static org.spee.commons.utils.ReflectionUtils.isAnnotationPresentOnMethod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

public class SortUtils {
	private static final Ordering<Object> NULLSAFEORDER = Ordering.from(TypeCompare.INSTANCE).nullsFirst();

	@SuppressWarnings("rawtypes")
	private static final ClassValue<Map<String,Ordering>> CLASS_FIELDORDERINGS = new ClassValue<Map<String,Ordering>>() {
		@Override
		protected Map<String, Ordering> computeValue(final Class<?> type) {
			final Predicate<Method> methodLimiter = type.isAnnotationPresent(SortColumn.class) ? Predicates.<Method>alwaysTrue() : isAnnotationPresentOnMethod(SortColumn.class);
			final Map<String, Ordering> found = new HashMap<>();

			// look for methods
			for(Class<?> st = type; st != null && !Object.class.equals(st); st = st.getSuperclass()){
				final Lookup lookup = MethodHandles.publicLookup().in(st);
				for (Method method : st.getDeclaredMethods()) {
					if( methodLimiter.apply(method) && canUseMethod(method) ){
						try {
							found.put(getName(method), new MethodHandleComparator(lookup.unreflect(method)).nullsFirst());
						} catch (IllegalAccessException e) {
							LoggerFactory.getLogger(SortUtils.class).warn("Could not sort with {}", method, e);
						}
					}
				}
			}

			return found;
		}
		
		/**
		 * Check if the given {@code Method} is a proper getter.
		 * @param method
		 * @return <code>true</code> if the method is a proper getter method
		 */
		private boolean canUseMethod(Method method){
			return method.getParameterCount() == 0 && Void.TYPE != method.getReturnType()
					&& Modifier.isPublic(method.getModifiers())
					&& !"getClass".equals(method.getName())
					&& !"hashCode".equals(method.getName())
					&& !"toString".equals(method.getName());
		}
		
		private String getName(Method method){
			SortColumn annotation = method.getAnnotation(SortColumn.class);
			if( annotation != null && annotation.value() != null && annotation.value().trim().length() > 0 ){
				return annotation.value();
			}
			return method.getName();
		}
		
		
		class MethodHandleComparator<T> extends Ordering<T> {

			private final MethodHandle methodHandle;

			public MethodHandleComparator(MethodHandle methodHandle) {
				this.methodHandle = methodHandle;
			}

			@Override
			public int compare(T o1, T o2) {
				try {
					Object oo1 = methodHandle.invokeWithArguments(o1);
					Object oo2 = methodHandle.invokeWithArguments(o2);
					return NULLSAFEORDER.compare(oo1, oo2);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Exception e) {
					LoggerFactory.getLogger(MethodHandleComparator.class).warn("Error comparing fields", e);
				} catch (Throwable e) {
					if ( e instanceof RuntimeException || e instanceof Error ){
						throw (RuntimeException)e;
					}
					throw new RuntimeException(e);
				}
				return 0;
			}
		}
	};
	
	private enum TypeCompare implements Comparator<Object> {
		INSTANCE;
		
		private final Ordering comparableOrder = Ordering.natural();
		
		@Override
		public int compare(Object o1, Object o2) {
			if( Comparable.class.isAssignableFrom(o1.getClass()) ){
				return comparableOrder.compare(o1, o2);
			}
			return 0;
		};
	}
	
	
	public static Collection<String> getSortMethods(Class<?> type){
		return CLASS_FIELDORDERINGS.get(type).keySet();
	}
	
	
	public static <T> Optional<Ordering<T>> getSort(Class<T> type, String name){
		return Optional.ofNullable( (Ordering<T>)CLASS_FIELDORDERINGS.get(type).get(name) );
	}
	
	
	public static <T> void sort(String name, List<T> values){
		if( values == null || values.isEmpty() ) return;
		
		Ordering comparator = CLASS_FIELDORDERINGS.get(Iterables.get(values, 0).getClass()).get(name);
		Collections.sort(values, comparator);
	}
	
}
