package org.spee.commons.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;

public class ReflectionUtils {

	/**
	 * Check if the <code>checks</code> are the same class as <code>checkType</code>
	 * @param checkType The type to check against
	 * @param checks The classes to check
	 * @return <code>true</code> if all matches.
	 */
	public static boolean typeMatch(Class<?> checkType, Class<?>... checks){
		boolean allMatch = true;
		for(int i = 0; i < checks.length && allMatch; i++){
			allMatch &= (checkType == checks[i]);
		}
		return allMatch;
	}
	
	
	/**
	 * Predicate that checks if a given method has the annotation.
	 * @param annotation
	 * @return
	 */
	public static Predicate<Method> isAnnotationPresentOnMethod(final Class<? extends Annotation> annotation){
		return new Predicate<Method>() {
			@Override
			public boolean apply(Method input) {
				return input.isAnnotationPresent(annotation);
			}
		};
	}
	
	
	/**
	 * Predicate that checks if a given method has the amount of parameters.
	 * <pre>
	 * function singleParameter(String s){}
	 * 
	 * hasParameterCount(0) => false
	 * hasParameterCount(1) => true
	 * </pre>
	 * @param parameters
	 * @return
	 */
	public static Predicate<Method> hasParameterCount(final int parameters){
		return new Predicate<Method>() {
			@Override
			public boolean apply(Method input) {
				return input != null && input.getParameterCount() == parameters;
			}
		};
	}
	
	
	public static Predicate<Class<?>> isAssignableFrom(final Class<?> type){
		return new Predicate<Class<?>>() {
			@Override
			public boolean apply(Class<?> input) {
				return input != null && input.isAssignableFrom(type);
			}
		};
	}
	
	
	/**
	 * Create a {@link Supplier} that will return a newInstance() of the given type.
	 * @param type
	 * @return Supplier that calls {@link Class#newInstance()}
	 */
	public static <T> Supplier<T> newInstanceSupplier(final Class<T> type){
		return new Supplier<T>() {
			@Override
			public T get() {
				try {
					return (T)type.newInstance();
				} catch (InstantiationException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
	
}
