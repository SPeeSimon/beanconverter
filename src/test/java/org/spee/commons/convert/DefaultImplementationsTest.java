package org.spee.commons.convert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class DefaultImplementationsTest {

	@Test
	public void testGetImplementationFor() {
		Assert.assertNotNull( DefaultImplementations.getImplementationFor(Collection.class) );
		Assert.assertNotNull( DefaultImplementations.getImplementationFor(Map.class) );
		Assert.assertNotNull( DefaultImplementations.getImplementationFor(Set.class) );
	}

	@Test(expected=IllegalArgumentException.class)
	public void testGetImplementationFor_class() {
		Assert.assertNotNull( DefaultImplementations.getImplementationFor(ArrayList.class) );
	}

}
