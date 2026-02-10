package org.belaran.tasks.util;

import java.net.URI;
import java.net.URISyntaxException;

public final class URLUtils {

	private URLUtils() {}
	
	public static String getLastSegmentOfURLPath(URI uri) {
                return uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
	}
	
	public static URI stringToURI(String uriAsString) {
		try {
			return new URI(uriAsString);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid URI: " + uriAsString, e);
		}
	}
}
