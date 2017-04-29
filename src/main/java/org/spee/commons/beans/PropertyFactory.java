package org.spee.commons.beans;

import com.google.common.reflect.TypeToken;

public class PropertyFactory {

	
	public static <C,T> Property<C, T> getFor(String property, TypeToken<T> propertyType, TypeToken<C> beanType){
		return new Property<>();
	}
	
	public static <C,T> Property<C, T> getFor(String property, Class<T> propertyType, Class<C> beanType){
		return new Property<>();
	}
	
	public static <C,T> Property<C, T> getFor(String property, TypeToken<T> propertyType, Class<C> beanType){
		return new Property<>();
	}
	
	
}
