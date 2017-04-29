package org.spee.commons.classutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark the type or method not to be used. Like {@link Deprecated} but will give an error instead of a warning.
 * This way, new compiled code will be prevented to use it, but the method still exists in runtime, so existing code will not break.
 * @author shave
 */
@Inherited
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
public @interface Obsolete {
	/**
	 * Friendly message to other developers
	 * @return
	 */
	String value() default "";
}
