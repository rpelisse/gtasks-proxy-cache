package org.belaran.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

@Singleton
public class TagController {

    private Map<String,String> tagsIndexedByName = new HashMap<String, String>(3);

    public TagController() {
        tagsIndexedByName.put("phone", "📞");
        tagsIndexedByName.put("dollar", "💲");
        tagsIndexedByName.put("blocker", "⛔");
        tagsIndexedByName.put("rpg","🎲");
        tagsIndexedByName.put("email", "✉️");
        tagsIndexedByName.put("cat", "🐈");
        tagsIndexedByName.put("music", "🎶");
        tagsIndexedByName.put("house", "🏠");
        tagsIndexedByName.put("print", "📄");
        tagsIndexedByName.put("food", "🍆");
        tagsIndexedByName.put("medics","💊");
        tagsIndexedByName.put("write","✏️");
        tagsIndexedByName.put("drums","🥁");
        tagsIndexedByName.put("clock","⌚");
    }

    public Map<String, String> getTagsIndexedByName() {
        return tagsIndexedByName;
    }

    public List<String> returnAllTagsIndexedByEmoticon() {
        return tagsIndexedByName.entrySet().stream()
                    .map(entry -> entry.getValue() + "\t-> " + entry.getKey())
                    .collect(Collectors.toList());
    }

    public static String tagTaskTitle(String symbol, String title) {
        return symbol + " " + title;
    }

    public String getSymbolForTag(String tag) {
        return tagsIndexedByName.get(getKeyAssociatedToTagId(tag));
    }

    public String getKeyAssociatedToTagId(String tag) {
        return tagsIndexedByName.keySet().stream().filter(key -> key.startsWith(tag)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No tag associated to :" + tag));
    }
}
