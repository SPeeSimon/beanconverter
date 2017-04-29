package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class NumberConverter implements InternalConverter {

    private static ClassValue<Boolean> number = new ClassValue<Boolean>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			for(Class numberClass : new Class[]{Byte.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE,
	    								Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class}
			){
				if( numberClass == type ){
					return Boolean.TRUE;
				}
			}
			return Boolean.FALSE;
		}
	};
	

	private static ClassValue<MethodHandle> valueOfMethodHandle = new ClassValue<MethodHandle>() {
		@Override
		protected MethodHandle computeValue(Class<?> type) {
			try {
				return MethodHandles.publicLookup().findStatic(type, "valueOf", MethodType.methodType(type, String.class));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				e.printStackTrace();
			}
			return null;
		}
	};
    
	@Override
	public boolean canMap(java.lang.Class<?> sourceType, java.lang.Class<?> targetType) {
		return number.get(sourceType) || number.get(targetType);
	}
	
	@Override
	public MethodHandle getTypeConverter(final Class<?> sourceType, final Class<?> targetType){

		if( number.get(sourceType) && number.get(targetType) ){
			return ImmutableMapper.IDENTITY_CONVERSION;
		}

		if( String.class.isAssignableFrom(targetType) ){
			// toString
			try {
				return MethodHandles.publicLookup().findStatic(String.class, "valueOf", MethodType.methodType(String.class, sourceType));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		if( String.class.isAssignableFrom(sourceType) ){
			// fromString
			if( Byte.class == targetType || Byte.TYPE == targetType ){
				return valueOfMethodHandle.get(Byte.class);
			}
			if( Short.class == targetType || Short.TYPE == targetType ){
				return valueOfMethodHandle.get(Short.class);
			}
			if( Integer.class== targetType || Integer.TYPE== targetType ){
				return valueOfMethodHandle.get(Integer.class);
			}
			if( Long.class== targetType || Long.TYPE== targetType ){
				return valueOfMethodHandle.get(Long.class);
			}
			if( Float.class== targetType || Float.TYPE== targetType ){
				return valueOfMethodHandle.get(Float.class);
			}
			if( Double.class== targetType || Double.TYPE== targetType ){
				return valueOfMethodHandle.get(Double.class);
			}
		}
		return null;
	}

	
}
