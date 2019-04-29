package com.adaptors.dialogflow.dialogflowadaptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.dialogflow.services.ConfigException;
import com.dialogflow.services.DialogflowConfig;
import com.dialogflow.services.DialogflowService;
import com.dialogflow.services.KnowledgeResponse;
import com.dialogflow.services.DialogflowConfig.AgentConfig;

@RestController
public class DialogflowController {
	
	Logger log = LoggerFactory.getLogger(DialogflowController.class);

	@Autowired
	DialogflowService dialogflowService;
	
	@Autowired
	DialogflowConfig config;
	
	
	@GetMapping("/showConfig")
	public DialogflowConfig showConfig(){
		
		return config;
		
	}
	
	@PostMapping("/userQuery")
	public BotResponse handleUserQuery(@RequestBody UserRequest userRequest) {
		
		log.info("Recieved user request with session-id : {} and queryText : {} ", userRequest.getSessionId(),userRequest.getUserQuery());
		try {
			return dialogflowService.detectIntentText(userRequest,config.getDefaultAgent());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new BotResponse(userRequest.getSessionId(),"Something went wrong!");
		}
	}
	
	
	@PostMapping("/knowledgeQuery")
	public KnowledgeResponse handleknowledgeQuery(@RequestBody UserRequest userRequest) {
		
		log.info("Recieved user request with session-id : {} and queryText : {} ", userRequest.getSessionId(),userRequest.getUserQuery());
		try {
			return dialogflowService.detectIntentKnowledge(userRequest);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new KnowledgeResponse(userRequest.getSessionId(),"Something went wrong!");
		}
	}
	
	

	@PostMapping("/{agentName}/userQueryByAgent")
	public BotResponse handleUserQueryByAgent(@PathVariable String agentName, @RequestBody UserRequest userRequest) {
		
		log.info("Recieved user request for agent {}  with session-id : {} and queryText : {} ", agentName,userRequest.getSessionId(),userRequest.getUserQuery());
		try {
			
			
			
			if(!CollectionUtils.isEmpty(config.getAgents())) {
				
				AgentConfig agentConfig=config.getAgents().stream().filter(c-> c.getAgentName().equalsIgnoreCase(agentName)).findAny().orElse(null);
				
				if(agentConfig!=null) {
					
					return dialogflowService.detectIntentText(userRequest,agentConfig); 
				}else {
					
					throw new ConfigException("No Configuration for agent with name "+agentName + " found");
				}
				
				
			}else {
				
				throw new ConfigException("No Configuration for agent with name "+agentName + " found");
			}
			
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			if(e instanceof ConfigException) {
				throw (ConfigException)e;
			}
			
			e.printStackTrace();
			
			return new BotResponse(userRequest.getSessionId(),"Something went wrong!");
		}
		
		
		
	}
	
	
}
