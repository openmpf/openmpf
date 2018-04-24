 /******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

 import org.mitre.mpf.mvc.model.PropertyModel;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

// NOTE: Don't use @Scope("request") because this class should be treated as a singleton.

@Controller
@Scope("singleton")
@Profile("website")
public class AdminPropertySettingsController 
{	
	private static final Logger log = LoggerFactory.getLogger(AdminPropertySettingsController.class);

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	private MpfService mpfService;

	@ResponseBody
	@RequestMapping(value = "/properties", method = RequestMethod.GET)
	public List<PropertyModel> getProperties() {
		return propertiesUtil.getCustomProperties();
	}


	@ResponseBody
	@RequestMapping(value = "/properties", method = RequestMethod.PUT)
	public void saveProperties(@RequestBody List<PropertyModel> propertyModels, HttpServletRequest request) {
		if (!LoginController.getAuthenticationModel(request).isAdmin()) {
			throw new IllegalStateException("A non-admin tried to modify properties.");
		}

		if (propertyModels.isEmpty()) {
			return;
		}

		propertiesUtil.setAndSaveCustomProperties(propertyModels);

		mpfService.addStandardSystemMessage("eServerPropertiesChanged");
	}


	//gets the current default job priority value
	@RequestMapping(value = "/properties/job-priority", method = RequestMethod.GET)
	@ResponseBody
	public int getDefaultJobPriority() {
		return propertiesUtil.getJmsPriority();
	}
}
