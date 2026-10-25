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
import java.util.Comparator;
import java.util.List;

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
import jakarta.validation.constraints.NotBlank;

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
	public String addTasks(@NotBlank @PathParam(value = "title") String title) throws FileNotFoundException, IOException {
		return insertTaskAsync(TaskUtils.buildTask(title, "", today()));
	}

	@PUT
	@Path("/add/from/url/")
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.TEXT_PLAIN)
	public String addTasksAsUrl(@NotBlank String urlAsString) throws FileNotFoundException, IOException {
		return asyncRefresh(insertTaskAsync(TaskUtils.insertURLTask(URLUtils.stringToURI(urlAsString), today())));
	}

	@PUT
	@Path("/{title}/{description}")
	@Produces(MediaType.TEXT_PLAIN)
	public String addTasksWithNotes(@NotBlank @PathParam(value = "title") String title,
		                            @NotBlank @PathParam(value = "description") String description) throws FileNotFoundException, IOException {
		return insertTaskAsync(TaskUtils.buildTask(title, description, today()));
	}

	@POST
	@Path("/notes/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.TEXT_PLAIN)
	public String addNotesToTask(@NotBlank @PathParam(value = "id") String id, @NotBlank String notes) throws FileNotFoundException, IOException {
		var task = gtasksClient.retrieveTaskById(id);
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
	public String renameTask(@NotBlank @PathParam(value = "id") String id, @NotBlank String title) throws FileNotFoundException, IOException {
		var task = gtasksClient.retrieveTaskById(id);
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
	public String tagTask(@NotBlank @PathParam(value = "id") String id,
                        @NotBlank @PathParam(value = "tag") String tag) throws IOException, GeneralSecurityException {
		return refreshAndReturnInfoMessage(gtasksClient.tagAndUpdateTask(tagController.getSymbolForTag(tag), gtasksClient.retrieveTaskById(id)));
	}

	@DELETE
	@Path("/tag/{id}/{tag}")
	@Produces(MediaType.TEXT_PLAIN)
	public String removeTagFromTask(@NotBlank @PathParam(value = "id") String id,
                        @NotBlank @PathParam(value = "tag") String tag) throws IOException, GeneralSecurityException {
		return refreshAndReturnInfoMessage(gtasksClient.removeTagAndUpdateTask(tagController.getSymbolForTag(tag), gtasksClient.retrieveTaskById(id)));
	}

    @DELETE
    @Path("/tag/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public String removeAllTagsFromTask(@NotBlank @PathParam(value = "id") String id) throws IOException, GeneralSecurityException {
        return refreshAndReturnInfoMessage(gtasksClient.removeAllTagsAndUpdateTask(gtasksClient.retrieveTaskById(id), tagController.returnAllTagsEmoticons()));
    }

	private String refreshAndReturnInfoMessage(Task task) throws IOException, GeneralSecurityException {
            asyncRefresh();
	    return "Tagged task '" + task.getId() + "' - '" + task.getTitle() + "'.";
	}

	@GET
	@Path("/tag/list")
	@Produces(MediaType.TEXT_PLAIN)
	public String supportedTagList() throws IOException, GeneralSecurityException {
		return tagController.returnAllTagsIndexedByEmoticon().toString().replaceAll("\\[", "").replaceAll("\\]", "");
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
        var today = today();
        return FormatUtils.formatTaskList(sortTasksOfDay(today), (t -> true), today);
    }

    final List<String> priorityEmojis = List.of("📞","✉️", "💊", "⛔");
    final Comparator<Task> emojiPriorityComparator = Comparator.comparingInt(t -> {
        String title = t.getTitle();
        for (int i = 0; i < priorityEmojis.size(); i++)
            if (title.startsWith(priorityEmojis.get(i)))
                   return i;
        return priorityEmojis.size();
    });

    private List<Task> sortTasksOfDay(DateTime date) {
        return tasks.getTasks().values().stream()
        .filter(t -> DateUtils.isSameDay(date, t.getDue()))
        .sorted(emojiPriorityComparator)  //Comparator.comparing(Task::getTitle))
        .toList();
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
	    final var tomorrow = tomorrow();
        return FormatUtils.formatTaskList(sortTasksOfDay(tomorrow), (t -> true), tomorrow);
	}

	@GET
	@Path("/list/on/{date}")
	@Produces(MediaType.TEXT_PLAIN)
	public String listOnDay(@NotBlank @PathParam(value = "date") String date) throws IOException, GeneralSecurityException {
		final var dueDate = DateTime.parseRfc3339(date + "T00:00:00.00Z");
        return FormatUtils.formatTaskList(sortTasksOfDay(dueDate), (t -> true), dueDate);
	}

	@GET
	@Path("/search/title/{pattern}")
	@Produces(MediaType.TEXT_PLAIN)
	public String search(@NotBlank @PathParam(value = "pattern") String pattern) {
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
	public String searchInNotes(@NotBlank @PathParam(value = "pattern") String pattern) {
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
	public String deleteTask(@NotBlank @PathParam(value = "id") String taskId) throws IOException {
		LOGGER.info("Delete called with :" + taskId);
                var returnMessage = FormatUtils.formatReturnMessage(gtasksClient.retrieveTaskById(taskId), "deleted");
                gtasksClient.deleteTask(taskId);
		asyncRefresh();
                return returnMessage;
	}

	@POST
	@Path("/bump/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public void bump(@NotBlank @PathParam(value = "id") String id) throws IOException {
		asyncBump(id, 1);
	}

	@POST
	@Path("/bump/to/{id}/{nbDays}")
	@Produces(MediaType.TEXT_PLAIN)
	public String bumpTo(@NotBlank @PathParam(value = "id") String id, @PathParam(value = "nbDays") int nbDays) throws IOException {
                asyncBump(id, nbDays);
                var task = gtasksClient.retrieveTaskById(id);
                return FormatUtils.formatReturnMessage(task, "bumped by " + accord(nbDays) + " to " + DateUtils.formatDate(task.getDue()));
	}

        private String accord(int nbDays) {
                return ( nbDays == 1 ) ? nbDays + " day" : nbDays + " days";
        }

	@GET
	@Path("/desc/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public String fetchTasksDesc(@NotBlank @PathParam(value = "id") String id) throws IOException {
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

	@GET
	@Path("/version")
	@Produces(MediaType.TEXT_PLAIN)
	public String version() {
		return "GTasks Proxy Cache Version: " + GtasksVersion.VERSION + " (Powered by Quarkus v" + GtasksVersion.QUARKUS_VERSION + " and using GTasks client API: " + GtasksVersion.GTASKS_VERSION + ")";
	}
}
