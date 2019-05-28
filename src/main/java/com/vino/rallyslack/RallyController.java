package com.vino.rallyslack;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;

@RestController
public class RallyController {

	private String NO_DATA_FOUND = "No Data Found";

	private String SUCCESS = "Success";

	private String ERROR = "Internal Server Error";

	@Value("${apikey}")
	private String apikey;

	@RequestMapping(value = "/ping", method = RequestMethod.GET)
	public ResponseEntity<String> ping() {
		return new ResponseEntity<>("Success", HttpStatus.OK);
	}

	@RequestMapping(value = "/timeentry", params = { "!date" }, method = RequestMethod.GET)
	public ResponseEntity<Rally> timeentryByProject(@RequestParam("project") String project) throws Exception {

		List<TimeEntry> timeEntryList = processTimeEntry(project, null);
		HttpStatus status = getHttpStatusCode(timeEntryList);
		Rally rally = getRallyResponse(timeEntryList, status);
		return new ResponseEntity<Rally>(rally, status);
	}

	@RequestMapping(value = "/timeentry", params = { "!project" }, method = RequestMethod.GET)
	public ResponseEntity<Rally> timeentryByDate(@RequestParam("date") String date) throws Exception {

		List<TimeEntry> timeEntryList = processTimeEntry(null, date);
		HttpStatus status = getHttpStatusCode(timeEntryList);
		Rally rally = getRallyResponse(timeEntryList, status);
		return new ResponseEntity<Rally>(rally, status);
	}

	@RequestMapping(value = "/timeentry", params = { "project", "date" }, method = RequestMethod.GET)
	public ResponseEntity<Rally> timeentryByProjectAndDate(@RequestParam("project") String project,
			@RequestParam("date") String date) throws Exception {

		List<TimeEntry> timeEntryList = processTimeEntry(project, date);
		HttpStatus status = getHttpStatusCode(timeEntryList);
		Rally rally = getRallyResponse(timeEntryList, status);
		return new ResponseEntity<Rally>(rally, status);
	}

	@RequestMapping(value = "/timeentry", params = { "!project", "!date" }, method = RequestMethod.GET)
	public ResponseEntity<Rally> timeentry() throws Exception {

		List<TimeEntry> timeEntryList = processTimeEntry(null, null);
		HttpStatus status = getHttpStatusCode(timeEntryList);
		Rally rally = getRallyResponse(timeEntryList, status);
		return new ResponseEntity<Rally>(rally, status);
	}

	private List<TimeEntry> processTimeEntry(String project, String date) throws Exception {

		List<TimeEntry> timeEntryList = null;

		RallyRestApi restApi = null;
		try {
			restApi = getRallyRestApi();

			timeEntryList = getTimeEntries(restApi, date, project);

		} catch (Exception e) {
			e.printStackTrace();
			timeEntryList = null;
		} finally {
			if (restApi != null) {
				restApi.close();
			}
		}
		return timeEntryList;
	}

	private List<TimeEntry> getTimeEntries(RallyRestApi restApi, String inputDateStr, String projectName)
			throws Exception {

		List<TimeEntry> timeEntryList = new ArrayList<TimeEntry>();
		String projectRef = getProjectRefByName(restApi, projectName);

		System.out.println("projectRef : " + projectRef);
		if (projectRef == null) {
			return timeEntryList;
		}
		QueryFilter queryFilter = getQueryFilterStringByDate(inputDateStr);
		System.out.println("queryFilter : " + queryFilter);

		QueryRequest timeRequest = new QueryRequest("TimeEntryValue");
		timeRequest.setQueryFilter(queryFilter);
		timeRequest.setFetch(new Fetch(new String[] { "Task", "Hours", "TimeEntryItem", "User", "DateVal" }));
		timeRequest.setProject(projectRef);
		timeRequest.setLimit(25000);
		timeRequest.setScopedDown(false);
		timeRequest.setScopedUp(false);

		QueryResponse timeQueryResponse = restApi.query(timeRequest);

		JsonArray timeJsonArray = timeQueryResponse.getResults();

		if (timeJsonArray.size() == 0) {
			return timeEntryList;
		}

		timeEntryList = getTimeEntryBean(timeJsonArray);

		return timeEntryList;
	}

