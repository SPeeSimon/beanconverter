package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class FallbackObjectToStringConverter implements InternalConverter {

	@Override
	public boolean canMap(Class<?> sourceType, Class<?> targetType) {
		return String.class == targetType;
	}

	@Override
	public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
		try {
			return MethodHandles.lookup().findVirtual(sourceType, "toString", MethodType.methodType(String.class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
}