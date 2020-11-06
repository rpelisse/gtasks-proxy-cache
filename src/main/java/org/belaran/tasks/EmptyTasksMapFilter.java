package org.belaran.tasks;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

@Provider
public class EmptyTasksMapFilter implements ContainerRequestFilter {

	private static final Logger LOGGER = Logger.getLogger(EmptyTasksMapFilter.class);
	private static final int TIMEOUT = 30000000;
	private static final int WAIT_TIME = 300;

	@Context
	UriInfo info;

	@Inject
	TasksCache tasks;

	@Inject
	GTasksServiceClient client;

	@Override
	public void filter(ContainerRequestContext context) throws IOException {
		if ( info.getPath().contains("tasks/list") && tasks.getTasks().isEmpty() ) {
			if (LOGGER.isDebugEnabled() ) LOGGER.debug("Tasks list is currently, wait for (maximum) " + 3 + " before returning request:");
			waitForTasksToFillUp();
			if ( tasks.getTasks().isEmpty() ) {
				client.resetClient();
				client.getService();
				synchronized (tasks) {
					tasks.getTasks().putAll(client.fetchAllItemsOfDefaultList());
				}
			}
		}
	}

	private void waitForTasksToFillUp() {
		int timeAwaited = 0;
		while ( tasks.getTasks().isEmpty() && timeAwaited < TIMEOUT) {
			if (LOGGER.isDebugEnabled() ) LOGGER.debug("Time awaited:" + timeAwaited);
			waitForInMilliSeconds(WAIT_TIME);
			timeAwaited += WAIT_TIME;
		}
	}

	private void waitForInMilliSeconds(int nbMilliSeconds) {
		CountDownLatch counter = new CountDownLatch(0);
		try {
			counter.await(nbMilliSeconds, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// ignore, filter will simply hand over the request
		}
	}
}
