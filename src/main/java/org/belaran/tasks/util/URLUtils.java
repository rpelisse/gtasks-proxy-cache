package org.belaran.tasks.util;

import java.net.MalformedURLException;
import java.net.URL;

public final class URLUtils {

	private URLUtils() {}
	
	public static String getLastSegmentOfURLPath(URL url) {
		return url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
	}
	
	public static URL stringToURL(String urlAsString) {
		try {
			return new URL(urlAsString);
		} catch ( MalformedURLException e ) {
			throw new IllegalArgumentException("Invalid URL:" + urlAsString);
		}
	}

}
