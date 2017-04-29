package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

public class ObjectToStringConverter implements InternalConverter {

	private final static ClassValue<MethodHandle> toStringMethodHandles = new ClassValue<MethodHandle>() {
		
		@Override
		protected MethodHandle computeValue(Class<?> type) {
			try {
//				Objects.toString(o, null);
//				Objects.toString(object, "string");

				return MethodHandles.insertArguments(				
						MethodHandles.lookup().findStatic(Objects.class, "toString", MethodType.methodType(String.class, Object.class, String.class))
						, 1, (String)null);
				
//				return MethodHandles.lookup().findStatic(type, "toString", MethodType.methodType(String.class));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
	};
	
	@Override
	public boolean canMap(Class<?> sourceType, Class<?> targetType) {
		return String.class == targetType;
	}

	@Override
	public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
		return toStringMethodHandles.get(sourceType);
	}
	
}