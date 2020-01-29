package org.belaran.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.Task;

@Path("/tasks")
public class TasksService {

	private final static Logger LOGGER = Logger.getLogger(TasksService.class.getName());

	private static final String APPLICATION_NAME = "Google Tasks API Java Quickstart";
	private static final String TASK_CLIENT_SECRET_FILE_ENV_VAR_NAME = "TASKS_CLIENT_SECRET";
	private static final String TASK_PID_FILE_ENV_VAR_NAME = "TASKSD_PIDFILE";

	private final File DATA_STORE_DIR = new File(System.getProperty("user.home"), ".credentials/tasks-java-quickstart");
	private final static String CLIENT_SECRET_FILENAME = System.getenv(TASK_CLIENT_SECRET_FILE_ENV_VAR_NAME);
	private final static String PID_FILE_NAME = System.getenv(TASK_PID_FILE_ENV_VAR_NAME);
	private final static JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private final static List<String> SCOPES = Arrays.asList(TasksScopes.TASKS);
	private static final long ONE_DAY__IN_MILLIS = 86400000;

	private static final String MAIN_TASK_LIST_ID = "@default";
	private static final String EOL = "\n";

	private static final ExecutorService executor = Executors.newSingleThreadExecutor();

	private static final Map<String, Task> tasks = new ConcurrentHashMap<String, Task>();

	private static Map<String,String> TAGS_INDEXED_BY_LETTER_ID = new HashMap<String, String>(3);

	private FileDataStoreFactory dataStoreFactory;
	private NetHttpTransport httpTransport;

	private static DateTime today() {
		return new DateTime(System.currentTimeMillis());
	}

	private static DateTime tomorrow() {
		return new DateTime(System.currentTimeMillis() + ONE_DAY__IN_MILLIS);
	}

	public TasksService() throws IOException {
		LOGGER.info("PID Filename:" + PID_FILE_NAME);
		Files.write(Paths.get(PID_FILE_NAME), String.valueOf(ProcessHandle.current().pid()).getBytes());
		LOGGER.info("PID has been stored into " + PID_FILE_NAME);
	}

	private void checkClientSecretFilename() {
		if (!"".equals(CLIENT_SECRET_FILENAME))
			LOGGER.info("Client Secret stored in " + CLIENT_SECRET_FILENAME);
		else
			throw new IllegalStateException("Path to certificate file not provided.");

		if (!new java.io.File(CLIENT_SECRET_FILENAME).exists())
			throw new IllegalStateException("Certificate file does not exists: " + CLIENT_SECRET_FILENAME);
	}

	private GoogleClientSecrets openGoogleClientSecrets() throws IOException {
		try {
			return GoogleClientSecrets.load(JSON_FACTORY,
					new InputStreamReader(new FileInputStream(CLIENT_SECRET_FILENAME)));

		} catch (FileNotFoundException fileNotFoundException) {
			throw new IllegalStateException(fileNotFoundException);
		}
	}

	@PostConstruct
	void serviceInit() throws IOException, GeneralSecurityException {
		checkClientSecretFilename();

		dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
		httpTransport = GoogleNetHttpTransport.newTrustedTransport();

		populateTagsIndexedMap();

		this.refresh();
	}

	private void populateTagsIndexedMap() {
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("phone", "☎️");
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("dollar", "💲");
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("blocker", "⛔");
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("rpg","🎲");
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("email", "✉️");
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("cat", "🐹");
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("music", "🎶");
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("house", "🏠");
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("print", "📄");
		TasksService.TAGS_INDEXED_BY_LETTER_ID.put("food", "🍆");
	}

	private boolean refresh() throws IOException {
		if ( ! tasks.isEmpty() )
			tasks.clear();
		LOGGER.info("Local cache for tasks is being refreshed.");
		fetchAllItemsOfDefaultList(getService());
		ifListStillEmptySomethingWentWrongTryAgain();
		return true;
	}

	private void ifListStillEmptySomethingWentWrongTryAgain() throws IOException {
		if ( tasks.isEmpty() ) {
			LOGGER.info("List empty after refreshing, trying again.");
			fetchAllItemsOfDefaultList(getService());
		}
	}

