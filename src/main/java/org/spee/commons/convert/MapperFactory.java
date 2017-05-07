package org.spee.commons.convert;

import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.parameterizedType;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.spee.commons.utils.ReflectionUtils.hasParameterCount;
import static org.spee.commons.utils.ReflectionUtils.isAnnotationPresentOnMethod;

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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.TypeCache;
import net.bytebuddy.TypeCache.SimpleKey;
import net.bytebuddy.TypeCache.Sort;
import net.bytebuddy.implementation.InvokeDynamic;

public class MapperFactory {
    private static final Logger LOG = LoggerFactory.getLogger(MapperFactory.class);
    @SuppressWarnings("rawtypes")
    private static final Class<Convert> CONVERTER_TYPE = Convert.class;
    private static final TypeCache<SimpleKey> TYPECACHE = new TypeCache<>(Sort.SOFT);
	
    static{
    	register(DefaultConverters.class);
    	register(CollectionUtils.class);
    }


    /**
     * Create the type converter and store in cache. 
     * @param sourceType
     * @param targetType
     * @return
     */
	private static <S,T> Convert<S,T> createTypeConverter(Class<S> sourceType, Class<T> targetType) {
		Preconditions.checkArgument(!sourceType.isPrimitive(), "Primitive type not supported: %s", sourceType);
		Preconditions.checkArgument(!targetType.isPrimitive(), "Primitive type not supported: %s", targetType);
		final ClassLoader classLoader = MapperFactory.class.getClassLoader();
		final SimpleKey key = new SimpleKey(sourceType, targetType);
		
		try {
			Class<?> converter = TYPECACHE.find(classLoader, key);

			if( converter == null ){
				converter =
					new ByteBuddy()
							.subclass(parameterizedType(CONVERTER_TYPE, sourceType, targetType).build())
					.method( isDeclaredBy(CONVERTER_TYPE).and(named("convert")) )
						.intercept(
							InvokeDynamic.bootstrap(MappingLocator.class.getDeclaredMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class))
								.withMethodArguments()
						)
					.make()
					.load(classLoader)
					.getLoaded();
				TYPECACHE.insert(classLoader, key, converter);
			}

			TYPECACHE.expungeStaleEntries();
			return (Convert<S,T>)converter.newInstance();
		}
		catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException e) {
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
		final Predicate<Method> methodCheck = Predicates.and(isAnnotationPresentOnMethod(Converter.class), hasParameterCount(1));
		for (Method method : container.getMethods()) {
			if( methodCheck.apply(method) ){
				LOG.trace("Registering @Converter on method {} of {}", method, container);
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
