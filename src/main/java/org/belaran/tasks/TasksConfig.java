package org.belaran.tasks;

import io.smallrye.config.ConfigMapping;
import java.util.Map;

@ConfigMapping(prefix = "tasks")
public interface TasksConfig {
    Map<String, String> tags();
}
