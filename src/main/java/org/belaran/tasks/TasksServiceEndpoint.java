package org.belaran.tasks;

import static org.belaran.tasks.util.DateUtils.today;
import static org.belaran.tasks.util.DateUtils.tomorrow;

import jakarta.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.belaran.tasks.util.DateUtils;
import org.belaran.tasks.util.FormatUtils;
import org.belaran.tasks.util.StringUtils;
import org.belaran.tasks.util.TaskUtils;
import org.belaran.tasks.util.URLUtils;

import com.google.api.client.util.DateTime;
import com.google.api.services.tasks.model.Task;

@Path("/tasks")
public class TasksServiceEndpoint {

	@Inject
	GTasksServiceClient gtasksClient;

	@Inject
	TasksCache tasks;

	@Inject
	TagController tagController;

	private final static Logger LOGGER = Logger.getLogger(TasksServiceEndpoint.class.getName());

	private static final String TASK_PID_FILE_ENV_VAR_NAME = "TASKSD_PIDFILE";
	private final static String PID_FILE_NAME = System.getenv(TASK_PID_FILE_ENV_VAR_NAME);

	public TasksServiceEndpoint() throws IOException {
		LOGGER.info("PID Filename:" + PID_FILE_NAME);
		Files.write(Paths.get(PID_FILE_NAME), String.valueOf(ProcessHandle.current().pid()).getBytes());
		LOGGER.info("PID has been stored into " + PID_FILE_NAME);
	}

	@PostConstruct
	void serviceInit() throws IOException, GeneralSecurityException {
		this.refresh();
	}

	private boolean refresh() throws IOException {
		if ( ! tasks.getTasks().isEmpty() )
			tasks.getTasks().clear();
		LOGGER.info("Local cache for tasks is being refreshed.");
		tasks.getTasks().putAll(gtasksClient.fetchAllItemsOfDefaultList());
		ifListStillEmptySomethingWentWrongTryAgain();
		return true;
	}

	private void ifListStillEmptySomethingWentWrongTryAgain() throws IOException {
		if ( tasks.getTasks().isEmpty() ) {
			LOGGER.info("List empty after refreshing, trying again.");
			tasks.getTasks().putAll(gtasksClient.fetchAllItemsOfDefaultList());
		}
	}

	private static final ExecutorService executor = Executors.newSingleThreadExecutor();
	private void asyncRefresh() throws IOException {
		executor.submit(() -> { return refresh(); });
	}

	private String insertTaskAsync(Task task) throws IOException {
		return asyncRefresh(gtasksClient.insertTask(task));
	}

	private void asyncBump(String id, int nbDays) throws IOException {
		gtasksClient.bumpTaskById(id, nbDays);
		asyncRefresh();
	}

	@PUT
	@Path("/{title}")
	@Produces(MediaType.TEXT_PLAIN)
	public String addTasks(@PathParam(value = "title") String title) throws FileNotFoundException, IOException {
		return insertTaskAsync(TaskUtils.buildTask(title, "", today()));
	}

