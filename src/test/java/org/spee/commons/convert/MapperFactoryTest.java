package org.spee.commons.convert;

import static org.junit.Assert.*;

import org.junit.Test;

public class MapperFactoryTest {

	@Test
	public void testRegisterClassOfQ() {
		System.setProperty("org.slf4j.simpleLogger.log.org.spee", "debug");

		MapperFactory.register(DefaultConverters.class);
		
	}

}
