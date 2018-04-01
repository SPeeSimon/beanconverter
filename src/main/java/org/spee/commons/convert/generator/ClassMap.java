package org.spee.commons.convert.generator;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;

import com.google.common.base.Converter;

public class ClassMap {

	private BeanInfo source;
	private BeanInfo target;
	private Collection<MappedProperties> mappedProperties = new ArrayList<>();
	
	
	public ClassMap(BeanInfo source, BeanInfo target) {
        this.source = source;
        this.target = target;
    }


	public BeanInfo getSource() {
		return source;
	}


	public void setSource(BeanInfo source) {
		this.source = source;
	}


	public BeanInfo getTarget() {
		return target;
	}


	public void setTarget(BeanInfo target) {
		this.target = target;
	}


	public Collection<MappedProperties> getMappedProperties() {
		return mappedProperties;
	}


	public static class MappedProperties {
		PropertyDescriptor sourceProperty, targetProperty;
		Class<? extends Converter<?, ?>> customConverter;
		
		public Type getSourceType(){
			return sourceProperty.getReadMethod().getGenericReturnType();
		}
		
		public Type getTargetType(){
			return targetProperty.getWriteMethod().getParameterTypes()[0];
		}
		
		public PropertyDescriptor getSourceProperty() {
			return sourceProperty;
		}

		public void setSourceProperty(PropertyDescriptor sourceProperty) {
			this.sourceProperty = sourceProperty;
		}

		public PropertyDescriptor getTargetProperty() {
			return targetProperty;
		}

		public void setTargetProperty(PropertyDescriptor targetProperty) {
			this.targetProperty = targetProperty;
		}

		public Class<? extends Converter<?, ?>> getCustomConverter() {
			return customConverter;
		}

		public void setCustomConverter(Class<? extends Converter<?, ?>> customConverter) {
			this.customConverter = customConverter;
		}

		public boolean hasSourceProperty(){
			return sourceProperty != null;
		}
		
		public boolean hasTargetProperty(){
			return targetProperty != null;
		}
		
		public boolean hasCustomConverter(){
			return customConverter != null;
		}
		
		@Override
		public String toString() {
			return "MappedProperty{ source=" + sourceProperty
					+ ", target=" + targetProperty
					+ ", customconverter=" + customConverter
					+ "}";
		}
	}
}
