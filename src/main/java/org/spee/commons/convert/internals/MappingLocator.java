package org.spee.commons.convert.internals;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.utils.MapUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;

public class MappingLocator {

	private final static Logger logger = LoggerFactory.getLogger(MappingLocator.class);
	private final static InternalConverter noAvailableConverter = new NoAvailableConverter();
	private final static List<InternalConverter> interalConverters = new LinkedList<>();
	private final static Map<Type, Map<Type, MethodHandle>> converters = new IdentityHashMap<>();
	private final static Cache<String, Type> genericMethod = CacheBuilder.newBuilder().build(new TypeFromDescriptor());

	static{
		interalConverters.add(new ImmutableMapper());
		interalConverters.add(new MethodConverterMapper());
//		interalConverters.add(new EnumConverter());
		interalConverters.add(new NumberConverter());
		interalConverters.add(new StringToEnumConverterFactory());
		interalConverters.add(new IntegerToEnumConverterFactory());
	}
	
	
	private static class TypeFromDescriptor extends CacheLoader<String, Type> {

		@Override
		public Type load(String key) throws Exception {
			// TODO String to generic type
			return null;
		}
	}
	
	/**
	 * Bootstrap for Java invokedynamic calls for converting a type.
	 * @param caller
	 * @param name
	 * @param type The method signature
	 * @return
	 * @throws NoSuchMethodException
	 * @throws IllegalAccessException
	 */
	public static CallSite bootstrap(final MethodHandles.Lookup caller, final String name, final MethodType type, String genericMethodDescriptor) throws NoSuchMethodException, IllegalAccessException {
		if( genericMethodDescriptor != null && genericMethodDescriptor.length() > 1 ){
			// generic method
			logger.trace("bootstrap called for generic conversion {}", genericMethodDescriptor);
			Type type2 = genericMethod.getIfPresent(genericMethodDescriptor);
			
		}
		return bootstrap(caller, name, type);
	}

	
	public static CallSite bootstrap(final MethodHandles.Lookup caller, final String name, final MethodType type) throws NoSuchMethodException, IllegalAccessException {
		logger.trace("bootstrap called for conversion {}", type);
		final Class<?> sourceType = type.parameterType(0);
		final Class<?> targetType = type.returnType();
		MethodHandle converter;
		
		for (InternalConverter internalConverter : interalConverters) {
			if( internalConverter.canMap(sourceType, targetType) ){
				converter = internalConverter.getTypeConverter(sourceType, targetType);
				if( converter != null ){
					logger.debug("using internal converter");
					return typedConstantCallSite(converter, type);
				}
			}
		}
		
		converter = findConverter(sourceType, targetType);
		if( converter != null ){
			return typedConstantCallSite(converter, type);
		}
		
		logger.warn("no converter found to convert '{}' to '{}'", sourceType, targetType);
		return typedConstantCallSite(noAvailableConverter.getTypeConverter(sourceType, targetType), type);
	}

	
	private static final CallSite typedConstantCallSite(final MethodHandle methodHandle, final MethodType type){
		return new ConstantCallSite( methodHandle.asType(type) );
	}
	

	private static MethodHandle findConverter(Type sourceType, Type targetType) {
		Map<Type, MethodHandle> sourceTypeConverters = MapUtils.putIfAbsent(converters, sourceType, new IdentityHashMap<Type, MethodHandle>());
		MethodHandle converter = sourceTypeConverters.get(targetType);
		return converter;
	}


	public static void register(Class<?> sourceType, Class<?> targetType, MethodHandle methodHandle) {
		if( findConverter(sourceType, targetType) == null ){
			logger.debug("registering converter for {} to {}", sourceType, targetType);
			converters.get(sourceType).put(targetType, methodHandle);
		}else{
			logger.warn("There is already a registered converter for {} to {}", sourceType, targetType);
		}
	}
    
	
}
