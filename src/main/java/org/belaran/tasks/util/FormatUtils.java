package org.belaran.tasks.util;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.api.client.util.DateTime;
import com.google.api.services.tasks.model.Task;

public final class FormatUtils {

	private static final String EOL = "\n";

	private FormatUtils() {

	}

	public static String formatTaskWithNotes(Task t) {
		return "[" + t.getId() + "] " + t.getTitle() + EOL + t.getNotes() + EOL;
	}

	public static String formatTaskList(Collection<Task> tasks, Predicate<Task> predicate, DateTime date) {
		return formatTaskList(tasks, "Tasks due on " + DateUtils.formatDate(date) + ":" + EOL + EOL, predicate);
	}

	public static String formatOverdueTaskList(Collection<Task> tasks, Predicate<Task> predicate) {
		return formatTaskList(tasks, "Tasks overdue:" + EOL + EOL, predicate);
	}

	public static String formatSearchResultList(Collection<Task> tasks, Predicate<Task> predicate) {
		return formatTaskList(tasks, "Results:" + EOL + EOL, predicate, (Task t) -> {
			return "[" + t.getId() + "] " + t.getTitle() + ", due on " + DateUtils.formatDate(t.getDue()) + EOL;
		}, "");
	}

	private static String formatTaskWithNumber(AtomicInteger counter, Task t, TaskFormatter taskFormatterFunction) {
		return counter.getAndIncrement() + ") " + taskFormatterFunction.formatTasks(t);
	}

	private static String selectTasksToDisplay(Collection<Task> tasks, Predicate<Task> predicate,
			AtomicInteger counter) {
		return selectTasksToDisplay(tasks, predicate, counter, (Task task) -> {
			return "[" + task.getId() + "] " + task.getTitle();
		});
	}

	private static String selectTasksToDisplay(Collection<Task> tasks, Predicate<Task> predicate, AtomicInteger counter,
			TaskFormatter taskFormatterFunction) {
		return tasks.stream().filter(t -> predicate.test(t))
				.map(t -> formatTaskWithNumber(counter, t, taskFormatterFunction)).collect(Collectors.joining(EOL));
	}

	private static String formatTaskList(Collection<Task> tasks, String header, Predicate<Task> predicate) {
		return formatTaskList(tasks, header, predicate, EOL);
	}

	private static String formatTaskList(Collection<Task> tasks, String header, Predicate<Task> predicate,
			String footer) {
		return header + selectTasksToDisplay(tasks, predicate, new AtomicInteger(1)) + footer;
	}

	private static String formatTaskList(Collection<Task> tasks, String header, Predicate<Task> predicate,
			TaskFormatter taskFormatterFunction, String footer) {
		return header + selectTasksToDisplay(tasks, predicate, new AtomicInteger(1), taskFormatterFunction) + footer;
	}

	interface TaskFormatter {
		public String formatTasks(Task t);
	}

	class DefaultTaskFormatter implements TaskFormatter {

		@Override
		public String formatTasks(Task t) {
			return "[" + t.getId() + "] " + t.getTitle();
		}

	}

	public static String appendNotes(String currentNotes, String notesToAppend) {
		return currentNotes + EOL + notesToAppend;
	}

    public static String formatReturnMessage(Task task, String message) {
        return "Task (" + task.getId() + "): '" + task.getTitle() + "' has been " + message;
    }
}
