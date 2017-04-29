package org.spee.commons.utils;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.Test;

public class ReflectionUtilsTest {

	class Example{
		String name;
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
	}
	
	@Test
	public void testGetBeanProperties() {
		
	}

}
