package org.spee.commons.convert;

public interface Convert<S,T> {

	T convert(S source);
	
}
