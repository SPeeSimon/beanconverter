package org.spee.commons.convert;

import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.parameterizedType;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.convert.generator.BeanCreationStrategy;
import org.spee.commons.convert.internals.MappingLocator;
import org.spee.commons.utils.CollectionUtils;
import org.spee.commons.utils.ReflectionUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.implementation.InvokeDynamic;

public class MapperFactory {
    private static final Logger logger = LoggerFactory.getLogger(MapperFactory.class);
	
    static{
    	register(DefaultConverters.class);
    	register(CollectionUtils.class);
    }

    
	private static <S,T> Convert<S,T> createTypeConverter(Class<S> sourceType, Class<T> targetType) {
		Preconditions.checkArgument(!sourceType.isPrimitive(), "Primitive type not supported: %s", sourceType);
		Preconditions.checkArgument(!targetType.isPrimitive(), "Primitive type not supported: %s", targetType);
		
		@SuppressWarnings("rawtypes") final Class<Convert> converterType = Convert.class;
		try {
			@SuppressWarnings("unchecked")
			Unloaded<Convert<S, T>> unloaded = (Unloaded<Convert<S, T>>)
				new ByteBuddy()
						.subclass(parameterizedType(converterType, sourceType, targetType).build())
				.method( isDeclaredBy(converterType).and(named("convert")) )
					.intercept(
							InvokeDynamic.bootstrap(MappingLocator.class.getDeclaredMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class))
								.withMethodArguments()
					)
				.make();
		
			Class<? extends Convert<S,T>> loaded2 = unloaded.load(MapperFactory.class.getClassLoader()).getLoaded();
			return loaded2.newInstance();
		} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}    
    

	@SuppressWarnings("unchecked")
	public <S, T> T convert(S from, Class<T> toClass){
    	return getConverter((Class<S>)from.getClass(), toClass).convert(from);
    }

    
    public static <S,T> Convert<S,T> getConverter(final Class<S> fromClass, final Class<T> toClass){
    	return createTypeConverter(fromClass, toClass);
    }


	public static void register(String id, com.google.common.base.Converter<?, ?> customConverter) {
		try {
			ParameterizedType classTypes = (ParameterizedType)customConverter.getClass().getGenericSuperclass();
			
			Class<?> sourceType = (Class<?>)classTypes.getActualTypeArguments()[0];
			Class<?> targetType = (Class<?>)classTypes.getActualTypeArguments()[1];
			MethodHandle methodHandle = MethodHandles.lookup().bind(customConverter, "convert", MethodType.methodType(Object.class, Object.class));
			
			MappingLocator.register( sourceType, targetType, methodHandle);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}
    
    
	public static void register(Class<?> container){
		final Predicate<Method> methodCheck = Predicates.and(ReflectionUtils.isAnnotationPresentOnMethod(Converter.class), ReflectionUtils.hasParameterCount(1));
		for (Method method : container.getMethods()) {
			if( methodCheck.apply(method) ){
				logger.debug("Registering @Converter on method {} of {}", method, container);
				try {
					Class<?> sourceType = method.getParameterTypes()[0];
					Class<?> targetType = method.getReturnType();
					MethodHandle methodHandle = MethodHandles.lookup().unreflect(method);
					
					MappingLocator.register(sourceType, targetType, methodHandle);
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
	}
    
	public static void register(Supplier<?> creator){
		BeanCreationStrategy.register(creator);
	}

}
