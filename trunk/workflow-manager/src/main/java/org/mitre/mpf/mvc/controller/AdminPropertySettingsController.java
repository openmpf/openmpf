 /******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

package org.mitre.mpf.mvc.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.mitre.mpf.mvc.model.AuthenticationModel;
import org.mitre.mpf.mvc.model.CompleteMpfPropertiesModel;
import org.mitre.mpf.mvc.model.MpfPropertiesModel;
import org.mitre.mpf.mvc.model.SavePropertiesResult;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

// NOTE: Don't use @Scope("request") because this class should be treated as a singleton.

@Controller
@Scope("singleton")
@Profile("website")
public class AdminPropertySettingsController 
{	
	private static final Logger log = LoggerFactory.getLogger(AdminPropertySettingsController.class);

	public static final String DEFAULT_ERROR_VIEW = "error";
	
	//@Controllers are singletons
	private boolean propertiesModified = false;
	//keep track of the modified properties and the original value
	private Map<String, Object> modifiedPropertiesMap = new HashMap<String, Object>();
	//why not keep the properties in order
	private Map<String,Object> readPropertiesMap = new TreeMap<String,Object>();

	@Autowired
	@Qualifier(PropertiesUtil.REF)
	private PropertiesUtil propertiesUtil;

	@Autowired
	private MpfService mpfService;

	@RequestMapping(value = "/adminPropertySettingsTemplate", method = RequestMethod.GET)
	public ModelAndView adminPropertySettingsTemplate(HttpServletRequest request) {		
		return new ModelAndView("admin_property_settings_template");	
	}	
	
	@ResponseBody
	@RequestMapping(value = "/properties/model", method = RequestMethod.GET)
	public CompleteMpfPropertiesModel getPropertiesModel() {	
		
		CompleteMpfPropertiesModel propertiesModel = new CompleteMpfPropertiesModel();	
		
		Resource resource = new ClassPathResource("/properties/mpf.properties");
		try {
			Properties props = PropertiesLoaderUtils.loadProperties(resource);
			Set<String> propertyNames = props.stringPropertyNames();
			for(String propName : propertyNames) {
				String propValue = props.getProperty(propName);
				readPropertiesMap.put(propName, propValue);
			}
		} catch (IOException e) {
			log.error("Failed to read mpf.properties with exception: {}", e);
		}
		
		//now load the mpf-custom properties and replace anything in the mpf.properties
		resource = new ClassPathResource("/properties/mpf-custom.properties");
		//the mpf-custom.properties resource should exist, but could be removed by a user
		if(resource != null && resource.exists()) {
			try {
				Properties props = PropertiesLoaderUtils.loadProperties(resource);
				Set<String> propertyNames = props.stringPropertyNames();
				for(String propName : propertyNames) {
					String propValue = props.getProperty(propName);
					//replace readPropertiesMap with the custom property
					if(readPropertiesMap.containsKey(propName)) {
						readPropertiesMap.put(propName, propValue);
					}
				}
			} catch (IOException e) {
				log.error("Failed to read mpf-custom.properties with exception: {}", e);
				propertiesModel.setErrorMessage("Failed to read mpf-custom.properties with an exception. Properties will not be saved!");
			}
		} else {
			log.error("The mpf-custom.properties file must not exist in the properties directory.");
			propertiesModel.setErrorMessage("Failed to read mpf-custom.properties. Please make sure that the file exists in the properties directory. "
					+ "Properties will not be saved!");
		}		
		
		propertiesModel.setPropertiesModified(propertiesModified);
		propertiesModel.setReadPropertiesMap(readPropertiesMap);
		propertiesModel.setModifiedPropertiesMap(modifiedPropertiesMap);						
		
		return propertiesModel;		
	}	
	
	@RequestMapping(value = "/properties/save", method = RequestMethod.POST)
	@ResponseBody
	public SavePropertiesResult saveMpfProperties(@RequestBody List<MpfPropertiesModel> mpfProperties, 
			HttpServletRequest httpServletRequest) {
		
		SavePropertiesResult savePropertiesResult = new SavePropertiesResult(false);
		//check the user authentication, only an admin should be able to access this url
		//a highly unlikely security issue could arise if the client side is modified in a way to POST to this url with a JSON List<MpfPropertiesModel>
        AuthenticationModel authenticationModel = LoginController.getAuthenticationModel(httpServletRequest);
        if(authenticationModel.getUserPrincipalName() != null && authenticationModel.isAuthenticated() && authenticationModel.isAdmin()) {
        	log.debug("Admin user with name '{}' is attempting to modify mpf properties.", authenticationModel.getUserPrincipalName());
        } else {
        	log.error("Invalid/non-admin user with name '{}' is attempting to modify mpf properties.", authenticationModel.getUserPrincipalName());
        	savePropertiesResult.setErrorMessage("Properties not saved! You do not have the proper user privileges to modify the mpf properties! Please log in as an admin user.");
        	//do not continue!
        	return savePropertiesResult;
        }				
		
		Resource resource = new ClassPathResource("/properties/mpf-custom.properties");
		try {
			Properties props = null;
			if(resource != null && resource.exists()) {
				props = PropertiesLoaderUtils.loadProperties(resource);
			} 
			
			if(props != null) {
				//set this to true - the value will be set back to false later if the property values 
				//are set back to the read in values
				propertiesModified = true;
	
				for(MpfPropertiesModel mpfPropertiesModel : mpfProperties) {
					//leaving the value as object in case we want to deal with different types in the future
					String propName = mpfPropertiesModel.getName();
					String propValue = (String) mpfPropertiesModel.getValue();
					props.setProperty(propName, propValue);
					//log.info("Set propery with name: {} to the value: {}", propName, propValue);
					//store the modified prop and the original value
					if(!modifiedPropertiesMap.containsKey(propName)) {
						//store the read (original value if never modified) property value at the time if we want to
						//allow the user to revert the value in the future or this map could be use to prevent the need of restart
						//message if the values are changed back to the ones originally read in
						modifiedPropertiesMap.put(propName, readPropertiesMap.get(propName));
					} else if( modifiedPropertiesMap.get(propName).equals(propValue) ) { //exist in the map
						//the new value is equal to the read in value and the entry can be removed from the map - the property
						//will no longer be marked as modified
						log.debug("removing modified entry for property with name: {}", propName);
						modifiedPropertiesMap.remove(propName);					
					}  
				}	
				
				log.debug("modifiedPropertiesMap size: {}", modifiedPropertiesMap.size());			
				//if the map is now empty then don't require a restart!
				if(modifiedPropertiesMap.isEmpty()) {
					propertiesModified = false;
					mpfService.deleteStandardSystemMessage("eServerPropertiesChanged");
				}
				else {
					mpfService.addStandardSystemMessage("eServerPropertiesChanged");
				}
				//properties have to be rewritten even when propertiesModified is set to false, because there
				//are still changes to be written even if to write a property value back to the original value
				try {
					File file = resource.getFile();
					props.store(new FileOutputStream(file), "modified");
					log.debug("Successfully saved mpf-custom.properties");
					savePropertiesResult = new SavePropertiesResult(true);
				} catch (IOException e) {
					log.error("Failed to store mpf-custom.properties with exception: {}", e);
					savePropertiesResult.setErrorMessage("Failed to store mpf-custom.properties. Please check server logs for more detail.");
				}				
			} else {
				log.error("Failed to access mpf-custom.properties, make sure the file exists in the properties directory.");
				savePropertiesResult.setErrorMessage("Failed to access mpf-custom.properties, make sure the file exists in the properties directory.");
			}
		} catch (IOException e) {
			log.error("Failed to read mpf-custom.properties with exception: {}", e);
			savePropertiesResult.setErrorMessage("Failed to read mpf-custom.properties. Please check server logs for more detail.");
		}
		
		//in some cases, the server may not have time to return a response from this request
		//before spring automatically restarts the web application
		return savePropertiesResult;
	}
	
	//gets the current default job priority value
	@RequestMapping(value = "/properties/job-priority", method = RequestMethod.GET)
	@ResponseBody
	public int getDefaultJobProprety() {
		return propertiesUtil.getJmsPriority();
	}	
}