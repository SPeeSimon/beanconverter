package org.spee.commons.convert;

import static org.junit.Assert.*;

import org.junit.Test;
import org.slf4j.LoggerFactory;

public class MapperFactoryTest {

	@Test
	public void testRegisterClassOfQ() {
		System.setProperty("org.slf4j.simpleLogger.log.org.spee", "debug");

		MapperFactory.register(DefaultConverters.class);
		
	}

	
	public void convert(Object a, Object b) {
		try {
			b = a.toString();
		}catch (Throwable e) {
			LoggerFactory.getLogger("test").trace("error");
			throw e;
		}
	}
	
}
