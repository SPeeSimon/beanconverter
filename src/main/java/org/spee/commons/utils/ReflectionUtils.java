package org.spee.commons.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import com.google.common.base.Optional;
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
	 * Predicate that checks if a given type is available.
	 * @param type The class name
	 * @return {@link Optional} indicating the type is present or absent.
	 */
	public static Optional<Class> isTypePresent(final String type){
		try {
			Class class1 = Class.forName(type);
			return Optional.of(class1);
		} catch (ClassNotFoundException e) {
			return Optional.absent();
		}
	}
	
	
	/**
	 * Retrieve the method from the methodHolder.
	 * @param methodHolder The class which you want to look the method
	 * @param methodName The name of the method
	 * @param parameterTypes The parameter types of the method
	 * @return {@link java.util.Optional} with the method present or absent in case of an exception.
	 * @see Class#getMethod(String, Class...)
	 */
	public static Optional<Method> getMethod(Class<?> methodHolder, String methodName, Class<?>... parameterTypes){
		try {
			Method method = methodHolder.getMethod(methodName, parameterTypes);
			return Optional.of( method );
		} catch (NoSuchMethodException | SecurityException e) {
			return Optional.absent();
		}
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
