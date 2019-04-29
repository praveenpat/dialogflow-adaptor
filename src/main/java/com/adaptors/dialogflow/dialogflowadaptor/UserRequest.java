package com.adaptors.dialogflow.dialogflowadaptor;

public class UserRequest {
	
	private String sessionId;
	
	private String userQuery;
	
	private String languageCode;
	
	
	public UserRequest() {
		super();
		
	}
	
	public String getLanguageCode() {
		return languageCode;
	}

	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

	

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getUserQuery() {
		return userQuery;
	}

	public void setUserQuery(String userQuery) {
		this.userQuery = userQuery;
	}

}
