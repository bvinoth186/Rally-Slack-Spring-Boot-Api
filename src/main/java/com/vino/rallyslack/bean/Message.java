package com.vino.rallyslack.bean;

public class Message {
	
	private String response_type;
	
	private String text;
	
	

	public Message(String response_type, String text) {
		super();
		this.response_type = response_type;
		this.text = text;
	}

	public String getResponse_type() {
		return response_type;
	}

	public void setResponse_type(String response_type) {
		this.response_type = response_type;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
	
	
	

}
