package com.vino.rallyslack;

public class Task {
	
	private String id;
	
	private String name;
	
	private String notes;
	
	

	public Task(String id, String name, String notes) {
		super();
		this.id = id;
		this.name = name;
		this.notes = notes;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	

}
