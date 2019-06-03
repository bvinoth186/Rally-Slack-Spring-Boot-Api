package com.vino.rallyslack;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

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
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RallyController.class);
	
	private static final  DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private static final  String NO_DATA_FOUND = "No Data Found";

    private static final  String SUCCESS = "Success";
	
    private static final  String DEFAULT_PROJECT = "";
    
    private static final  String SLACK_RESPONSE_TYPE = "in_channel";
	
	@Value("${apikey}")
	private String apikey;
	
	@Value("${slack-channel-transaction}")
	private String slackChTransaction;
	
	@Autowired
	private Environment env;
	
	@Autowired
	private RestTemplate restTemplate;

	@RequestMapping(value = "/ping", method = RequestMethod.GET)
	public ResponseEntity<String> ping() {
		return new ResponseEntity<>(SUCCESS, HttpStatus.OK);
	}
	
	public static String getUsage() {
		
		String usage = "Slack application intracts with Rally and returns the timesheet data for the given project and date\n\n";
		usage = usage + "`USAGE: `\n\n";
		usage = usage + "   \t/fly-rally-timesheet project-name[,date] \n\n";
		usage = usage + "   --> project-name - Name of the Rally Project (required) \n";
		usage = usage + "   --> date         - Date in which timesheet details required (optional) \n\n";
		usage = usage + "   --> project-name shoufd match with Rally \n";
		usage = usage + "   --> if the date is not provided, timesheet details of current day would be returned \n";
		usage = usage + "   --> date should be in  YYYY-MM-dd format (optional) \n";
		usage = usage + "   --> project-name and date is seperated by comma ',' \n\n";
		usage = usage + "`EXAMPLE: `\n\n";
		usage = usage + "   /fly-rally-timesheet Brainiacs \n";
		usage = usage + "   /fly-rally-timesheet Brainiacs,2019-05-31\n";
				
				
			
		return usage;
	}
	
	@Scheduled(cron = "11 10 * * * *")
	public void schedule() throws Exception {
		LOGGER.info("Scheduled at" + new Date());
	   
		MultiValueMap<String, String> bodyMap = new LinkedMultiValueMap<String, String>();
		List<String> inputList = new ArrayList<String>();
		inputList.add("Brainiacs");
		bodyMap.put("text", inputList);
		
		ResponseEntity<Slack> responseEntity = publishToSlack(bodyMap);
		LOGGER.info("Scheduler response " + responseEntity.getStatusCode().toString());
	}

	
	@RequestMapping(value = "/publish", method = RequestMethod.POST )
	public ResponseEntity<Slack> publishToSlack(@RequestBody MultiValueMap<String, String> bodyMap) throws Exception {
		
		List<String> inputList  = parseInputArgument(bodyMap);
		if (inputList == null) {
			return new ResponseEntity<Slack>(new Slack(SLACK_RESPONSE_TYPE, getUsage()), HttpStatus.OK);
		}
		
		String project = inputList.get(0);
		ResponseEntity<Slack> responseEntity = timeentry(bodyMap);
		
		String endpoint = getSlackWebHookUrl(project);
		if (endpoint != null) {
			String results = "[Auto triggered on " + getCentralTime() + "] \n\n" + responseEntity.getBody().getText();
			ResponseEntity<String> response = post(endpoint, results);
			
			LOGGER.info("published to Slack : " + response.getBody() + " for the project " + project);
		} else {
			LOGGER.info("Webhook URL is not defined for " + project);
		}
		
		return responseEntity;
		
	}
	
	private String getSlackWebHookUrl(String project) {
		return env.getProperty(project);
	}


	@RequestMapping(value = "/timeentry", method = RequestMethod.POST )
	public ResponseEntity<Slack> timeentry(@RequestBody MultiValueMap<String, String> bodyMap) throws Exception {
		
		LOGGER.info("Post parameters " + bodyMap);
		logTransactionIntoSlack(bodyMap);
		
		List<String> inputList  = parseInputArgument(bodyMap);
		if (inputList == null) {
			return new ResponseEntity<Slack>(new Slack(SLACK_RESPONSE_TYPE, getUsage()), HttpStatus.OK);
		}
		
		String project = inputList.get(0);
		String date = inputList.get(1);
		Map<String, List<Task>>  timeMap = process(project, date);
		String result = constructResultString(project, date, timeMap);
		
		LOGGER.info("Timesheet data fetched for " + timeMap.size() + " users");
		return new ResponseEntity<Slack>(new Slack(SLACK_RESPONSE_TYPE, result), HttpStatus.OK);
	}
	
	private List<String> parseInputArgument(MultiValueMap<String, String> bodyMap) {
		
		
		
		List<String> textList = bodyMap.get("text");
		String project = DEFAULT_PROJECT;
		String date = getTodaysDate();
		
		if (textList != null && !textList.isEmpty()) {
			String text = textList.get(0);
			if (text != null && text.length() > 0) {
				StringTokenizer token = new StringTokenizer(text, ",");
				if (token.countTokens() == 2) {
					project = token.nextElement().toString();
					date = token.nextElement().toString();
				} else if (token.countTokens() == 1) {
					project = text;
				} else {
					return null;
					
				}
			} else {
				return null;
			}
		} else {
			return null;
		}
		
		List<String> inputList  = new ArrayList<String>();
		inputList.add(project);
		inputList.add(date);
		
		return inputList;
	}

	private void logTransactionIntoSlack(MultiValueMap<String, String> bodyMap) {
		String transactionLog = "User Name = " + bodyMap.get("user_name") 
										+ ", Channel Name = " + bodyMap.get("channel_name") 
										+ ", Command = " + bodyMap.get("command") 
										+ ", Arguments = " + bodyMap.get("text") ;
		

		ResponseEntity<String> response = post(slackChTransaction, transactionLog);
		LOGGER.info("logTransactionToSlack : " + response.getBody());
	}



	private ResponseEntity<String> post(String endpoint, String text) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		Map<String, String> map = new HashMap<String, String>();
		map.put("text", text);
		HttpEntity<Map<String, String>> request = new HttpEntity<Map<String, String>>(map, headers);
		ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request , String.class );
		
		return response;
	}

	private String constructResultString(String project, String date, Map<String, List<Task>>  timeMap) {
		
		String result = "`" + project + " Staus Update - " + date + "`" + "\n" + "===============================================================\n";
		
		if (timeMap == null || timeMap.isEmpty()) {
			result = result +  "    " + "- " + NO_DATA_FOUND;
			return result;
		}
		
		Set<String> userSet = timeMap.keySet();
		for (Iterator <String> iterator = userSet.iterator(); iterator.hasNext();) {
			String userName = iterator.next();
			
			result = result + "`" + userName + "`" + "\n\n";
			
			List<Task> taskList = timeMap.get(userName);
			for (Iterator<Task> iterator2 = taskList.iterator(); iterator2.hasNext();) {
				Task task = (Task) iterator2.next();
				result = result + "    " + "- " + task.getId() + " - " + task.getName() + " - " + task.getState() + "\n" ;
				
				if (task.getNotes() != null && task.getNotes().length() > 0) {
					result = result + "        " + ">> Notes :  " + task.getNotes() + "\n";
				}
			}
			result = result + "\n";
		}
		return result;
	}

	private Map<String, List<Task>> process(String project, String date) throws Exception {

		LOGGER.info("Project : " + project + ", Input Date " + date);
		Map<String, List<Task>> timeMap = null;
		
		RallyRestApi restApi = null;
		try {
			restApi = getRallyRestApi();
			LOGGER.info("Rally API " + restApi);

			timeMap = getTimeEntries(restApi, project, date);

		} catch (Exception e) {
			e.printStackTrace();
			timeMap = null;
		} finally {
			if (restApi != null) {
				restApi.close();
			}
		}
		return timeMap;
	}

	private Map<String, List<Task>> getTimeEntries(RallyRestApi restApi, String projectName, String date)
			throws Exception {

		Map<String, List<Task>> timeMap = new HashMap<String, List<Task>>();
		
		String projectRef = getProjectRefByName(restApi, projectName);
		LOGGER.info("projectRef : " + projectRef);
		if (projectRef == null) {
			return timeMap;
		}
		
		QueryFilter queryFilter = getQueryFilterStringByDate(date);
		LOGGER.info("queryFilter : " + queryFilter);
		if (queryFilter == null) {
			return timeMap;
		}

		QueryRequest timeRequest = new QueryRequest("TimeEntryValue");
		timeRequest.setQueryFilter(queryFilter);
		timeRequest.setFetch(new Fetch(new String[] { "Task", "Hours", "TimeEntryItem", "User", "DateVal", "ObjectID", "Name" }));
		timeRequest.setProject(projectRef);
		timeRequest.setLimit(25000);
		timeRequest.setScopedDown(false);
		timeRequest.setScopedUp(false);

		QueryResponse timeQueryResponse = restApi.query(timeRequest);
		JsonArray timeJsonArray = timeQueryResponse.getResults();
		if (timeJsonArray.size() == 0) {
			return timeMap;
		}

		timeMap  = getTimeEntryMap(restApi, timeJsonArray, projectRef);

		return timeMap;
	}

	private Map<String, List<Task>>  getTimeEntryMap(RallyRestApi restApi, JsonArray timeJsonArray,
			String projectRef) throws Exception {
		
		Map<String, List<Task>> timeMap = new HashMap<String, List<Task>>();
		Set<String> objSet = new HashSet<String>();
		for (int i = 0; i < timeJsonArray.size(); i++) {
			JsonObject timeJsonObject = timeJsonArray.get(i).getAsJsonObject();
			JsonObject itemJsonObject = timeJsonObject.get("TimeEntryItem").getAsJsonObject();

			if (itemJsonObject.get("Task") != JsonNull.INSTANCE) {
				
				JsonObject taskObj = itemJsonObject.get("Task").getAsJsonObject();
				String taskName = taskObj.get("Name").toString();
				taskName = taskName.replace("\"", "");

				if (!taskName.toUpperCase().contains("Project Meetings".toUpperCase())) {
					String user = itemJsonObject.get("User").getAsJsonObject().get("_refObjectName").toString();
					user = user.replace("\"", "");
					
					String taskObjId = taskObj.get("ObjectID").toString();
					if (!objSet.contains(taskObjId)) {
						objSet.add(taskObjId);
						Task task = getTaskBean(restApi, taskObjId, projectRef);
	
						List<Task> taksList = timeMap.get(user);
						if (taksList == null) {
							taksList = new ArrayList<Task>();
						}
						taksList.add(task);
						timeMap.put(user, taksList);
					}
				}

			}
		}

		return timeMap;
	}

	private Task getTaskBean(RallyRestApi restApi, String taskObjId, String projectRef) throws Exception {

		  QueryRequest taskRequest = new QueryRequest("Task");
		  taskRequest.setProject(projectRef);
		  taskRequest.setFetch(new Fetch(new String[] { "Name", "Notes", "FormattedID", "ObjectID", "State" }));
		  taskRequest.setQueryFilter(new QueryFilter("ObjectID", "=", taskObjId));
		  
		  QueryResponse taskQueryResponse = restApi.query(taskRequest);
		  
		  JsonArray taskJsonArray = taskQueryResponse.getResults();
		  Task task = null;
		  if (taskJsonArray.size() != 0) {
			  JsonObject taskJsonObject = taskJsonArray.get(0).getAsJsonObject();
			  
			  String taskName = taskJsonObject.get("Name").toString();
			  String id = taskJsonObject.get("FormattedID").toString();
			  String notes = taskJsonObject.get("Notes").toString();
			  String state = taskJsonObject.get("State").toString();
			  
			  taskName = taskName.replace("\"", "");
			  id = id.replace("\"", "");
			  notes = notes.replace("\"", "");
			  state = state.replace("\"", "");
			  
			  
			  notes = notes.replaceAll("\\<.*?\\>", "");
			  notes = notes.replaceAll("&nbsp;", "");
			  notes = notes.replaceAll("&amp;", "");
			  
			  task = new Task(id, taskName, notes, state);
		  }
		  
		  return task;
	}

	private QueryFilter getQueryFilterStringByDate(String inputDateStr) {

		QueryFilter filter = null;
		try {
			LocalDate current = null;
			
			if (inputDateStr == null) {
				current = LocalDate.now();
			} else {
				current = LocalDate.parse(inputDateStr, DATE_FORMATTER);
			}

			LocalDate next = current.plusDays(1);
			LocalDate prev = current.minusDays(1);

			String nextStr = next.format(DATE_FORMATTER);
			String prevStr = prev.format(DATE_FORMATTER);

			prevStr = "\"" + prevStr + "\"";
			nextStr = "\"" + nextStr + "\"";

			filter = new QueryFilter("DateVal", ">", prevStr).and(new QueryFilter("DateVal", "<", nextStr));
		} catch (Exception e) {
			e.printStackTrace();
		}

		return filter;
	}
	
	private String getTodaysDate() {
		
		Instant nowUtc = Instant.now();
		ZoneId usCentral = ZoneId.of("US/Central");
		ZonedDateTime centralTime = ZonedDateTime.ofInstant(nowUtc, usCentral);
		return centralTime.format(DATE_FORMATTER);
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
		}

		return projectRef;
	}
	
	private void printAllProjects(RallyRestApi restApi) throws IOException {

		QueryRequest projectRequest = new QueryRequest("project");
		projectRequest.setFetch(new Fetch("Name", "ObjectID"));
		
		QueryResponse projectQueryResponse = restApi.query(projectRequest);
		JsonArray projectJsonArray = projectQueryResponse.getResults();
		
		for (int i = 0; i < projectJsonArray.size(); i++) {
			JsonObject obj  = projectJsonArray.get(i).getAsJsonObject();
			String name = obj.get("Name").toString();
			name = name.replace("\"", "");
			LOGGER.info(name);
		}
	}

	

	
	private RallyRestApi getRallyRestApi() throws Exception {

		URI server = new URI("https://rally1.rallydev.com");
		return new RallyRestApi(server, apikey);
	}
	
	private String getCentralTime() {
		
		Instant nowUtc = Instant.now();
		ZoneId usCentral = ZoneId.of("US/Central");
		ZonedDateTime centralTime = ZonedDateTime.ofInstant(nowUtc, usCentral);
		return centralTime.format(DATE_TIME_FORMATTER);
	}
	
	public static void main(String args[]) throws Exception {
		RallyController r = new RallyController();
		/**List<TimeEntry> timeEntryList =  r.process("Brainiacs", "2019-05-29");
		String result = r.constructResultString("Brainiacs", "2019-05-29", timeEntryList);
		
		LOGGER.info(result);*/
		
//		LOGGER.info(getUsage());
		
//		r.printAllProjects(r.getRallyRestApi());
		
		LOGGER.info(r.getTodaysDate());
		LOGGER.info(r.getCentralTime());
		
	    
	}
	
}
