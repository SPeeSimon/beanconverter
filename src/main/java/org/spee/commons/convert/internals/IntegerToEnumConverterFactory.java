package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;

public class IntegerToEnumConverterFactory implements InternalConverter {

	
	@Override
	public boolean canMap(Class<?> sourceType, Class<?> targetType) {
		return (Number.class.isAssignableFrom(sourceType) || sourceType.isPrimitive()) && Enum.class.isAssignableFrom(targetType);
	}

	@Override
	public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
		try {
			return MethodHandles.insertArguments(
					MethodHandles.lookup().findStatic(Array.class, "get", MethodType.methodType(Object.class, Object.class, int.class))
					, 0, (Object)targetType.getEnumConstants());
		} catch (NoSuchMethodException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	public Object getEnumValue(Class<? super Enum<?>> targetType, int value){
		return targetType.getEnumConstants()[value];
	}
	
}