package org.belaran.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

@Singleton
public class TagController {

    private Map<String,String> tagsIndexedByName;

    @Inject
    TasksConfig config;

    public TagController() {}

    @PostConstruct
    public void loadTags() {
        tagsIndexedByName = config.tags();
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
