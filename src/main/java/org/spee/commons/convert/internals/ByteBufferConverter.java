package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;

public class ByteBufferConverter implements InternalConverter {

	@Override
	public boolean canMap(Class<?> sourceType, Class<?> targetType) {
		return 
			(sourceType == ByteBuffer.class && targetType == byte[].class) ||
			(sourceType == byte[].class && targetType == ByteBuffer.class) ||
			(sourceType == ByteBuffer.class && targetType == Object.class) || 
			(sourceType == Object.class && targetType == ByteBuffer.class);
	}

	@Override
	public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
		
		if( targetType == ByteBuffer.class ){
			return toByteBuffer(sourceType);
		}
		if( sourceType == ByteBuffer.class ){
			return fromByteBuffer(targetType);
		}
		
		return null;
	}

	private MethodHandle fromByteBuffer(Class<?> targetType) {
//		ByteBuffer b; b.remaining();
//		byte[] bytes = new byte[source.remaining()];
//		source.get(bytes);
//
//		if (targetType.isAssignableTo(BYTE_ARRAY_TYPE)) {
//			return bytes;
//		}
		Array.newInstance(byte.class, 0);
//		return this.conversionService.convert(bytes, BYTE_ARRAY_TYPE, targetType);
		if( byte[].class == targetType || Byte[].class == targetType ){
			try {
				MethodHandles.lookup().findVirtual(ByteBuffer.class, "get", MethodType.methodType(Void.TYPE, byte[].class));
				
//						MethodHandles.lookup().findStatic(Array.class, "newInstance", MethodType.methodType(Object.class, byte.class))
			} catch (NoSuchMethodException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}

	private MethodHandle toByteBuffer(Class<?> sourceType) {
		if( sourceType == byte[].class ){
			
//			byte[] bytes = (byte[]) (source instanceof byte[] ? source :
//			this.conversionService.convert(source, sourceType, BYTE_ARRAY_TYPE));
//
//			ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
//			byteBuffer.put(bytes);
	
		}
		if ( ByteBuffer.class.isAssignableFrom(sourceType) ){
//			ByteBuffer b; b.duplicate();
			// public abstract ByteBuffer duplicate();
			try {
				return MethodHandles.lookup().findVirtual(sourceType, "duplicate", MethodType.methodType(ByteBuffer.class));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
}