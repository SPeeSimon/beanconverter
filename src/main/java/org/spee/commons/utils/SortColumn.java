package org.spee.commons.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SortColumn {
	/**
	 * An alternative id for naming the field.
	 * @return
	 */
	String value() default "";
}
