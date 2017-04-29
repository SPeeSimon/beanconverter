package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Primitives;

public class ImmutableMapper implements InternalConverter {

	private static final Logger logger = LoggerFactory.getLogger(ImmutableMapper.class);
	protected static final MethodHandle IDENTITY_CONVERSION = MethodHandles.identity(Object.class);
	
	@Override
	public boolean canMap(Class<?> sourceType, Class<?> targetType) {
		return (sourceType.isPrimitive() && targetType.isPrimitive())
				|| (String.class == sourceType && sourceType == targetType)
				|| ((Primitives.isWrapperType(sourceType) || sourceType.isPrimitive()) && Primitives.wrap(sourceType) == Primitives.wrap(targetType))
				;
	}

	@Override
	public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
		logger.debug("no converting needed for {} to {}", sourceType, targetType);
		return IDENTITY_CONVERSION;
	}

}