	@PUT
	@Path("/add/from/url/")
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.TEXT_PLAIN)
	public String addTasksAsUrl(String urlAsString) throws FileNotFoundException, IOException {
		return asyncRefresh(insertTaskAsync(TaskUtils.insertURLTask(URLUtils.stringToURL(urlAsString), today())));
	}

	@PUT
	@Path("/{title}/{description}")
	@Produces(MediaType.TEXT_PLAIN)
	public String addTasksWithNotes(@PathParam(value = "title") String title,
			@PathParam(value = "description") String description) throws FileNotFoundException, IOException {
		return insertTaskAsync(TaskUtils.buildTask(title, description, today()));
	}

	@POST
	@Path("/notes/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.TEXT_PLAIN)
	public String addNotesToTask(@PathParam(value = "id") String id, String notes) throws FileNotFoundException, IOException {
		Task task = gtasksClient.retrieveTaskById(id);
		if ( task == null )
			throw new IllegalArgumentException("No tasks associated to id:" + id);
		if (task.getNotes() == null || ! task.getNotes().isBlank() )
			notes = FormatUtils.appendNotes(task.getNotes(), notes);
		task.setNotes(notes);
		return asyncRefresh(gtasksClient.updateTask(task).getId());
	}

	@POST
	@Path("/rename/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.TEXT_PLAIN)
	public String renameTask(@PathParam(value = "id") String id, String title) throws FileNotFoundException, IOException {
		Task task = gtasksClient.retrieveTaskById(id);
		if ( task == null )
			throw new IllegalArgumentException("No tasks associated to id:" + id);
		task.setTitle(title);
		return asyncRefresh(gtasksClient.updateTask(task).getId());
	}

	private <T> T asyncRefresh(T object) throws IOException {
		asyncRefresh();
		return object;
	}

	@POST
	@Path("/tag/{id}/{tag}")
	@Produces(MediaType.TEXT_PLAIN)
	public void tagTask(@PathParam(value = "id") String id, @PathParam(value = "tag") String tag) throws IOException, GeneralSecurityException {
		gtasksClient.tagAndUpdateTask(tagController.getSymbolForTag(tag),gtasksClient.retrieveTaskById(id));
		asyncRefresh();
	}

	@GET
	@Path("/tag/list")
	@Produces(MediaType.TEXT_PLAIN)
	public String supportedTagList() throws IOException, GeneralSecurityException {
		return tagController.getTagsIndexedByName().toString();
	}

	@POST
	@Path("/refresh")
	@Produces(MediaType.TEXT_PLAIN)
	public void httpRefresh() throws IOException, GeneralSecurityException {
		asyncRefresh();
	}

	@POST
	@Path("/refresh/sync")
	@Produces(MediaType.TEXT_PLAIN)
	public void httpSyncRefresh() throws IOException, GeneralSecurityException {
		refresh();
	}

	@GET
	@Path("/list/today")
	@Produces(MediaType.TEXT_PLAIN)
	public String todayList() throws IOException, GeneralSecurityException {
		final DateTime today = today();
		return FormatUtils.formatTaskList(tasks.getTasks().values(), (t -> {
			return DateUtils.isSameDay(today, t.getDue());
		}), today);
	}

	@GET
	@Path("/list/overdue")
	@Produces(MediaType.TEXT_PLAIN)
	public String overdueList() throws IOException, GeneralSecurityException {
		return FormatUtils.formatOverdueTaskList(tasks.getTasks().values(), (t -> {
			return DateUtils.isDueDateBefore(today(), t.getDue());
		}));
	}

	@GET
	@Path("/list/tomorrow")
	@Produces(MediaType.TEXT_PLAIN)
	public String list() throws IOException, GeneralSecurityException {
		final DateTime tomorrow = tomorrow();
		return FormatUtils.formatTaskList(tasks.getTasks().values(),(t -> {
			return DateUtils.isSameDay(tomorrow, t.getDue());
		}), tomorrow);
	}

	@GET
	@Path("/list/on/{date}")
	@Produces(MediaType.TEXT_PLAIN)
	public String listOnDay(@PathParam(value = "date") String date) throws IOException, GeneralSecurityException {
        // 2019-10-12T00:00:00.00Z
        // 2019-10-12T07:20:50.52Z
		final DateTime dueDate = DateTime.parseRfc3339(date + "T00:00:00.00Z");
        LOGGER.info("List tasks on:" + dueDate);
		return FormatUtils.formatTaskList(tasks.getTasks().values(),(t -> {
			return DateUtils.isSameDay(dueDate, t.getDue());
		}), dueDate);
	}

	@GET
	@Path("/search/title/{pattern}")
	@Produces(MediaType.TEXT_PLAIN)
	public String search(@PathParam(value = "pattern") String pattern) {
			return FormatUtils.formatSearchResultList(tasks.getTasks().values(), new Predicate<Task>() {
			@Override
			public boolean test(Task t) {
				return t.getTitle().toLowerCase().contains(pattern.toLowerCase());
			}
		});
	}

	@GET
	@Path("/search/notes/{pattern}")
	@Produces(MediaType.TEXT_PLAIN)
	public String searchInNotes(@PathParam(value = "pattern") String pattern) {
		return FormatUtils.formatTaskList(tasks.getTasks().values(), new Predicate<Task>() {
			@Override
			public boolean test(Task t) {
				return StringUtils.isStringNull(t.getNotes()).toLowerCase().contains(pattern.toLowerCase());
			}
		}, today());
	}

	@DELETE
	@Path("/delete/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public void deleteTask(@PathParam(value = "id") String taskId) throws IOException {
		System.out.println("Delete called with :" + taskId);
		gtasksClient.deleteTask(taskId);
		asyncRefresh();
	}

	@POST
	@Path("/bump/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public void bump(@PathParam(value = "id") String id) throws IOException {
		asyncBump(id, 1);
	}

	@POST
	@Path("/bump/to/{id}/{nbDays}")
	@Produces(MediaType.TEXT_PLAIN)
	public void bumpTo(@PathParam(value = "id") String id, @PathParam(value = "nbDays") int nbDays) throws IOException {
		asyncBump(id, nbDays);
	}

	@GET
	@Path("/desc/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public String fetchTasksDesc(@PathParam(value = "id") String id) throws IOException {
		if ( tasks.getTasks().isEmpty() )
			refresh();
		if ( tasks.getTasks().containsKey(id)) {
			return FormatUtils.formatTaskWithNotes(tasks.getTasks().get(id));
		}
		throw new IllegalArgumentException("No tasks associated to ID: " + id);
	}

	@GET
	@Path("/pid")
	@Produces(MediaType.TEXT_PLAIN)
	public String pid() {
		return String.valueOf(ProcessHandle.current().pid());
	}
}
