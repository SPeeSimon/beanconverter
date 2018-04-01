package org.spee.commons.utils;

import static org.junit.Assert.*;

import org.junit.Test;

public class IterableUtilsTest {

	@Test
	public void testOfIterableOfT() {
		fail("Not yet implemented");
	}


	@Test
	public void testOfTArray() {
		String[] array = new String[0];
		for (String val : IterableUtils.of(array)) {
			System.out.println(val);
		}
		
	}

}