	private List<TimeEntry> getTimeEntryBean(JsonArray timeJsonArray) {
		Map<String, List<String>> timeMap = new HashMap<String, List<String>>();
		for (int i = 0; i < timeJsonArray.size(); i++) {
			JsonObject timeJsonObject = timeJsonArray.get(i).getAsJsonObject();
			JsonObject itemJsonObject = timeJsonObject.get("TimeEntryItem").getAsJsonObject();

			if (itemJsonObject.get("Task") != JsonNull.INSTANCE) {
				String taskName = itemJsonObject.get("Task").getAsJsonObject().get("_refObjectName").toString();
				taskName = taskName.replace("\"", "");

				if (!taskName.toUpperCase().contains("Project Meetings".toUpperCase())) {
					String user = itemJsonObject.get("User").getAsJsonObject().get("_refObjectName").toString();
					user = user.replace("\"", "");

					List<String> taksList = timeMap.get(user);
					if (taksList == null) {
						taksList = new ArrayList<String>();
					}
					taksList.add(taskName);
					timeMap.put(user, taksList);
				}

				/*
				 * if (!taskName.toUpperCase().contains("Project Meetings".toUpperCase())) {
				 * String results = results + "Name : " + user + ", Task : " + taskName // +
				 * ", Date : " + timeJsonObject.get("DateVal") + "\n"; } }
				 */
			}
		}

		Set<String> userSet = timeMap.keySet();
		List<TimeEntry> timeEntryList = new ArrayList<TimeEntry>();
		for (Iterator<String> iterator = userSet.iterator(); iterator.hasNext();) {
			String user = (String) iterator.next();
			TimeEntry timeEntry = new TimeEntry();
			timeEntry.setName(user);
			timeEntry.setTasks(timeMap.get(user));

			timeEntryList.add(timeEntry);

		}

		return timeEntryList;
	}

	private QueryFilter getQueryFilterStringByDate(String inputDateStr) {

		LocalDate current = null;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		if (inputDateStr == null) {
			current = LocalDate.now();
		} else {
			current = LocalDate.parse(inputDateStr, formatter);
		}

		LocalDate next = current.plusDays(1);
		LocalDate prev = current.minusDays(1);

		String nextStr = next.format(formatter);
		String prevStr = prev.format(formatter);

		prevStr = "\"" + prevStr + "\"";
		nextStr = "\"" + nextStr + "\"";

		QueryFilter filter = new QueryFilter("DateVal", ">", prevStr).and(new QueryFilter("DateVal", "<", nextStr));

		return filter;
	}

	private String getProjectRefByName(RallyRestApi restApi, String projectName) throws IOException {

		if (projectName == null) {
			projectName = "Brainiacs";
		}
		QueryRequest storyRequest = new QueryRequest("project");
		storyRequest.setFetch(new Fetch("Name", "ObjectID"));
		storyRequest.setQueryFilter(new QueryFilter("Name", "=", projectName));

		QueryResponse storyQueryResponse = restApi.query(storyRequest);

		String projectRef = null;
		if (storyQueryResponse.getTotalResultCount() > 0) {
			projectRef = "project/" + storyQueryResponse.getResults().get(0).getAsJsonObject().get("ObjectID");
			System.out
					.println("Project Name : " + storyQueryResponse.getResults().get(0).getAsJsonObject().get("Name"));
		}

		return projectRef;
	}

	private RallyRestApi getRallyRestApi() throws Exception {

		URI server = new URI("https://rally1.rallydev.com");
		System.out.println("apikey " + apikey);
		return new RallyRestApi(server, apikey);
	}

	private Rally getRallyResponse(List<TimeEntry> timeEntryList, HttpStatus status) {

		String message = SUCCESS;
		if (status == HttpStatus.NOT_FOUND) {
			message = NO_DATA_FOUND;
		} else if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
			message = ERROR;
		}

		Rally rally = new Rally();
		rally.setTimeList(timeEntryList);
		rally.setMessage(message);
		return rally;
	}

	private HttpStatus getHttpStatusCode(List<TimeEntry> timeEntryList) {

		HttpStatus status = HttpStatus.OK;
		if (timeEntryList == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		} else if (timeEntryList.isEmpty()) {
			status = HttpStatus.NOT_FOUND;
		}

		return status;
	}

}