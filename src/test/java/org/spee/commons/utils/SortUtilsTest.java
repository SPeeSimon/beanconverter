package org.spee.commons.utils;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

public class SortUtilsTest {

	@SortColumn
	public class Example1{
		String one, two, three;

		public Example1(String one, String two, String three) {
			super();
			this.one = one;
			this.two = two;
			this.three = three;
		}

		public String getOne() {
			return one;
		}

		public void setOne(String one) {
			this.one = one;
		}

		public String getTwo() {
			return two;
		}

		public void setTwo(String two) {
			this.two = two;
		}

		public String getThree() {
			return three;
		}

		public void setThree(String three) {
			this.three = three;
		}
		
	}
	
	public class Example2 {
		String one, two, three;
		
		public Example2(String one, String two, String three) {
			super();
			this.one = one;
			this.two = two;
			this.three = three;
		}
		
		@SortColumn("one")
		public String getOne() {
			return one;
		}
		
		public void setOne(String one) {
			this.one = one;
		}
		
		@SortColumn
		public String getTwo() {
			return two;
		}
		
		public void setTwo(String two) {
			this.two = two;
		}
		
		public String getThree() {
			return three;
		}
		
		public void setThree(String three) {
			this.three = three;
		}
	}

	
	@Test
	public void getSortMethods() {
		Collection<String> sortMethods = SortUtils.getSortMethods(Example1.class);
		assertEquals(3, sortMethods.size());
		
		sortMethods = SortUtils.getSortMethods(Example2.class);
		assertEquals(2, sortMethods.size());
		assertTrue( sortMethods.contains("one") );
		assertTrue( sortMethods.contains("getTwo") );
	}

	@Test
	public void sort() {
		List<Example1> list = Arrays.asList(new Example1("a", "a", "a"), new Example1("c", "c", "c"), new Example1("b", "b", "b"));
		
		SortUtils.sort("getOne", list);
		assertEquals("a", list.get(0).one);
		assertEquals("b", list.get(1).one);
		assertEquals("c", list.get(2).one);
	}

}
