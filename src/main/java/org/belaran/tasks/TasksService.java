package org.belaran.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
    private static final String TASK_PID_FILE_ENV_VAR_NAME = "TASKS_PIDFILE";
    //

    private final File DATA_STORE_DIR = new File(System.getProperty("user.home"), ".credentials/tasks-java-quickstart");
    private final static String CLIENT_SECRET_FILENAME = System.getenv(TASK_CLIENT_SECRET_FILE_ENV_VAR_NAME);
    private final static String PID_FILE_NAME = System.getenv(TASK_PID_FILE_ENV_VAR_NAME);
    private final static JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private final static List<String> SCOPES = Arrays.asList(TasksScopes.TASKS);
    private static final long ONE_DAY__IN_MILLIS = 86400000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final Map<String, Task> tasks = new ConcurrentHashMap<String, Task>();

    private FileDataStoreFactory dataStoreFactory;
    private  NetHttpTransport httpTransport;

    private static DateTime today() {
        return new DateTime(System.currentTimeMillis());
    }

    private static DateTime tomorrow() {
        return new DateTime(System.currentTimeMillis() + ONE_DAY__IN_MILLIS);
    }

    public TasksService() throws IOException {
        LOGGER.info("PID Filename:" + PID_FILE_NAME);
        Files.write(Paths.get(PID_FILE_NAME), String.valueOf(ProcessHandle.current().pid()).getBytes());
        LOGGER.info("PID has been stored into "  + PID_FILE_NAME);
    }

    private void checkClientSecretFilename() {
        if ( ! "".equals(CLIENT_SECRET_FILENAME) )
            LOGGER.info("Client Secret stored in " + CLIENT_SECRET_FILENAME);
        else
            throw new IllegalStateException("Path to certificate file not provided.");

        if ( ! new java.io.File(CLIENT_SECRET_FILENAME).exists() )
            throw new IllegalStateException("Certificate file does not exists: " + CLIENT_SECRET_FILENAME);
    }

    private GoogleClientSecrets openGoogleClientSecrets() throws IOException {
        try {
            return GoogleClientSecrets.load(JSON_FACTORY,
                    new InputStreamReader(new FileInputStream( CLIENT_SECRET_FILENAME )));

        } catch ( FileNotFoundException fileNotFoundException ) {
            throw new IllegalStateException(fileNotFoundException);
        }
    }

    @PostConstruct
    void serviceInit() throws IOException, GeneralSecurityException {
        checkClientSecretFilename();

        dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
        httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        this.refresh();
    }

    private boolean refresh() throws IOException {
        Tasks service = getService();
        tasks.clear();
        for (Task t : service.tasks().list("@default").execute().getItems()  ) {
            tasks.put(t.getId(),t);
        }
        return true;
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
        String id = getService().tasks().insert("@default",task).execute().getId();
        asyncRefresh();
        return id;
    }

    private Tasks getService() throws IOException {
        GoogleClientSecrets clientSecrets = openGoogleClientSecrets();
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder( httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline")
                .build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        return new Tasks.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
    }

    private void bump(String id, int nbDays) throws IOException {
        long NB_SECONDS_BY_DAY = 86400L * 1000;
        Tasks taskService = getService();
        Task task = taskService.tasks().get("@default", id).execute();
        if ( task != null ) {
            task.setDue(new DateTime(task.getDue().getValue() + (nbDays * NB_SECONDS_BY_DAY)));
            taskService.tasks().update("@default", task.getId(), task).execute();
            asyncRefresh();
         }
    }

    @PUT
    @Path("/{title}")
    @Produces(MediaType.TEXT_PLAIN)
    public String addTaskss(@PathParam(value="title") String title) throws FileNotFoundException, IOException {
        return insertTask(buildTask(title, "", today()));
    }

    @PUT
    @Path("/{title}/{description}")
    @Produces(MediaType.TEXT_PLAIN)
    public String addTasksWithNotes(@PathParam(value="title") String title, @PathParam(value="description") String description) throws FileNotFoundException, IOException {
        return insertTask(buildTask(title, description, today()));
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
        return formatTaskList(today());
    }

    @DELETE
    @Path("/delete/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public void deleteTask(@PathParam(value = "id") String taskId) throws IOException {
        System.out.println("Delete called with :" + taskId);
        getService().tasks().delete("@default", taskId).execute();
        asyncRefresh();
    }

    @POST
    @Path("/bump/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public void bump(@PathParam(value="id") String id) throws IOException {
        bump(id,1);
    }

    @POST
    @Path("/bump/to/{id}/{nbDays}")
    @Produces(MediaType.TEXT_PLAIN)
    public void bumpTo(@PathParam(value="id") String id, @PathParam(value="nbDays") int nbDays) throws IOException {
        bump(id,nbDays);
    }

    @GET
    @Path("/list/tomorrow")
    @Produces(MediaType.TEXT_PLAIN)
    public String tomorrowList() throws IOException, GeneralSecurityException {
        return formatTaskList(tomorrow());
    }

    @GET
    @Path("/pid")
    @Produces(MediaType.TEXT_PLAIN)
    public String pid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

    private static boolean isSameDay(DateTime day, DateTime otherDay) {
        return day.toStringRfc3339().substring(0,10).equals(otherDay.toStringRfc3339().substring(0,10));
    }

    private static String formatTaskList(DateTime dueDate) {
        String output = "Tasks due on " + dueDate + ":\n\n";
        int taskId = 0;
        for ( Task t : tasks.values() ) {
            if ( isSameDay(dueDate,t.getDue()) )
                output += taskId++ + ") [" + t.getId() + "] " + t.getTitle() + "\n";
        }
        return output;
    }
}
