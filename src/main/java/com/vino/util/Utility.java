package com.vino.util;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;

public class Utility {

	public static void main(String[] args) throws Exception {

		

		

		URI server = new URI("https://rally1.rallydev.com");

		RallyRestApi restApi = new RallyRestApi(server, "_hTYpnS3FT5KuWDPiQb1kFdCDmOgaZ0JXmeYUSIZz6s");

		System.out.println(restApi);

		System.out.println(restApi.getWsapiVersion());

		restApi.setApplicationName("brainiacs_vinoth");



//		getAllTasks(restApi);

		getTimeEntries(restApi, null, null);

	}

	private static QueryFilter getQueryFilterStringByDate(String inputDateStr) {

		LocalDate current = null;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		if (inputDateStr == null) {
			current = LocalDate.now();
		} else {
			current = LocalDate.parse(inputDateStr, formatter);
		}

		LocalDate next = current.plusDays(1);
		LocalDate prev = current.minusDays(1);

//        String currentStr = current.format(formatter);
		String nextStr = next.format(formatter);
		String prevStr = prev.format(formatter);

		prevStr = "\"" + prevStr + "\"";
		nextStr = "\"" + nextStr + "\"";
		
		QueryFilter filter = new QueryFilter("DateVal", ">", prevStr).and(new QueryFilter("DateVal", "<", nextStr));

		return filter;
	}

	private static void getTimeEntries(RallyRestApi restApi, String inputDateStr, String projectName) throws IOException {
		String projectRef = getProjectRefByName(restApi, projectName);;
		QueryRequest timeRequest = new QueryRequest("TimeEntryValue");
		timeRequest.setQueryFilter(getQueryFilterStringByDate(inputDateStr));
		timeRequest.setFetch(new Fetch(new String[] { "Task", "Hours", "TimeEntryItem", "User", "DateVal" }));
		timeRequest.setProject(projectRef);
		timeRequest.setLimit(25000);
		timeRequest.setScopedDown(false);
		timeRequest.setScopedUp(false);

		QueryResponse timeQueryResponse = restApi.query(timeRequest);
		System.out.println(timeQueryResponse.getResults());
		System.out.println("Size: " + timeQueryResponse.getTotalResultCount());

		JsonArray timeJsonArray = timeQueryResponse.getResults();
		for (int i = 0; i < timeJsonArray.size(); i++) {
			JsonObject timeJsonObject = timeJsonArray.get(i).getAsJsonObject();
			JsonObject itemJsonObject = timeJsonObject.get("TimeEntryItem").getAsJsonObject();
			String taskName = itemJsonObject.get("Task").getAsJsonObject().get("_refObjectName").toString();
			String user = itemJsonObject.get("User").getAsJsonObject().get("_refObjectName").toString();

			if (!taskName.toUpperCase().contains("Project Meetings".toUpperCase())) {

				System.out.println(
						"Name : " + user + ", Task : " + taskName + ", Date : " + timeJsonObject.get("DateVal"));
//										+ " Hours Spent : " + timeJsonObject.get("Hours"));
			}
		}
	}

	private static void getAllTasks(RallyRestApi restApi) throws IOException {
		String projectRef = "project/251916962448";
		QueryRequest storyRequest = new QueryRequest("HierarchicalRequirement");
		storyRequest.setProject(projectRef);

		storyRequest.setFetch(new Fetch(new String[] { "Name", "FormattedID", "Tasks" }));
		storyRequest.setQueryFilter((new QueryFilter("LastUpdateDate", ">", "\"2019-05-27\""))
				.and(new QueryFilter("ScheduleState", "<", "Completed")));
		storyRequest.setLimit(25000);
		storyRequest.setScopedDown(false);
		storyRequest.setScopedUp(false);

		QueryResponse storyQueryResponse = restApi.query(storyRequest);
		System.out.println("Successful: " + storyQueryResponse.wasSuccessful());
		System.out.println("Size: " + storyQueryResponse.getTotalResultCount());
		System.out.println(storyQueryResponse.getResults());

		for (int i = 0; i < storyQueryResponse.getResults().size(); i++) {
			JsonObject storyJsonObject = storyQueryResponse.getResults().get(i).getAsJsonObject();
			System.out.println("Name: " + storyJsonObject.get("Name"));

			QueryRequest taskRequest = new QueryRequest(storyJsonObject.getAsJsonObject("Tasks"));
			taskRequest.setFetch(new Fetch("Name", "FormattedID", "owner", "State", "Actuals", "Estimate", "ToDo",
					"LastUpdateDate"));
			taskRequest.setQueryFilter((new QueryFilter("LastUpdateDate", ">", "\"2019-05-26\"")));

			// load the collection
			JsonArray tasks = restApi.query(taskRequest).getResults();
			System.out.println("Tasks Size " + tasks.size());

			for (int j = 0; j < tasks.size(); j++) {
				JsonObject taskJsonObject = tasks.get(j).getAsJsonObject();

				String owner = taskJsonObject.get("Owner").getAsJsonObject().get("_refObjectName").toString();
				String taskName = taskJsonObject.get("Name").toString();

//            	if (owner.toUpperCase().contains("KINGS")) {

				if (!taskName.toUpperCase().contains("HOLDER")) {

					/*
					 * System.out.println("Owner: " + owner + " State: " +
					 * taskJsonObject.get("State") + " Estimate: " + taskJsonObject.get("Estimate")
					 * + " ToDo: " + taskJsonObject.get("ToDo") + " LastUpdateDate: " +
					 * taskJsonObject.get("LastUpdateDate") + " Name: " +
					 * taskJsonObject.get("Name"));
					 */

					System.out.println("Owner: " + owner + " Task Name: " + taskName);
				}
			}

			System.exit(0);
		}
	}

	private static String getProjectRefByName(RallyRestApi restApi, String projectName) throws IOException {
		
		if (projectName == null) {
			projectName = "Brainiacs";
		}
		QueryRequest storyRequest = new QueryRequest("project");
		storyRequest.setFetch(new Fetch("Name", "ObjectID"));
		storyRequest.setQueryFilter(new QueryFilter("Name", "=", projectName));
		
		QueryResponse storyQueryResponse = restApi.query(storyRequest);
		System.out.println("Successful: " + storyQueryResponse.wasSuccessful());
		System.out.println("Size: " + storyQueryResponse.getTotalResultCount());
		
		String projectRef = null;
		if (storyQueryResponse.getTotalResultCount() > 0) {
			projectRef = "project/" + storyQueryResponse.getResults().get(0).getAsJsonObject().get("ObjectID");
		}
		System.out.println(projectRef);
		
		return projectRef;
	}

}