	private void fetchAllItemsOfDefaultList(Tasks service) throws IOException {
		com.google.api.services.tasks.model.Tasks gtasks = null;
		String token = null;
		int nbItems = 0;
		do {
			gtasks = service.tasks().list(MAIN_TASK_LIST_ID).setMaxResults(100l).setPageToken(token).execute();
			if (gtasks != null && gtasks.getItems() != null) {
				for (Task t : gtasks.getItems()) {
					LOGGER.fine("Adding to local cache:" + t.getTitle());
					tasks.put(t.getId(), t);
				}
				nbItems += gtasks.getItems().size();
				token = gtasks.getNextPageToken();
			} else
				LOGGER.warning("Failed to retrieve any tasks!");
		} while (token != null);
		LOGGER.info("Cache refreshed with " + nbItems + " items fetched. (in cache: " + tasks.size() + " ).");
		LOGGER.info("Due Date:" + today().toStringRfc3339());
	}

	private boolean asyncRefresh() throws IOException {
		executor.submit(new Callable<Boolean>() {

			@Override
			public Boolean call() throws Exception {
				return refresh();
			}
		});
		return true;
	}

	private static Task buildTask(String title, String description, DateTime dueDate) {
		Task task = new com.google.api.services.tasks.model.Task();
		task.setTitle(title);
		task.setNotes(description);
		task.setDue(dueDate);
		return task;
	}

	private String insertTask(Task task) throws IOException {
		return asyncRefresh(getService().tasks().insert(MAIN_TASK_LIST_ID, task).execute().getId());
	}

