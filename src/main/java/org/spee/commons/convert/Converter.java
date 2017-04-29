package org.spee.commons.convert;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicate that the given method can be used for type conversion.
 * For use the method must take a single argument and it's return type must not be void.
 * <pre>
 * @Converter public Integer stringToInteger(String value){...}
 * </pre>
 * @author shave
 * @see DefaultConverters
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Converter {

}
