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
import java.util.StringTokenizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
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
	
	private String DEFAULT_PROJECT = "Brainiacs";
	
	private String INVALID_USAGE = "Invalid Usage";
	
	private String SLACK_RESPONSE_TYPE = "in_channel";
	
	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
	
	@RequestMapping(value = "/timeentry", method = RequestMethod.POST )
	public ResponseEntity<Slack> timeentryByPost(@RequestBody MultiValueMap<String, String> bodyMap) throws Exception {
		System.out.println(bodyMap);
		List<String> inputList = bodyMap.get("text");
		
		String project = DEFAULT_PROJECT;
		String date = getTodaysDate();
	
		String result = "";
		if (inputList != null && !inputList.isEmpty()) {
			String input = inputList.get(0);
			if (input != null && input.length() > 0) {
				StringTokenizer token = new StringTokenizer(input, ",");
				if (token.countTokens() == 2) {
					project = token.nextElement().toString();
					date = token.nextElement().toString();
				} else if (token.countTokens() == 1) {
					project = input;
				} else {
					result = INVALID_USAGE;
					return new ResponseEntity<Slack>(new Slack(SLACK_RESPONSE_TYPE, result), HttpStatus.OK);
				}
			}
		}
		
		List<TimeEntry> timeEntryList = processTimeEntry(project, date);
		
		result = "`" + project + " Staus Update - " + date + "`" + "\n" + "============================================================\n";
		
		if (timeEntryList == null || timeEntryList.isEmpty()) {
			result = result +  "    " + "- " + "No Records Found";
			return new ResponseEntity<Slack>(new Slack(SLACK_RESPONSE_TYPE, result), HttpStatus.OK);
		}
		
		for (Iterator<TimeEntry> iterator = timeEntryList.iterator(); iterator.hasNext();) {
			TimeEntry timeEntry = (TimeEntry) iterator.next();
			
			result = result + timeEntry.getName() + "\n";
			
			List<String> taskList = timeEntry.getTasks();
			for (Iterator<String> iterator2 = taskList.iterator(); iterator2.hasNext();) {
				String task = (String) iterator2.next();
				result = result + "    " + "- " + task + "\n";
			}
			result = result + "\n";
		}
		return new ResponseEntity<Slack>(new Slack(SLACK_RESPONSE_TYPE, result), HttpStatus.OK);
	}

	private List<TimeEntry> processTimeEntry(String project, String date) throws Exception {

		System.out.println("Input Project : " + project + ", Input Date " + date);
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
		
		if (queryFilter == null) {
			return timeEntryList;
		}

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

		QueryFilter filter = null;
		try {
			LocalDate current = null;
			
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

			filter = new QueryFilter("DateVal", ">", prevStr).and(new QueryFilter("DateVal", "<", nextStr));
		} catch (Exception e) {
			e.printStackTrace();
		}

		return filter;
	}
	
	private String getTodaysDate() {
		
		LocalDate current = LocalDate.now();
		return current.format(formatter);
		
	}

	private String getProjectRefByName(RallyRestApi restApi, String projectName) throws IOException {

		if (projectName == null) {
			projectName = DEFAULT_PROJECT;
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

	

	private Rally getRallyResponse(List<TimeEntry> timeEntryList, HttpStatus status) {

		String message = SUCCESS;
		if (status == HttpStatus.NOT_FOUND) {
			message = NO_DATA_FOUND;
		} else if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
			message = ERROR;
		}

		Rally rally = new Rally();
		rally.setTimeSheetEntries(timeEntryList);
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
	
	private RallyRestApi getRallyRestApi() throws Exception {

		URI server = new URI("https://rally1.rallydev.com");
		return new RallyRestApi(server, apikey);
	}
	
	public static void main(String args[]) throws Exception {
		
		RallyController rallyController = new RallyController(); 
//		System.out.println(rallyController.getTodaysDate());
		/*
		 * String project = "Brainiacs";
		 * 
		 * String date = "2019-05-29";
		 * 
		 * RallyController rallyController = new RallyController(); List<TimeEntry>
		 * timeEntryList = rallyController.processTimeEntry(project, date); HttpStatus
		 * status = rallyController.getHttpStatusCode(timeEntryList); Rally rally =
		 * rallyController.getRallyResponse(timeEntryList, status);
		 * 
		 * 
		 * 
		 * String result = "`" + project + " Staus Update - " + date + "`" + "\n";
		 * 
		 * if (timeEntryList == null || timeEntryList.isEmpty()) { result = result +
		 * "    " + "- " + "No Records Found"; } for (Iterator iterator =
		 * timeEntryList.iterator(); iterator.hasNext();) { TimeEntry timeEntry =
		 * (TimeEntry) iterator.next();
		 * 
		 * result = result + timeEntry.getName() + "\n";
		 * 
		 * List<String> taskList = timeEntry.getTasks(); for (Iterator iterator2 =
		 * taskList.iterator(); iterator2.hasNext();) { String task = (String)
		 * iterator2.next(); result = result + "    " + "- " + task + "\n"; } }
		 */
		
		/*
		 * String input = "Brainiacs2019-05-29::"; StringTokenizer token = new
		 * StringTokenizer(input, "::"); if (token.countTokens() == 2) {
		 * System.out.println(token.nextElement().toString());
		 * System.out.println(token.nextElement().toString()); } else if
		 * (token.countTokens() == 1) { System.out.println(input); } else {
		 * System.out.println("error"); }
		 */
		
		
			String input = "Brainiacs,,2019,-05-29";
			StringTokenizer token = new StringTokenizer(input, ",");
			int count = token.countTokens();
			System.out.println(count);
			if (count == 2) {
				System.out.println(token.nextElement().toString());
				System.out.println(token.nextElement().toString());
			} else if (count == 1) {
				System.out.println(token.nextElement().toString());
			} else {
				System.out.println("error");
			}
		
		
		
	}

}
