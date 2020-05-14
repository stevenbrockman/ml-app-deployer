package com.marklogic.mgmt.resource.tasks;

import com.marklogic.mgmt.ManageClient;
import com.marklogic.mgmt.PayloadParser;
import com.marklogic.mgmt.SaveReceipt;
import com.marklogic.mgmt.api.API;
import com.marklogic.mgmt.api.task.Task;
import com.marklogic.mgmt.resource.AbstractResourceManager;
import com.marklogic.mgmt.resource.requests.RequestManager;
import com.marklogic.rest.util.Fragment;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * The "groupName" property of this class corresponds to the "group-id" querystring parameter. It's called
 * "groupName" because "group-id" is misleading - it's a name, not an ID.
 */
public class TaskManager extends AbstractResourceManager {

	private String groupName = "Default";

	public TaskManager(ManageClient client) {
		super(client);
	}

	public TaskManager(ManageClient client, String groupName) {
		super(client);
		this.groupName = groupName;
	}

	/**
	 * Tasks are tricky because their unique field is "task-id", but that's generated by MarkLogic - it's not expected
	 * to be in a payload. So this method has to do some extra work to determine what the task-id is for the given
	 * payload. It does so by looking for an existing task with the same task-path and task-database as what's in the
	 * given payload. If one exists, then the task-id from the existing task is used. Otherwise, null is returned.
	 *
	 * @param payload
	 * @return
	 */
	@Override
	protected String getResourceId(String payload) {
		final String taskId = payloadParser.getPayloadFieldValue(payload, "task-id", false);
		if (taskId != null) {
			return taskId;
		}

		final String taskPath = payloadParser.getPayloadFieldValue(payload, "task-path");
		final String taskDatabase = payloadParser.getPayloadFieldValue(payload, "task-database", false);

		final String xpath = taskDatabase != null ?
			format("/t:tasks-default-list/t:list-items/t:list-item[t:task-path = '%s' and t:task-database = '%s']/t:idref", taskPath, taskDatabase) :
			format("/t:tasks-default-list/t:list-items/t:list-item[t:task-path = '%s']/t:idref", taskPath);

		final List<String> resourceIds = getAsXml().getElementValues(xpath);
		if (resourceIds == null || resourceIds.isEmpty()) {
			return null;
		}

		// Check each matching resource ID until we fine one with the same taskRoot
		final String taskRoot = payloadParser.getPayloadFieldValue(payload, "task-root", false);
		if (taskRoot == null) {
			throw new RuntimeException("Unable to determine ID for task, as multiple existing tasks have the same " +
				"task-path and task-database, but payload is missing a task-root to determine which existing task is " +
				"the same root; payload: " + payload);
		}
		for (String resourceId : resourceIds) {
			String json = getManageClient().getJson(appendGroupId(super.getResourcesPath() + "/" + resourceId + "/properties"));
			String thisTaskRoot = payloadParser.getPayloadFieldValue(json, "task-root", false);
			if (taskRoot.equals(thisTaskRoot)) {
				return resourceId;
			}
		}

		// If no matching task has the same taskRoot, then this is a new task
		return null;
	}

	@Override
	public String getResourcesPath() {
		return appendGroupId(super.getResourcesPath());
	}

	protected String appendGroupId(String path) {
		if (groupName != null) {
			if (path.contains("?")) {
				return path + "&group-id=" + groupName;
			}
			return path + "?group-id=" + groupName;
		}
		return path;
	}

	@Override
	public String getResourcePath(String resourceNameOrId, String... resourceUrlParams) {
		return super.getResourcesPath() + "/" + getTaskIdForTaskPath(resourceNameOrId);
	}

	@Override
	protected String[] getUpdateResourceParams(String payload) {
		List<String> params = new ArrayList<>();
		params.add("group-id");
		params.add(groupName);
		return params.toArray(new String[]{});
	}

	@Override
	protected String getIdFieldName() {
		return "task-id";
	}

	/**
	 * Previously, this method only accepted a task-path because that was previously used as the unique ID for a task.
	 * But since two or more tasks can have the same task-path, this class now uses task-id. To preserve backwards
	 * compatibility then, this method accepts a task-path or a task-id.
	 *
	 * @param taskPathOrTaskId
	 * @return
	 */
	public String getTaskIdForTaskPath(String taskPathOrTaskId) {
		Fragment f = getAsXml();
		String xpath = "/t:tasks-default-list/t:list-items/t:list-item[t:task-path = '%s' or t:idref = '%s']/t:idref";
		xpath = String.format(xpath, taskPathOrTaskId, taskPathOrTaskId);
		List<String> resourceIds = f.getElementValues(xpath);
		if (resourceIds == null || resourceIds.isEmpty()) {
			throw new RuntimeException("Could not find a scheduled task with a task-path or task-id of: " + taskPathOrTaskId);
		}
		if (resourceIds.size() == 1) {
			return resourceIds.get(0);
		}
		throw new RuntimeException(format("Found multiple task IDs with the same task-path of %s; IDs: %s", taskPathOrTaskId, resourceIds));
	}

	/**
	 * This accounts for an existing task with either task-path or task-id equal to the given resourceNameOrId.
	 * This preserves backwards compatibility to when this class used task-path as the ID property.
	 *
	 * @param resourceNameOrId
	 * @param resourceUrlParams
	 * @return
	 */
	@Override
	public boolean exists(String resourceNameOrId, String... resourceUrlParams) {
		if (logger.isInfoEnabled()) {
			logger.info("Checking for existence of resource: " + resourceNameOrId);
		}
		Fragment f = getAsXml();
		return f.elementExists(format(
			"/t:tasks-default-list/t:list-items/t:list-item[t:task-path = '%s' or t:idref = '%s']",
			resourceNameOrId, resourceNameOrId));
	}

