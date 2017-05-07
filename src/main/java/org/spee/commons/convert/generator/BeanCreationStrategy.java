package org.spee.commons.convert.generator;

import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.xml.datatype.XMLGregorianCalendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.convert.DefaultImplementations;
import org.spee.commons.convert.internals.XMLGregorianCalendarConverter;
import org.spee.commons.utils.MapUtils;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;

/**
 * Possible methods how a new instance of an object is created.
 * Currently:
 * <ul>
 * <li>Constructor (the default noargs constructor)
 * <li>By adding a {@link Supplier} that returns the new instance
 * <li>Retrieve a instance of the registered implementation for the given interface (see: {@link DefaultImplementations})
 * <li>static method getInstance() of the given type
 * <li>static method newInstance() of the given type
 * <li>A instance for {@link XMLGregorianCalendar}
 * </ul>
 * @author shave
 *
 */
public class BeanCreationStrategy {

	private static final Logger logger = LoggerFactory.getLogger(BeanCreationStrategy.class);
	private static final BySupplier bySupplierStrategy = new BySupplier();
	
	protected static final String STRATEGY_CONSTRUCTOR = "constructor";
	protected static final String STRATEGY_SUPPLIER = "supplier";
	protected static final String STRATEGY_INTERFACE = "interface";
	protected static final String STRATEGY_GETINSTANCE = "getInstance";
	protected static final String STRATEGY_NEWINSTANCE = "newInstance";
	protected static final String STRATEGY_XMLGEGORIANCALENDAR = "xmlGregorianCalendar";

	private static final Map<String,Function<Class<?>, Optional<MethodHandle>>> creationStrategyMap;

	static{
		Map<String,Function<Class<?>, Optional<MethodHandle>>> strategy = new IdentityHashMap<>(6);
		strategy.put(STRATEGY_CONSTRUCTOR, ByConstructor.INSTANCE);
		strategy.put(STRATEGY_SUPPLIER, bySupplierStrategy);
		strategy.put(STRATEGY_INTERFACE, ByInterface.INSTANCE);
		strategy.put(STRATEGY_GETINSTANCE, ByGetInstance.INSTANCE);
		strategy.put(STRATEGY_NEWINSTANCE, ByNewInstance.INSTANCE);
		strategy.put(STRATEGY_XMLGEGORIANCALENDAR, XmlGregorianCalendar.INSTANCE);
		creationStrategyMap = Collections.unmodifiableMap(strategy);
	}
	
	private static final ClassValue<MethodHandle> beanCreator = new ClassValue<MethodHandle>() {
		@Override
		protected MethodHandle computeValue(final Class<?> type) {
			for (Function<Class<?>, Optional<MethodHandle>> function : creationStrategyMap.values()) {
				Optional<MethodHandle> possibleMethodHandle = function.apply(type);
				if( possibleMethodHandle.isPresent() ){
					MethodHandle methodHandle = possibleMethodHandle.get();
					if( methodHandle != null ){
						logger.debug("Using creation strategy for type {}: {}", type, function);
						return methodHandle;
					}
				}
			}
			return null; // TODO create a fallback handler
		}
	};
	
	/**
	 * Used by Java for bootstrapping generated classes with a predefined creation strategy.
	 */
	public static CallSite bootstrap(final MethodHandles.Lookup caller, final String name, final MethodType type, String useStrategy) throws NoSuchMethodException, IllegalAccessException {
		final Class<?> targetType = type.returnType();
		Function<Class<?>, Optional<MethodHandle>> function = creationStrategyMap.get(useStrategy);
		
		if( function != null ){
			Optional<MethodHandle> methodHandle = function.apply(targetType);
			if( methodHandle.isPresent() ){
				return new ConstantCallSite(methodHandle.get().asType(type));
			}
		}
		
		return bootstrap(caller, name, type);
	}
	
	/**
	 * Used by Java for bootstrapping generated classes
	 */
	public static CallSite bootstrap(final MethodHandles.Lookup caller, final String name, final MethodType type) throws NoSuchMethodException, IllegalAccessException {
		final Class<?> targetType = type.returnType();
		logger.debug("Bootstrap for creating a new instance of {}", targetType);
		return new ConstantCallSite(beanCreator.get(targetType).asType(type));
	}
	
