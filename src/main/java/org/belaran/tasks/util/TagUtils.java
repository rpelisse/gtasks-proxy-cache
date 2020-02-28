package org.belaran.tasks.util;

import java.util.HashMap;
import java.util.Map;

public final class TagUtils {

	public static Map<String,String> TAGS_INDEXED_BY_LETTER_ID = new HashMap<String, String>(3);

	{
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("phone", "☎️");
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("dollar", "💲");
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("blocker", "⛔");
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("rpg","🎲");
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("email", "✉️");
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("cat", "🐹");
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("music", "🎶");
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("house", "🏠");
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("print", "📄");
		TagUtils.TAGS_INDEXED_BY_LETTER_ID.put("food", "🍆");
	}

	private TagUtils() {

	}

	public static String tagTaskTitle(String symbol, String title) {
		return symbol + " " + title;
	}

	public static String getSymbolForTag(String tag) {
		return TagUtils.TAGS_INDEXED_BY_LETTER_ID.get(getKeyAssociatedToTagId(tag));
	}

	public static String getKeyAssociatedToTagId(String tag) {
		return TagUtils.TAGS_INDEXED_BY_LETTER_ID.keySet().stream().filter(key -> key.startsWith(tag)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No tag associated to :" + tag));
	}
}
