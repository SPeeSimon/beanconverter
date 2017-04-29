package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class NoAvailableConverter implements InternalConverter {

	static final MethodHandle toExceptionMethodHandle = getConverterNotFoundMethodHandle();
	
	private static MethodHandle getConverterNotFoundMethodHandle() {
		try {
			return MethodHandles.lookup().findStatic(NoAvailableConverter.class, "converterNotFound", MethodType.methodType(Void.TYPE, Class.class, Object.class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	
	@Override
	public boolean canMap(Class<?> sourceType, Class<?> targetType) {
		return false;
	}

	@Override
	public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
		return MethodHandles.insertArguments(toExceptionMethodHandle, 0, targetType);
	}

	
	public static void converterNotFound(Class<?> returnType, Object parameter){
		throw new IllegalArgumentException("converter not found for " + parameter.getClass() + " to " + returnType);
	}
}
