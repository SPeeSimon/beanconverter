package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class EnumConverter implements InternalConverter {

	static MethodHandle enumValueOfMethodHandle = getEnumValueOfMethodHandle();

	private static MethodHandle getEnumValueOfMethodHandle() {
		try {
			MethodHandle methodHandle = MethodHandles.lookup().findStatic(Enum.class, "valueOf", MethodType.methodType(Enum.class, Class.class, String.class));
			return methodHandle;
		} catch (NoSuchMethodException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	private static ClassValue<MethodHandle> enumToStringMethodHandle = new ClassValue<MethodHandle>() {
		@Override
		protected MethodHandle computeValue(final Class<?> type) {
			try {
				return MethodHandles.lookup().findVirtual(type, "toString", MethodType.methodType(String.class));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
	};

	@Override
	public boolean canMap(Class<?> sourceType, Class<?> targetType) {
		return sourceType.isEnum() || targetType.isEnum();
	}

	@Override
	public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
		if( targetType.isEnum() ){
//			Enum.valueOf(targetType, "value");
			if( String.class == sourceType ){
				return MethodHandles.insertArguments(enumValueOfMethodHandle, 0, targetType);
			}
			else {
				return MethodHandles.filterArguments(
						MethodHandles.insertArguments(enumValueOfMethodHandle , 0, targetType)
						, 0, enumToStringMethodHandle.get(sourceType)
						);				
			}
		}
		else if (sourceType.isEnum()){
			if( String.class == targetType ){
				// toString
				return enumToStringMethodHandle.get(sourceType);
			}
		}
		return null;
	}

}
