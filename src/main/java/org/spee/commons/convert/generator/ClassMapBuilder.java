package org.spee.commons.convert.generator;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.convert.generator.ClassMap.MappedProperties;
import org.spee.commons.utils.ObjectUtils;
import org.spee.commons.utils.StringUtils;

import com.google.common.base.Converter;


public class ClassMapBuilder {
	private final static Logger logger = LoggerFactory.getLogger(ClassMapBuilder.class);
	private ClassMap classMap;
	private Map<String, PropertyDescriptor> sourceProperties, targetProperties;

	
	public static ClassMapBuilder build(Class<?> sourceClass, Class<?> targetClass){
		return new ClassMapBuilder(sourceClass, targetClass);
	}
	

	private ClassMapBuilder(Class<?> sourceClass, Class<?> targetClass) {
		try {
			BeanInfo source = Introspector.getBeanInfo(sourceClass);
			BeanInfo target = Introspector.getBeanInfo(targetClass);
			classMap = new ClassMap(source, target);
		} catch (IntrospectionException e) {
		}
	}
	
	
	
	private MappedProperties getMapping(final String fieldSource, final String fieldTarget){
		MappedProperties mapped = new MappedProperties();
		
		if( sourceProperties == null ){
			sourceProperties = new HashMap<>();
			for (PropertyDescriptor propertyDescriptor : classMap.getSource().getPropertyDescriptors()) {
				sourceProperties.put(propertyDescriptor.getName(), propertyDescriptor);
				if( propertyDescriptor.getName().equals(fieldSource) ){
					mapped.sourceProperty = propertyDescriptor;
				}
			}
		} else {
			mapped.sourceProperty = sourceProperties.get(fieldSource);
		}
		
		if( targetProperties == null ){
			targetProperties = new HashMap<>();
			for (PropertyDescriptor propertyDescriptor : classMap.getTarget().getPropertyDescriptors()) {
				targetProperties.put(propertyDescriptor.getName(), propertyDescriptor);
				if( propertyDescriptor.getName().equals(fieldTarget) ){
					mapped.targetProperty = propertyDescriptor;
				}
			}
		} else {
			mapped.targetProperty = targetProperties.get(fieldTarget);
		}
		
		return mapped;
	}
	
	
	/**
	 * Add a mapping of a field on the source class to field on the target class.
	 * Any type conversion is looked up in the available converters.
	 * @param fieldSource
	 * @param fieldTarget
	 * @return 
	 */
	public ClassMapBuilder mapField(final String fieldSource, final String fieldTarget){
		MappedProperties mapped = getMapping(fieldSource, fieldTarget);
		
		if( mapped.hasSourceProperty() && mapped.hasTargetProperty() ){
			classMap.getMappedProperties().add(mapped);
		} else {
			logger.warn("Could not set up mapper from '{}' to '{}':{}{}", fieldSource, fieldTarget, 
							ObjectUtils.toString(mapped.sourceProperty, " source property not found", ""), ObjectUtils.toString(mapped.targetProperty, " target property not found", ""));
		}
		
		return this;
	}
	
	/**
	 * Add a mapping which must use the given Converter, instead of looking for it in the available converters.
	 * @param fieldSource
	 * @param fieldTarget
	 * @param specificConverter
	 * @return
	 */
	public ClassMapBuilder mapField(String fieldSource, String fieldTarget, Class<? extends Converter<?, ?>> specificConverter){
		MappedProperties mapped = getMapping(fieldSource, fieldTarget);
		mapped.customConverter = specificConverter;
		
		if( mapped.hasSourceProperty() && mapped.hasTargetProperty() ){
			classMap.getMappedProperties().add(mapped);
		} else {
			logger.warn("Could not set up mapper from '{}' to '{}':{}{}", fieldSource, fieldTarget, 
							ObjectUtils.toString(mapped.sourceProperty, " source property not found", ""), ObjectUtils.toString(mapped.targetProperty, " target property not found", ""));
		}
		return this;
	}
	
/*	public ClassMapBuilder mapField(String fieldSource, String fieldTarget, String customConverterId){
		MappedProperties mapped = getMapping(fieldSource, fieldTarget);
		
		return this;
	}
*/	
	
	/**
	 * @see #useDefaults(false)
	 */
	public ClassMapBuilder useDefaults(){
		return useDefaults(false);
	}

	/**
	 * Looks for fields matching by name and map the matching properties
	 * @param ignoreCasing <code>true</code> to ignore case to be different. <code>false</code> if casing is different.  
	 * @return 
	 */
	public ClassMapBuilder useDefaults(boolean ignoreCasing){
		final Comparator<String> stringComparer = StringUtils.stringComparer(ignoreCasing);
		
		for (PropertyDescriptor sourceDescriptor : classMap.getSource().getPropertyDescriptors()) {
			if( !sourceDescriptor.getName().equalsIgnoreCase("class") ){ // ignore the getClass() method as a property
				for (PropertyDescriptor targetDescriptor : classMap.getTarget().getPropertyDescriptors()) {
					if( stringComparer.compare(sourceDescriptor.getName(),targetDescriptor.getName()) == 0 ){
						MappedProperties mapped = new MappedProperties();
						mapped.sourceProperty = sourceDescriptor;
						mapped.targetProperty = targetDescriptor;
						classMap.getMappedProperties().add(mapped);
						break;
					}
				}
			}
		}
		
		return this;
	}
	
	
	
	public CustomProperty source(){
		CustomProperty cp = new CustomProperty(this, classMap.getSource());
		return cp;
	}
	
	public CustomProperty target(){
		CustomProperty cp = new CustomProperty(this, classMap.getTarget());
		return cp;
	}
	
	
	
	protected class CustomProperty {
		final ClassMapBuilder builder;
		final BeanInfo inClass;
		
		public CustomProperty(ClassMapBuilder builder, BeanInfo inClass) {
			this.builder = builder;
			this.inClass = inClass;
		}

		public CustomProperty getter(String methodName){
			return this;
		}
		
		public CustomProperty setter(String methodName){
			return this;
		}
		
		public CustomProperty property(String property){
			return this;
		}
		
	}

	
	
	public ClassMap generate(){
		return classMap;
	}
	
}
