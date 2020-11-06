package org.belaran.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

import org.belaran.tasks.util.TaskUtils;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.Task;

@Singleton
public class GTasksServiceClient {

	private final static Logger LOGGER = Logger.getLogger(GTasksServiceClient.class.getName());

	private static final String APPLICATION_NAME = "Google Tasks API Java Quickstart";
	private static final String TASK_CLIENT_SECRET_FILE_ENV_VAR_NAME = "TASKS_CLIENT_SECRET";

	private final File DATA_STORE_DIR = new File(System.getProperty("user.home"), ".credentials/tasks-java-quickstart");
	private final static String CLIENT_SECRET_FILENAME = System.getenv(TASK_CLIENT_SECRET_FILE_ENV_VAR_NAME);
	private final static JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private final static List<String> SCOPES = Arrays.asList(TasksScopes.TASKS);

	private static final String MAIN_TASK_LIST_ID = "@default";

	private FileDataStoreFactory dataStoreFactory;

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
	}

	public Map<String, Task> fetchAllItemsOfDefaultList() throws IOException {
		return fetchAllItemsOfDefaultList(getService());
	}

	private Map<String, Task> fetchAllItemsOfDefaultList(Tasks service) throws IOException {
		com.google.api.services.tasks.model.Tasks gtasks = null;
		final Map<String, Task> tasks = new ConcurrentHashMap<String, Task>();
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
		return tasks;
	}

	private Optional<Tasks> taskService = Optional.empty();

	public Tasks getService() throws IOException {
		try {
			return getServiceXXX();
		} catch ( GeneralSecurityException e) {
			throw new IOException(e);
		}
	}

	private Tasks getServiceXXX() throws IOException, GeneralSecurityException {
		if (taskService.isEmpty()) {
			NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			GoogleClientSecrets clientSecrets = openGoogleClientSecrets();
			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY,
					clientSecrets, SCOPES).setDataStoreFactory(dataStoreFactory).setAccessType("offline").build();
			Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
					.authorize("user");
			taskService = Optional.of(new Tasks.Builder(httpTransport, JSON_FACTORY, credential)
					.setApplicationName(APPLICATION_NAME).build());
		}
		return taskService.get();
	}

	public void resetClient() {
		taskService = Optional.empty();
	}

	public void tagAndUpdateTask(String symbol, Task task) throws IOException {
		getService().tasks()
				.update(MAIN_TASK_LIST_ID, task.getId(),
						TaskUtils.updateTaskTitle(task, TagController.tagTaskTitle(symbol, task.getTitle())))
				.execute();
	}

	public Task updateTask(Task task) throws IOException {
		return getService().tasks()
				.update(MAIN_TASK_LIST_ID, task.getId(), task)
				.execute();
	}

	public Task retrieveTaskById(String id) throws IOException {
		return Optional.of(getService().tasks().get(MAIN_TASK_LIST_ID, id).execute()).orElseThrow(() -> new IllegalArgumentException("No task associated to id " + id));
	}

	public String insertTask(Task task) throws IOException {
		return getService().tasks().insert(MAIN_TASK_LIST_ID, task).execute().getId();
	}

	public void bumpTaskById(String id, int nbDays) throws IOException {
		Task task = this.retrieveTaskById(id);
		getService().tasks().update(MAIN_TASK_LIST_ID, task.getId(), TaskUtils.pushDueDateTo(task, nbDays)).execute();
	}

	public void deleteTask(String taskId) {
		try {
		getService().tasks().delete(MAIN_TASK_LIST_ID, taskId).execute();
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
