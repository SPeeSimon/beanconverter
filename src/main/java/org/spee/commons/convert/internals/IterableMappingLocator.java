package org.spee.commons.convert.internals;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import org.spee.commons.utils.CollectionUtils;
import org.spee.commons.utils.MapUtils;

import com.google.common.base.Function;



public class IterableMappingLocator {

	private static Map<Class<?>, Map<Class<?>, MethodHandle>> loopFunctions = new IdentityHashMap<>();
	
	public static CallSite bootstrap(final MethodHandles.Lookup caller, final String name, final MethodType type) {
		return new ConstantCallSite(findLoop(type.parameterType(0), type.parameterType(1)).asType(type));
	}
	
		
	private static MethodHandle findLoop(Class<?> from, Class<?> to){
		Map<Class<?>, MethodHandle> allFrom = MapUtils.putIfAbsent(loopFunctions, from, new IdentityHashMap<Class<?>, MethodHandle>());
		MethodHandle methodHandle = allFrom.get(to);
		if( methodHandle == null ){
			try {
				return MethodHandles.lookup().findStatic(CollectionUtils.class, "transformInto", MethodType.methodType(Void.TYPE, Iterable.class, Collection.class, Function.class));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return methodHandle;
	}
	
	
	
	
	private static class MethodHandleFunction<F,T> implements Function<F, T> {
		
		final MethodHandle function;

		public MethodHandleFunction(MethodHandle function) {
			super();
			this.function = function;
		}

		public T apply(F input) {
			try {
				return (T)function.invokeWithArguments(input);
			} catch (RuntimeException | Error e){
				throw e;
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		};
		
	}
	
}
