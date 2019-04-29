package com.dialogflow.services;


import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.adaptors.dialogflow.dialogflowadaptor.BotResponse;
import com.adaptors.dialogflow.dialogflowadaptor.UserRequest;
import com.google.api.gax.core.FixedCredentialsProvider;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2beta1.DetectIntentRequest;
import com.google.cloud.dialogflow.v2beta1.DetectIntentResponse;
import com.google.cloud.dialogflow.v2beta1.KnowledgeAnswers;
import com.google.cloud.dialogflow.v2beta1.KnowledgeBaseName;
import com.google.cloud.dialogflow.v2beta1.QueryInput;
import com.google.cloud.dialogflow.v2beta1.QueryParameters;
import com.google.cloud.dialogflow.v2beta1.QueryResult;
import com.google.cloud.dialogflow.v2beta1.SessionName;
import com.google.cloud.dialogflow.v2beta1.SessionsClient;
import com.google.cloud.dialogflow.v2beta1.SessionsSettings;
import com.google.cloud.dialogflow.v2beta1.TextInput;
import com.google.cloud.dialogflow.v2beta1.KnowledgeAnswers.Answer;
import com.google.cloud.dialogflow.v2beta1.TextInput.Builder;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

@Service
public class DialogflowService {
	
	
	
	private final Map<String,SessionsSettings> settingsByAgent= new HashMap<>();
	
	
	Logger log = LoggerFactory.getLogger(DialogflowService.class);
	
	@Autowired
	private DialogflowConfig config;
	
	
	
	
public BotResponse detectIntentText(UserRequest request,DialogflowConfig.AgentConfig agentConfig) throws Exception {
		
		
		SessionsSettings sessionsSettings =
				    SessionsSettings.newBuilder()
				         .setCredentialsProvider(FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(new FileInputStream(agentConfig.getCredentialfile()))))
				        .build();
		try (SessionsClient sessionsClient = SessionsClient.create(sessionsSettings)) {
		      // Set the session name using the sessionId (UUID) and projectID (my-project-id)
			    SessionName session = SessionName.of(agentConfig.getProjectId(), request.getSessionId());
			    log.info("Session Path: {}", session.toString());
			    
		        Builder textInput = TextInput.newBuilder()
		        		                         .setText(request.getUserQuery())
		        		                         .setLanguageCode(!StringUtils.isEmpty(request.getLanguageCode())?request.getLanguageCode():agentConfig.getLanguageCode());

		        // Build the query with the TextInput
		        
		        QueryParameters queryParameters=null;
		        
		        if(!CollectionUtils.isEmpty(agentConfig.getKnowledgeBases())) {
		        	      
		        	      com.google.cloud.dialogflow.v2beta1.QueryParameters.Builder newBuilder = QueryParameters.newBuilder();
		        	      
		        	      agentConfig.getKnowledgeBases().forEach(kb->{
		        	    	  
		        	    	  KnowledgeBaseName knowledgeBaseName= KnowledgeBaseName.of(agentConfig.getProjectId(), kb.getName());
		        	    	  newBuilder.addKnowledgeBaseNames(knowledgeBaseName.toString());
		        	    	  log.info("Knowledge base name {} ",knowledgeBaseName.toString());
		        	    	  
		        	      });
		        	      
		        	      queryParameters=newBuilder.build();
		        		                		
		        }
		        
		        QueryInput queryInput = QueryInput.newBuilder()
		        		                              .setText(textInput).build();
		        
		        
		        DetectIntentRequest detectIntentRequest =
			            DetectIntentRequest.newBuilder()
			                .setSession(session.toString())
			                .setQueryInput(queryInput)
			                .setQueryParams(queryParameters)
			                .build();

		        // Performs the detect intent request
		        DetectIntentResponse response = sessionsClient.detectIntent(detectIntentRequest);

		        // Display the query result
		        QueryResult queryResult = response.getQueryResult();

		        
		        log.info("Query Text:{}", queryResult.getQueryText());
		        log.info("Detected Intent: {} (confidence: {})\n",
		            queryResult.getIntent().getDisplayName(), queryResult.getIntentDetectionConfidence());
		        log.info("Fulfillment Text: {}", queryResult.getFulfillmentText());
		        
		        
		        BotResponse botResponse= new BotResponse();
		        botResponse.setSessionId(request.getSessionId());
		        botResponse.setFullfillmentText(queryResult.getFulfillmentText());
		        botResponse.setActionName(queryResult.getAction());
		        botResponse.setIntentName(queryResult.getIntent().getDisplayName());
		        
		        Struct parameters = queryResult.getParameters();
		        
		     
		        
		        Map<FieldDescriptor, Object> fields = parameters.getAllFields();
		        
		        fields.forEach((k,v)->{
		        	
		        	log.info("Key : {}  , value : {}" ,k,v);
		        	
		        	if(k.isMapField()) {
		        		
		        	 if (v instanceof List) {
							List<MapEntry<String,Value>> entityList = (List) v;
							
							entityList.forEach(val->{
							
							 	botResponse.addEntity(val.getKey(),val.getValue().getStringValue());
								log.info("Key : {}  , value : {} , is Map Field? {}" , val.getKey(), val.getValue().getStringValue(),k.isMapField());
								
							});
				
		        	 
		        	 }
		        		
		       }
		        	
		        	
		        });
		        
		        
		        //Handle Custom Payloads in response messages
		        
		        if(queryResult.getFulfillmentMessagesCount()!=0) {
		        	
		        	         queryResult.getFulfillmentMessagesList().forEach(message -> {
		        	        	 
		        	        	 botResponse.addPayloadData(buildPayload(message.getPayload().getAllFields()));
		        	        	 
		        	         });
		        	
		      	
		        }  
		        
		        KnowledgeAnswers knowledgeAnswers = queryResult.getKnowledgeAnswers();
		        for (Answer answer : knowledgeAnswers.getAnswersList()) {
		          log.info(" Question {}- Answer: {}", answer.getFaqQuestion(),answer.getAnswer());
		          log.info(" - Confidence: {}", answer.getMatchConfidence());
		          
		          botResponse.addKnowledgeAnswers(answer.getFaqQuestion(),answer.getAnswer(), answer.getMatchConfidence());
		        }
		        
		        
		        
		        return botResponse;
		        
		       
		        
		      
		    }
		
		
	}
	
	
	
	
