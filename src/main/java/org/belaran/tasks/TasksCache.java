package org.belaran.tasks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Singleton;

import com.google.api.services.tasks.model.Task;

@Singleton
public class TasksCache {

	private final Map<String, Task> tasks = new ConcurrentHashMap<String, Task>();

	public TasksCache() {

	}

	public Map<String, Task> getTasks() {
		return tasks;
	}

}
