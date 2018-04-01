package org.spee.commons.classutils;

import com.google.common.base.Optional;

public class ClassUtils {

	/**
	 * Uses <code>Class.forName(className)</code> to return an Optional Class where
	 * you can check whether it is present() or absent() before getting it.
	 * @param className
	 * @return
	 */
	public static Optional<Class<?>> getClass(String className){
		try {
			return Optional.<Class<?>>of( Class.forName(className) );
		} catch (ClassNotFoundException e) {
			return Optional.<Class<?>>absent();
		}
	}
		
}
