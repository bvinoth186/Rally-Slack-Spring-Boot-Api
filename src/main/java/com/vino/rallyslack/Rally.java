package com.vino.rallyslack;

import java.util.List;

public class Rally {
	
	private String message;
	
	private List<TimeEntry> TimeSheetEntries;

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public List<TimeEntry> getTimeSheetEntries() {
		return TimeSheetEntries;
	}

	public void setTimeSheetEntries(List<TimeEntry> timeSheetEntries) {
		TimeSheetEntries = timeSheetEntries;
	}

	
}