	/**
	 * Retrieve a new instance of the given type.
	 * @param type
	 * @return 
	 * @throws RuntimeException in case of an error
	 */
	public static <T> T newInstance(final Class<T> type) {
		try {
			return (T)beanCreator.get(type).invoke();
		} catch (RuntimeException | Error e ){
			throw e;
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Register a {@link Supplier} that can create a certain type.
	 * @param supplier
	 */
	public static void register(final Supplier<?> supplier){
		bySupplierStrategy.register(supplier);
	}
	

	private enum ByConstructor implements Function<Class<?>, Optional<MethodHandle>> {
		INSTANCE;
		
		@Override
		public Optional<MethodHandle> apply(final Class<?> input) {
			try {
				Constructor<?> defaultConstructor = input.getConstructor();
				return Optional.of(publicLookup().in(input).unreflectConstructor(defaultConstructor));
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException e) {
				LoggerFactory.getLogger(getClass()).trace("Could not bind to default constructor of {}: {}", input, e.getMessage(), e);
			}
			return Optional.absent();
		}
		
		@Override
		public String toString() {
			return "Default constructor strategy";
		}
	}
	

	
	private enum ByGetInstance implements Function<Class<?>, Optional<MethodHandle>> {
		INSTANCE;
		
		@Override
		public Optional<MethodHandle> apply(final Class<?> input) {
			try {
				return Optional.of(publicLookup().findStatic(input, "getInstance", methodType(input)));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				LoggerFactory.getLogger(getClass()).trace("Could not bind to method 'getInstance()': {}", e.getMessage(), e);
			}
			return Optional.absent();
		}
		
		@Override
		public String toString() {
			return "Static getInstance() strategy";
		}
	}
	
	
	
	private enum ByNewInstance implements Function<Class<?>, Optional<MethodHandle>> {
		INSTANCE;
		
		@Override
		public Optional<MethodHandle> apply(final Class<?> input) {
			try {
				return Optional.of(publicLookup().findStatic(input, "newInstance", methodType(input)));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				LoggerFactory.getLogger(getClass()).trace("Could not bind to method 'newInstance()': {}", e.getMessage(), e);
			}
			return Optional.absent();
		}
		
		@Override
		public String toString() {
			return "Static newInstance() strategy";
		}
	}

	
	
	private enum ByInterface implements Function<Class<?>, Optional<MethodHandle>> {
		INSTANCE;
		
		@Override
		public Optional<MethodHandle> apply(final Class<?> input) {
			if( input.isInterface() ){
				try {
					Method method = DefaultImplementations.class.getMethod("getImplementationFor", Class.class);
					return Optional.of( insertArguments(publicLookup().unreflect(method), 0, input) );
				} catch (NoSuchMethodException | SecurityException | IllegalAccessException e) {
					LoggerFactory.getLogger(getClass()).warn("Could not bind to method 'DefaultImplementations.getImplementationFor()': {}", e.getMessage(), e);
				}
			}
			return Optional.absent();
		}
		
		@Override
		public String toString() {
			return "Interface DefaultImplementations strategy";
		}
	}

	
	
	private enum XmlGregorianCalendar implements Function<Class<?>, Optional<MethodHandle>> {
		INSTANCE;
		
		@Override
		public Optional<MethodHandle> apply(final Class<?> input) {
			if( XMLGregorianCalendar.class.isAssignableFrom(input) ){
				try {
					return Optional.of(lookup().findStatic(XMLGregorianCalendarConverter.class, "createInstance", methodType(XMLGregorianCalendar.class)));
				} catch (NoSuchMethodException | IllegalAccessException e) {
					LoggerFactory.getLogger(getClass()).trace("Could not bind to method 'XMLGregorianCalendarConverter.createInstance()': {}", e.getMessage(), e);
				}
			}
			return Optional.absent();
		}
		
		@Override
		public String toString() {
			return "XmlGregorianCalendar strategy";
		}
	}
	
	
	private static class BySupplier implements Function<Class<?>, Optional<MethodHandle>> {
		Map<Type, Supplier<?>> suppliers = new IdentityHashMap<>();
		
		public void register(final Supplier<?> supplier){
			ParameterizedType classTypes = (ParameterizedType)supplier.getClass().getGenericInterfaces()[0];
			Type type = classTypes.getActualTypeArguments()[0];
			MapUtils.putIfAbsent(suppliers, type, supplier);
		}
		
		@Override
		public Optional<MethodHandle> apply(final Class<?> input) {
			Supplier<?> supplier = suppliers.get(input);
			if( supplier != null ){
				try {
					return Optional.of( lookup().bind(supplier, "get", methodType(Object.class)).asType(methodType(input)) );
				} catch (NoSuchMethodException | IllegalAccessException e) {
					LoggerFactory.getLogger(getClass()).trace("Could not bind to method 'get()' of Supplier {}: {}", supplier.getClass(), e.getMessage(), e);
				}
			}
			return Optional.absent();
		}

		@Override
		public String toString() {
			return "Supplier strategy";
		}
	}
	
//	public static Function<Class<?>, Optional<MethodHandle>> byStaticCreateMethod(Class<?> type) {
//		find type, find method => invoke
//		return null;
//	}
//	public static Function<Class<?>, Optional<MethodHandle>> byFactory(Class<?> type) {
//		return null;
//	}

}