	/**
	 * When a task is being created, its resourceId - its task-id - will always be null because MarkLogic generates it.
	 * This method only needs the resourceId for logging purposes. So it's overridden so that the task-path can be used
	 * as the resourceId so that the logging is more useful.
	 *
	 * @param payload
	 * @param resourceId
	 * @return
	 */
	@Override
	protected SaveReceipt createNewResource(String payload, String resourceId) {
		final String taskPath = payloadParser.getPayloadFieldValue(payload, "task-path", false);

		SaveReceipt receipt = super.createNewResource(payload, taskPath);
		updateNewTaskIfItShouldBeDisabled(payload, receipt);
		return receipt;
	}

	/**
	 * This accounts for a bug in the Manage API where when a new task is created and it has task-enabled=false, the
	 * task isn't actually disabled. So an update call is made to the task right after it's created.
	 *
	 * @param payload
	 * @param receipt
	 */
	protected void updateNewTaskIfItShouldBeDisabled(String payload, SaveReceipt receipt) {
		String enabled = payloadParser.getPayloadFieldValue(payload, "task-enabled", false);
		if ("false".equalsIgnoreCase(enabled)) {
			// We don't reuse updateResource here since that first deletes the task
			URI uri = receipt.getResponse().getHeaders().getLocation();
			// Expecting a path of "/manage/(version)/tasks/(taskId)"
			String[] tokens = uri.getPath().split("/");
			final String taskId = tokens[tokens.length - 1];

			Task task = new Task(new API(getManageClient()), taskId);
			task.setTaskEnabled(false);

			String path = getPropertiesPath(taskId);
			path = appendParamsAndValuesToPath(path, getUpdateResourceParams(payload));
			logger.info("Updating new scheduled task so it is disabled; task ID: " + taskId);
			putPayload(getManageClient(), path, task.getJson());
		}
	}

	/**
	 * Per ticket #367, when a task is updated, it is first deleted. This is to workaround Manage API behavior which
	 * does not allow for any property besides "task-enabled" to be updated on a scheduled task. But it's often handy
	 * during a deployment to update some other property of a scheduled task. Unfortunately, that throws an error.
	 *
	 * So to work around that, when a scheduled task is updated, it's first deleted. Then the task is created, which
	 * includes making another call to disable the task if task-enabled is set to false.
	 *
	 * @param payload
	 * @param resourceId
	 * @return
	 */
	@Override
	public SaveReceipt updateResource(String payload, String resourceId) {
		logger.info("Deleting scheduled task first since updates are not allowed except for task-enabled; task ID: " + resourceId);
		deleteByIdField(resourceId);

		PayloadParser parser = new PayloadParser();
		payload = parser.excludeProperties(payload, "task-id");
		String taskPath = parser.getPayloadFieldValue(payload, "task-path");
		SaveReceipt receipt = super.createNewResource(payload, taskPath);
		updateNewTaskIfItShouldBeDisabled(payload, receipt);
		return receipt;
	}

	public List<String> getTaskPaths() {
		return getAsXml().getListItemValues("task-path");
	}

	public void disableAllTasks() {
		for (String id : getAsXml().getListItemIdRefs()) {
			disableTask(id);
		}
	}

	public void enableAllTasks() {
		for (String id : getAsXml().getListItemIdRefs()) {
			enableTask(id);
		}
	}

	public void disableTask(String taskId) {
		String json = format("{\"task-id\":\"%s\", \"task-enabled\":false}", taskId);
		String path = appendGroupId(super.getResourcesPath() + "/" + taskId + "/properties");
		putPayload(getManageClient(), path, json);
	}

	public void enableTask(String taskId) {
		String json = format("{\"task-id\":\"%s\", \"task-enabled\":true}", taskId);
		String path = appendGroupId(super.getResourcesPath() + "/" + taskId + "/properties");
		putPayload(getManageClient(), path, json);
	}

	public void deleteAllTasks() {
		deleteAllScheduledTasks();
	}

	public void deleteTaskWithPath(String taskPath) {
		String json = format("{\"task-path\":\"%s\"}", taskPath);
		delete(json, "group-id", groupName);
	}

	public String getTaskId(String taskPath) {
		return getAsXml().getElementValue(format(
			"/t:tasks-default-list/t:list-items/t:list-item[t:task-path = '%s']/t:idref", taskPath)
		);
	}

	public void deleteAllScheduledTasks() {
		for (String id : getAsXml().getListItemIdRefs()) {
			deleteAtPath(appendGroupId(super.getResourcesPath() + "/" + id));
		}
	}

	public void waitForTasksToComplete(String group, int retryInMilliseconds) {
		Fragment servers = getManageClient().getXml("/manage/v2/task-servers");
		String taskServerId = servers.getElementValue(format("//ts:list-item[ts:groupnameref = '%s']/ts:idref", group));
		if (taskServerId == null) {
			logger.warn(format("Could not find task server ID for group %s, so not waiting for tasks to complete", group));
			return;
		}
		RequestManager mgr = new RequestManager(getManageClient());
		if (logger.isInfoEnabled()) {
			logger.info("Waiting for tasks to complete on task server");
		}
		int count = mgr.getRequestCountForRelationId(taskServerId);
		while (count > 0) {
			if (logger.isInfoEnabled()) {
				logger.info("Waiting for tasks to complete on task server, count: " + count);
			}
			try {
				Thread.sleep(retryInMilliseconds);
			} catch (InterruptedException e) {
			}
			count = mgr.getRequestCountForRelationId(taskServerId);
		}
		if (logger.isInfoEnabled()) {
			logger.info("Finished waiting for tasks to complete on task server");
		}
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	public String getGroupName() {
		return groupName;
	}
}