	private Optional<Tasks> taskService = Optional.empty();
	private Tasks getService() throws IOException {
		if ( taskService.isEmpty() ) {
			GoogleClientSecrets clientSecrets = openGoogleClientSecrets();
			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY,
					clientSecrets, SCOPES).setDataStoreFactory(dataStoreFactory).setAccessType("offline").build();
			Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
			taskService = Optional.of(new Tasks.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build());
		}
		return taskService.get();
	}

	private void bump(String id, int nbDays) throws IOException {
		Task task = retrieveTaskById(id);
		getService().tasks().update(MAIN_TASK_LIST_ID, task.getId(), pushDueDateTo(task, nbDays)).execute();
		asyncRefresh();
	}

	private Task pushDueDateTo(Task task, int nbDays) {
		long NB_SECONDS_BY_DAY = 86400L * 1000;
		task.setDue(new DateTime(task.getDue().getValue() + (nbDays * NB_SECONDS_BY_DAY)));
		return task;
	}

	@PUT
	@Path("/{title}")
	@Produces(MediaType.TEXT_PLAIN)
	public String addTasks(@PathParam(value = "title") String title) throws FileNotFoundException, IOException {
		return insertTask(buildTask(title, "", today()));
	}

	private String getLastSegmentOfURLPath(URL url) {
		return url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
	}

	private String insertURLTask(URL taskURL) throws FileNotFoundException, IOException {
		try {
			String title = Jsoup.connect(taskURL.toString()).get().title();
			if ( title.startsWith("Login server redirect"))
				throw new IOException("Can't add URL due to authentification");
			return insertTask(buildTask(title, taskURL.toString(), today()));
		} catch ( IOException e ) {
			return insertTask(buildTask(getLastSegmentOfURLPath(taskURL), taskURL.toString(), today()));
		}
	}

	private URL turnTitleIntoAnURL(String urlAsString) {
		try {
			return new URL(urlAsString);
		} catch ( MalformedURLException e ) {
			throw new IllegalArgumentException("Invalid URL:" + urlAsString);
		}
	}

	@PUT
	@Path("/add/from/url/")
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.TEXT_PLAIN)
	public String addTasksAsUrl(String urlAsString) throws FileNotFoundException, IOException {
		return asyncRefresh(insertURLTask(turnTitleIntoAnURL(urlAsString)));
	}


	@PUT
	@Path("/{title}/{description}")
	@Produces(MediaType.TEXT_PLAIN)
	public String addTasksWithNotes(@PathParam(value = "title") String title,
			@PathParam(value = "description") String description) throws FileNotFoundException, IOException {
		return insertTask(buildTask(title, description, today()));
	}

	@POST
	@Path("/notes/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.TEXT_PLAIN)
	public String addNotesToTask(@PathParam(value = "id") String id, String notes) throws FileNotFoundException, IOException {
		Task task = retrieveTaskById(id);
		if ( task == null )
			throw new IllegalArgumentException("No tasks associated to id:" + id);
		if (task.getNotes() == null || ! task.getNotes().isBlank() )
			notes = task.getNotes() + EOL + notes;
		task.setNotes(notes);
		return asyncRefresh(updateTask(task).getId());
	}


	@POST
	@Path("/rename/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.TEXT_PLAIN)
	public String renameTask(@PathParam(value = "id") String id, String title) throws FileNotFoundException, IOException {
		Task task = retrieveTaskById(id);
		if ( task == null )
			throw new IllegalArgumentException("No tasks associated to id:" + id);
		task.setTitle(title);
		return asyncRefresh(updateTask(task).getId());
	}

	private <T> T asyncRefresh(T object) throws IOException {
		asyncRefresh();
		return object;
	}

	@POST
	@Path("/tag/{id}/{tag}")
	@Produces(MediaType.TEXT_PLAIN)
	public void tagTask(@PathParam(value = "id") String id, @PathParam(value = "tag") String tag) throws IOException, GeneralSecurityException {
		tagAndUpdateTask(getSymbolForTag(tag),retrieveTaskById(id));
		asyncRefresh();
	}

	@GET
	@Path("/tag/list")
	@Produces(MediaType.TEXT_PLAIN)
	public String supportedTagList() throws IOException, GeneralSecurityException {
		return TasksService.TAGS_INDEXED_BY_LETTER_ID.toString();
	}

	private void tagAndUpdateTask(String symbol, Task task) throws IOException {
		getService().tasks()
				.update(MAIN_TASK_LIST_ID, task.getId(), updateTaskTitle(task, tagTaskTitle(symbol, task.getTitle())))
				.execute();
	}

	private Task updateTask(Task task) throws IOException {
		return getService().tasks()
				.update(MAIN_TASK_LIST_ID, task.getId(), task)
				.execute();
	}

	private static Task updateTaskTitle(Task task, String newTitle) {
		task.setTitle(newTitle);
		return task;
	}
	
	private static String tagTaskTitle(String symbol, String title) {
		return symbol + " " + title;
	}

	private String getSymbolForTag(String tag) {
		return TasksService.TAGS_INDEXED_BY_LETTER_ID.get(getKeyAssociatedToTagId(tag));
	}

	private String getKeyAssociatedToTagId(String tag) {
		return TasksService.TAGS_INDEXED_BY_LETTER_ID.keySet().stream().filter(key -> key.startsWith(tag)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No tag associated to :" + tag));
	}

	private Task retrieveTaskById(String id) throws IOException {
		return Optional.of(getService().tasks().get(MAIN_TASK_LIST_ID, id).execute()).orElseThrow(() -> new IllegalArgumentException("No task associated to id " + id));
	}

	@POST
	@Path("/refresh")
	@Produces(MediaType.TEXT_PLAIN)
	public void httpRefresh() throws IOException, GeneralSecurityException {
		asyncRefresh();
	}

	@GET
	@Path("/list/today")
	@Produces(MediaType.TEXT_PLAIN)
	public String todayList() throws IOException, GeneralSecurityException {
		final DateTime today = today();
		return formatTaskList((t -> {
			return isSameDay(today, t.getDue());
		}), today);
	}

	@GET
	@Path("/list/overdue")
	@Produces(MediaType.TEXT_PLAIN)
	public String overdueList() throws IOException, GeneralSecurityException {
		return formatOverdueTaskList((t -> {
			return isDueDateBefore(today(), t.getDue());
		}));
	}

	@GET
	@Path("/list/tomorrow")
	@Produces(MediaType.TEXT_PLAIN)
	public String list() throws IOException, GeneralSecurityException {
		final DateTime tomorrow = tomorrow();
		return formatTaskList((t -> {
			return isSameDay(tomorrow, t.getDue());
		}), tomorrow);
	}

	@GET
	@Path("/search/title/{pattern}")
	@Produces(MediaType.TEXT_PLAIN)
	public String search(@PathParam(value = "pattern") String pattern) {
		return formatSearchResultList(new Predicate<Task>() {
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
		return formatTaskList(new Predicate<Task>() {
			@Override
			public boolean test(Task t) {
				return isStringNull(t.getNotes()).toLowerCase().contains(pattern.toLowerCase());
			}
		}, today());
	}

	private static String isStringNull(String string) {
		return string == null ? "" : string;
	}

	@DELETE
	@Path("/delete/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public void deleteTask(@PathParam(value = "id") String taskId) throws IOException {
		System.out.println("Delete called with :" + taskId);
		asyncRefresh(getService().tasks().delete(MAIN_TASK_LIST_ID, taskId).execute());
	}

	@POST
	@Path("/bump/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public void bump(@PathParam(value = "id") String id) throws IOException {
		bump(id, 1);
	}

	@POST
	@Path("/bump/to/{id}/{nbDays}")
	@Produces(MediaType.TEXT_PLAIN)
	public void bumpTo(@PathParam(value = "id") String id, @PathParam(value = "nbDays") int nbDays) throws IOException {
		bump(id, nbDays);
	}

	@GET
	@Path("/desc/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public String fetchTasksDesc(@PathParam(value = "id") String id) throws IOException {
		if ( tasks.isEmpty() )
			refresh();
		if ( tasks.containsKey(id)) {
			return formatTaskWithNotes(tasks.get(id), (Task t) -> { return "[" + t.getId() + "] " + t.getTitle();});
		}
		throw new IllegalArgumentException("No tasks associated to ID: " + id);
	}

	@GET
	@Path("/pid")
	@Produces(MediaType.TEXT_PLAIN)
	public String pid() {
		return String.valueOf(ProcessHandle.current().pid());
	}

	private static boolean isSameDay(DateTime day, DateTime otherDay) {
		if (areDatesNull(day, otherDay)) {
			return false;
		}
		return day.toStringRfc3339().substring(0, 10).equals(otherDay.toStringRfc3339().substring(0, 10));
	}

	private static boolean areDatesNull(DateTime day, DateTime otherDay) {
		return (day == null || otherDay == null);
	}

	private static SimpleDateFormat SIMPLE_DATE_FORMATTER = new SimpleDateFormat("yyyyMMdd");

	private static int compareDateTo(Date date1, Date date2) {
		return SIMPLE_DATE_FORMATTER.format(date1).compareTo(SIMPLE_DATE_FORMATTER.format(date2));
	}

	private static int compareDateTo(DateTime date1, DateTime date2) {
		return compareDateTo(new Date(date1.getValue()), new Date(date2.getValue()));
	}

	private static boolean isDueDateBefore(DateTime dueDate, DateTime date) {
		return areDatesNull(dueDate, date) ? false : (compareDateTo(dueDate, date) > 0);
	}

	private static String formatTaskWithNumber(AtomicInteger counter, Task t, TaskFormatter taskFormatterFunction) {
		return counter.getAndIncrement() + ") " + taskFormatterFunction.formatTasks(t);
	}


	private static String formatTaskWithNotes(Task t, TaskFormatter taskFormatterFunction) {
		return taskFormatterFunction.formatTasks(t) + EOL + t.getNotes() + EOL;
	}

	private static String selectTasksToDisplay(Predicate<Task> predicate, AtomicInteger counter) {
		return selectTasksToDisplay(predicate, counter, (Task task) -> {return "[" + task.getId() + "] " + task.getTitle();});
	}

	private static String selectTasksToDisplay(Predicate<Task> predicate, AtomicInteger counter, TaskFormatter taskFormatterFunction) {
		return tasks.values().stream().filter(t -> predicate.test(t)).map(t -> formatTaskWithNumber(counter, t, taskFormatterFunction ))
				.collect(Collectors.joining(EOL));
	}

	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
			.withZone(java.time.ZoneId.of("CET"));

	private static String formatDate(DateTime date) {
		return formatter.format(new Date(date.getValue()).toInstant());
	}

	private static String formatTaskList(Predicate<Task> predicate, DateTime date) {
		return formatTaskList("Tasks due on " + formatDate(date) + ":" + EOL + EOL, predicate);
	}

	private static String formatOverdueTaskList(Predicate<Task> predicate) {
		return formatTaskList("Tasks overdue:" + EOL + EOL, predicate);
	}

	private String formatSearchResultList(Predicate<Task> predicate) {
		return formatTaskList("Results:" + EOL + EOL, predicate, (Task t) -> { 	return "[" + t.getId() + "] " + t.getTitle() + ", due on " + formatDate(t.getDue()) + EOL;} ,"");
	}

	private static String formatTaskList(String header, Predicate<Task> predicate) {
		return formatTaskList(header, predicate, EOL);
	}

	private static String formatTaskList(String header, Predicate<Task> predicate, String footer) {
		return header + selectTasksToDisplay(predicate, new AtomicInteger(1)) + footer;
	}

	private static String formatTaskList(String header, Predicate<Task> predicate, TaskFormatter taskFormatterFunction, String footer) {
		return header + selectTasksToDisplay(predicate, new AtomicInteger(1), taskFormatterFunction) + footer;
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
}
