package org.belaran.tasks.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

import org.jsoup.Jsoup;

import com.google.api.client.util.DateTime;
import com.google.api.services.tasks.model.Task;

public final class TaskUtils {
	
	private TaskUtils() {
		
	}
	
	public static Task insertURLTask(String taskURL, DateTime date) throws FileNotFoundException, IOException {
		var title = Jsoup.connect(taskURL).get().title();
		if ( title.startsWith("Login server redirect"))
			throw new IOException("Can't add URL due to authentification");
		return buildTask(title, taskURL, date);
	}

	public static Task insertURLTask(URL taskURL, DateTime date) throws FileNotFoundException, IOException {
		try {
			return insertURLTask(taskURL.toString(), date);
		} catch ( IOException e ) {
			return buildTask(URLUtils.getLastSegmentOfURLPath(taskURL), taskURL.toString(), date);
		}
	}

	public static Task updateTaskTitle(Task task, String newTitle) {
		task.setTitle(newTitle);
		return task;
	}

	
	public static Task buildTask(String title, String description, DateTime dueDate) {
		var task = new com.google.api.services.tasks.model.Task();
		task.setTitle(title);
		task.setNotes(description);
		task.setDue(dueDate);
		return task;
	}
	
	public static Task pushDueDateTo(Task task, int nbDays) {
		long NB_SECONDS_BY_DAY = 86400L * 1000;
		task.setDue(new DateTime(task.getDue().getValue() + (nbDays * NB_SECONDS_BY_DAY)));
		return task;
	}
}
