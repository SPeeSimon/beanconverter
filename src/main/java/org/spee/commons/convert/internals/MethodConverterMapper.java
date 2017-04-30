package org.spee.commons.convert.internals;

import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.LoggerFactory;

/**
 * Converter that will call a method on the given type to convert.
 * The methods must match:
 * <pre>
 * public NewType asType(){} // like groovy supports
 * public NewType toNewType(){} // like Spring Framework supports
 * </pre>
 * 
 * example:
 * <pre>
 * class MyData {
 *   Date birthDay;
 *   
 *   public Date toDate(){ return birthDay; }
 * }
 * </pre>
 * 
 * @author shave
 *
 */
public class MethodConverterMapper implements InternalConverter {

	private static final String TO = "to";

	private static ClassValue<Map<Class<?>, MethodHandle>> classWithMethods = new ClassValue<Map<Class<?>, MethodHandle>>() {

		protected Map<Class<?>, MethodHandle> computeValue(final java.lang.Class<?> type) {
			Map<Class<?>, MethodHandle> foundMethods = new LinkedHashMap<>();
			final Lookup lookup = MethodHandles.lookup().in(type);
			
			for (Method method : type.getMethods()) {
				if( isPublic(method.getModifiers()) && !isStatic(method.getModifiers()) && !(Void.TYPE == method.getReturnType()) && method.getParameterCount()==0 ) {
					try {
						if (method.getName().startsWith("asType")) { // groovy like
							foundMethods.put(method.getReturnType(), lookup.unreflect(method));
						}
						else if( method.getName().startsWith(TO) && method.getName().equals(TO + method.getReturnType()) ){ // spring like
							foundMethods.put(method.getReturnType(), lookup.unreflect(method));
						}
					} catch (IllegalAccessException e) {
						LoggerFactory.getLogger(MethodConverterMapper.class).warn("Could not use conversion by method on class {}: {}", type, e);
					}
				}
			}
			
			for (Constructor<?> constructor : type.getConstructors()) { // copy constructors
				if( isPublic(constructor.getModifiers()) && constructor.getParameterCount() == 1 ){
					try {
						if( !foundMethods.containsKey(constructor.getParameterTypes()[0]) ){
							foundMethods.put(constructor.getParameterTypes()[0], lookup.unreflectConstructor(constructor));
						}
					} catch (IllegalAccessException e) {
						LoggerFactory.getLogger(MethodConverterMapper.class).warn("Could not use conversion by constructor on class {}: {}", type, e);
					}
				}
			}
			
			return foundMethods.isEmpty() ? Collections.<Class<?>, MethodHandle>emptyMap() : Collections.unmodifiableMap(foundMethods);
		}
	};
	
	
	@Override
	public boolean canMap(final Class<?> sourceType, final Class<?> targetType) {
		return String.class != sourceType && classWithMethods.get(sourceType).containsKey(targetType);
	}

	/**
	 * Look for a method in sourceType for a method "asType" with a return type of targetType.
	 * @param sourceType The class to look in
	 * @param returnType The return type of the method
	 * @return 
	 */
	@Override
	public MethodHandle getTypeConverter(final Class<?> sourceType, final Class<?> targetType) {
		return classWithMethods.get(sourceType).get(targetType);
	}

}
