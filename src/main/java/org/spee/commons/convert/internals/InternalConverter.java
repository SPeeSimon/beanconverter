package org.spee.commons.convert.internals;

import java.lang.invoke.MethodHandle;

public interface InternalConverter {

	boolean canMap(final Class<?> sourceType, final Class<?> targetType);

	public MethodHandle getTypeConverter(final Class<?> sourceType, final Class<?> targetType);
}
