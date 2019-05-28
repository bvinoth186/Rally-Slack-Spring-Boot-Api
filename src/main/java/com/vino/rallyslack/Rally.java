package com.vino.rallyslack;

import java.util.List;

public class Rally {
	
	private String message;
	
	private List<TimeEntry> timeList;

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public List<TimeEntry> getTimeList() {
		return timeList;
	}

	public void setTimeList(List<TimeEntry> timeList) {
		this.timeList = timeList;
	}
	
	
	
	

}