public Map<String,Object> buildPayload(Map<FieldDescriptor, Object> fields) {
	  
	Map<String,Object> responseMap=new HashMap<>();
	 
	fields.forEach((k,v)->{
    	
	        
    	//System.out.format("Key : %s  , value : %s , class of Val- %s \n" ,k,v,v.getClass() );
    	
    	
    	if(k.isMapField()) {
    		
    	 if (v instanceof List) {
					List<MapEntry<String,Value>> entityList = (List) v;
					
					entityList.forEach(val->{
					
						if(val.getValue().hasStructValue()) {
							
							System.out.format("Key : %s :",val.getKey());
							//printFields( val.getValue().getStructValue().getAllFields());
							responseMap.put(val.getKey(),this.buildPayload(val.getValue().getStructValue().getAllFields()));
							
							
						}else {
							
							responseMap.put(val.getKey(), val.getValue().getStringValue());
					 	
						System.out.format("Key : %s  , value : %s , is Map Field? %s \n" , val.getKey(), val.getValue().getStringValue(),k.isMapField());
						}
					});
		
    	 	}
    		
    	 }
    });
	
	return responseMap;
}
	
	
	
	
	
	
	
	
	
	
	
	public BotResponse detectIntentTextsByAgent(UserRequest request,String agentName) throws Exception {
		
		
		SessionsSettings sessionsSettings =
				    SessionsSettings.newBuilder()
				         .setCredentialsProvider(FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(new FileInputStream("/Users/praveen/VA-FRAMEWORK/Dialogflow-credentials/testagent-ae789-2570630db9dd.json"))))
				        .build();
		try (SessionsClient sessionsClient = SessionsClient.create(sessionsSettings)) {
		      // Set the session name using the sessionId (UUID) and projectID (my-project-id)
			    SessionName session = SessionName.of(config.getDefaultAgent().getProjectId(), request.getSessionId());
			    log.info("Session Path: {}", session.toString());
			    
		        Builder textInput = TextInput.newBuilder().setText(request.getUserQuery()).setLanguageCode(!StringUtils.isEmpty(request.getLanguageCode())?request.getLanguageCode():config.getDefaultLanguageCode());

		        // Build the query with the TextInput
		        
		        
		        QueryInput queryInput = QueryInput.newBuilder().setText(textInput).build();

		       
		        
		        // Performs the detect intent request
		        DetectIntentResponse response = sessionsClient.detectIntent(session, queryInput);

		        // Display the query result
		        QueryResult queryResult = response.getQueryResult();

		        
		        log.info("Query Text:{}", queryResult.getQueryText());
		        log.info("Detected Intent: {} (confidence: {})\n",
		            queryResult.getIntent().getDisplayName(), queryResult.getIntentDetectionConfidence());
		        log.info("Fulfillment Text: {}", queryResult.getFulfillmentText());
		        
		        
		        BotResponse botResponse= new BotResponse();
		        botResponse.setSessionId(request.getSessionId());
		        botResponse.setFullfillmentText(queryResult.getFulfillmentText());
		        botResponse.setActionName(queryResult.getAction());
		        
		        Struct parameters = queryResult.getParameters();
		        
		     
		        
		        Map<FieldDescriptor, Object> fields = parameters.getAllFields();
		        
		        fields.forEach((k,v)->{
		        	
		        	log.info("Key : {}  , value : {}" ,k,v);
		        	
		        	if(k.isMapField()) {
		        		
		        	 if (v instanceof List) {
							List<MapEntry<String,Value>> entityList = (List) v;
							
							entityList.forEach(val->{
								
								
							 	botResponse.addEntity(val.getKey(),val.getValue().getStringValue());
								log.info("Key : {}  , value : {} , is Map Field? {}" , val.getKey(), val.getValue().getStringValue(),k.isMapField());
								
							});
				
		        	 
		        	 }
		        		
		       }
		        	
		        	
		        });
		        
		       
		        
		        return botResponse;
		        
		       
		        
		      
		    }
		
		
	}
	
	public  BotResponse detectIntentTexts( UserRequest request) throws Exception {
		    // Instantiates a client
		    try (SessionsClient sessionsClient = SessionsClient.create()) {
		      // Set the session name using the sessionId (UUID) and projectID (my-project-id)
			    SessionName session = SessionName.of("testagent-ae789", request.getSessionId());
			    log.info("Session Path: {}", session.toString());
			    
		        Builder textInput = TextInput.newBuilder().setText(request.getUserQuery()).setLanguageCode(!StringUtils.isEmpty(request.getLanguageCode())?request.getLanguageCode():config.getDefaultLanguageCode());

		        // Build the query with the TextInput
		        QueryInput queryInput = QueryInput.newBuilder().setText(textInput).build();

		        // Performs the detect intent request
		        DetectIntentResponse response = sessionsClient.detectIntent(session, queryInput);

		        // Display the query result
		        QueryResult queryResult = response.getQueryResult();

		        
		        log.info("Query Text:{}", queryResult.getQueryText());
		        log.info("Detected Intent: {} (confidence: {})\n",
		            queryResult.getIntent().getDisplayName(), queryResult.getIntentDetectionConfidence());
		        log.info("Fulfillment Text: {}", queryResult.getFulfillmentText());
		        
		        
		        BotResponse botResponse= new BotResponse();
		        botResponse.setSessionId(request.getSessionId());
		        botResponse.setFullfillmentText(queryResult.getFulfillmentText());
		        botResponse.setActionName(queryResult.getAction());
		        
		        Struct parameters = queryResult.getParameters();
		        
		     
		        
		        Map<FieldDescriptor, Object> fields = parameters.getAllFields();
		        
		        fields.forEach((k,v)->{
		        	
					        	log.info("Key : {}  , value : {}" ,k,v);
					        	
					        	if(k.isMapField()) {
					        		
						        	 if (v instanceof List) {
											List<MapEntry<String,Value>> entityList = (List) v;
											
											entityList.forEach(val->{
												
											 	botResponse.addEntity(val.getKey(),val.getValue().getStringValue());
												log.info("Key : {}  , value : {} , is Map Field? {}" , val.getKey(), val.getValue().getStringValue(),k.isMapField());
												
											});
								 }
					       }
		        });
		        
		        
		        KnowledgeAnswers knowledgeAnswers = queryResult.getKnowledgeAnswers();
		        for (Answer answer : knowledgeAnswers.getAnswersList()) {
		          log.info(" Question {}- Answer: {}", answer.getFaqQuestion(),answer.getAnswer());
		          log.info(" - Confidence: {}", answer.getMatchConfidence());
		          
		          botResponse.addKnowledgeAnswers(answer.getFaqQuestion(),answer.getAnswer(), answer.getMatchConfidence());
		        }
		        
		        
		        
		        return botResponse;
		        
		       
		        
		      
		    }
		  }
	
	
	
	
	  public  KnowledgeResponse detectIntentKnowledge(UserRequest userRequest)
	      throws Exception {
	    // Instantiates a client
	    try (SessionsClient sessionsClient = SessionsClient.create()) {
	      // Set the session name using the sessionId (UUID) and projectID (my-project-id)
	      SessionName session = SessionName.of(config.getDefaultAgent().getProjectId(), userRequest.getSessionId());
	      System.out.println("Session Path: " + session.toString());

	       
	        Builder textInput = TextInput.newBuilder().setText(userRequest.getUserQuery()).setLanguageCode(!StringUtils.isEmpty(userRequest.getLanguageCode())?userRequest.getLanguageCode():config.getDefaultLanguageCode());
	        // Build the query with the TextInput
	        QueryInput queryInput = QueryInput.newBuilder().setText(textInput).build();

	        //KnowledgeBaseName knowledgeBaseName = KnowledgeBaseName.of(config.getDefaultAgent().getProjectId(), config.getDefaultAgent().getKnowledgeBases()getKnowledgeBaseId());
	        QueryParameters queryParameters =
	            QueryParameters.newBuilder()
	                //.addKnowledgeBaseNames(knowledgeBaseName.toString())
	                .build();

	        DetectIntentRequest detectIntentRequest =
	            DetectIntentRequest.newBuilder()
	                .setSession(session.toString())
	                .setQueryInput(queryInput)
	                .setQueryParams(queryParameters)
	                .build();
	        // Performs the detect intent request
	        DetectIntentResponse response = sessionsClient.detectIntent(detectIntentRequest);

	        // Display the query result
	        QueryResult queryResult = response.getQueryResult();

	        log.info("Knowledge results:");
	        
	        log.info("Query Text: {}", queryResult.getQueryText());
	        log.info("Detected Intent: {} (confidence: {})", queryResult.getIntent().getDisplayName(), queryResult.getIntentDetectionConfidence());
	        log.info("Fulfillment Text: {}", queryResult.getFulfillmentText());
	        
	        KnowledgeResponse knowledgeResponse = new KnowledgeResponse();
	        
	        knowledgeResponse.setUserQuery(queryResult.getQueryText());
	        knowledgeResponse.setIntentName(queryResult.getIntent().getDisplayName());
	        knowledgeResponse.setFullfillmentText(queryResult.getFulfillmentText());
	        
	        
	        KnowledgeAnswers knowledgeAnswers = queryResult.getKnowledgeAnswers();
	        for (Answer answer : knowledgeAnswers.getAnswersList()) {
	          log.info(" Question {}- Answer: {}", answer.getFaqQuestion(),answer.getAnswer());
	          log.info(" - Confidence: {}", answer.getMatchConfidence());
	          
	          knowledgeResponse.addKnowledgeAnswers(answer.getFaqQuestion(),answer.getAnswer(), answer.getMatchConfidence());
	        }
	        
	        return knowledgeResponse;
	      
	    }
	  }

}
