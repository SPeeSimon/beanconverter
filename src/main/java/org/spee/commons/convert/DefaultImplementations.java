package org.spee.commons.convert;

import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.spee.commons.utils.MapUtils;
import org.spee.commons.utils.ReflectionUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;


/**
 * This class is where the matching of an Interface to an implementation is kept.
 * Default are the Collection interfaces mapped to a specific implementation of that interface.
 * So if you want to retrieve an implementation for {@link List} all you need to do is call the {@link #getImplementationFor(Class)}
 * method with <code>List.class</code>.
 * 
 * @author shave
 *
 */
public final class DefaultImplementations {

	private final static Map<Class<?>, Supplier<?>> interfaceImplementations = new IdentityHashMap<>();
	
	static{
		addImplementationForInterface(Collection.class, LinkedList.class);
		addImplementationForInterface(List.class, LinkedList.class);
		addImplementationForInterface(Set.class, LinkedHashSet.class);
		addImplementationForInterface(Map.class, LinkedHashMap.class);
		addImplementationForInterface(Iterable.class, LinkedList.class);
		addImplementationForInterface(Deque.class, LinkedList.class);
		addImplementationForInterface(SortedSet.class, TreeSet.class);
	}
	
	
	/**
	 * Retrieve an Implementation for the given Interface.
	 * 
	 * @param intf
	 * @return
	 * @throws NullPointerException if no <code>intf</code> is given
	 * @throws IllegalArgumentException if the <code>intf</code> is not an interface
	 * @throws RuntimeException if no implementation is found or the found implementation could not be instantiated
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getImplementationFor(Class<T> intf){
		Preconditions.checkNotNull(intf);
		Preconditions.checkArgument(intf.isInterface(), "Not an interface %s", intf);
		
		return (T)findImplementation(intf).get();
	}


	private static <T> Supplier<?> findImplementation(Class<T> intf) {
		Supplier<?> implementation = interfaceImplementations.get(intf); // First try exact matching
		
		if( implementation == null ){ // No match; than deep search
			implementation = MapUtils.findValue(interfaceImplementations, ReflectionUtils.isAssignableFrom(intf)).orNull();

			if( implementation == null ){
				throw new RuntimeException("No implementation found for interface " + intf); // No implementation found
			}
		}
		return implementation;
	}
	
	
	/**
	 * Bind an implementation to an interface.
	 * @param interfaceClass An interface class
	 * @param implementationClass An implementation class that implements the interface and has a default constructor
	 * @throws IllegalArgumentException
	 *  if <code>interfaceClass</code> is not an Interface,
	 *  if <code>implementationClass</code> is an interface,
	 *  if <code>implementationClass</code> is not assignable from the interface,
	 *  if <code>implementationClass</code> is already configured,
	 *  if <code>implementationClass</code> does not have a default constuctor.
	 */
	public static void addImplementationForInterface(Class<?> interfaceClass, Class<?> implementationClass){
		Preconditions.checkArgument(interfaceClass.isInterface(), "Not an interface: %s", interfaceClass);
		Preconditions.checkArgument(!implementationClass.isInterface(), "Implementation is an interface: %s", implementationClass);
		Preconditions.checkArgument(interfaceClass.isAssignableFrom(implementationClass), "Implementation %s does not implement %s", implementationClass, interfaceClass);
		try {
			implementationClass.getConstructor();
		} catch (NoSuchMethodException | SecurityException e) {
			throw new IllegalArgumentException("Implementation has no default constructor: " + implementationClass, e);
		};
		Preconditions.checkArgument(!interfaceImplementations.containsKey(interfaceClass), "Implementation for %s is already present", interfaceClass);

		// All preconditions met, so ok to add
		interfaceImplementations.put(interfaceClass, ReflectionUtils.newInstanceSupplier(implementationClass));
	}
	
}
