package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class StringToEnumConverterFactory implements InternalConverter {

	@Override
	public boolean canMap(Class<?> sourceType, Class<?> targetType) {
		return String.class == sourceType && Enum.class.isAssignableFrom(targetType);
	}

	@Override
	public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
		//	Enum.valueOf(targetType, "value");
		try {
			return MethodHandles.insertArguments(
						MethodHandles.lookup().findStatic(Enum.class, "valueOf", MethodType.methodType(Enum.class, Class.class, String.class))
						, 0, targetType);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}
	
}