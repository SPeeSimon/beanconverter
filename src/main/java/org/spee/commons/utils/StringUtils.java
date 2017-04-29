package org.spee.commons.utils;

import java.util.Comparator;

public class StringUtils {


	/**
	 * Return a {@link Comparator} that compares {@link String}s either case sensitive or case insensitive.
	 * @param ignoreCasing <code>true</code> casing is ignored, <code>false</code> casing is used to calculate the difference.
	 * @return
	 */
	public static Comparator<String> stringComparer(boolean ignoreCasing){
		if( ignoreCasing ){
			return new Comparator<String>() {
				@Override
				public int compare(final String o1, final String o2) {
					return o1 == null ? (o2 == null ? 0 : 1) : o1.compareToIgnoreCase(o2);
				}
			};
		}else{
			return new Comparator<String>() {
				@Override
				public int compare(final String o1, final String o2) {
					return o1 == null ? (o2 == null ? 0 : 1) : o1.compareTo(o2);
				}
			};
		}
		
	}
	
	
}
