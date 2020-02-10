package org.belaran.tasks.util;

public final class StringUtils {

	private StringUtils() {
		
	}

	public static String isStringNull(String string) {
		return string == null ? "" : string;
	}

}
