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

 import static java.util.stream.Collectors.toList;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
 import java.util.stream.Collectors;
 import javax.servlet.http.HttpServletRequest;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.mitre.mpf.mvc.model.PropertyModel;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.service.PipelineService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

// swagger includes
 @Api(value = "properties", description = "Properties get and save")

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

	@Autowired
	private PipelineService pipelineService;

    /**
     * Get the PropertyModel associated with the specified property key.
     * @param propertyKey system property key to search for.
     * @return PropertyModel associated with the specified property key or null if not found.
     * @throws IOException
     */
    @ApiOperation(value = "Gets the specified system property", produces = "application/json", response=PropertyModel.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successful response"),
        @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @RequestMapping(value = "/property", method = RequestMethod.GET)
	public PropertyModel getProperty(@RequestParam(value = "propertyKey") String propertyKey) {

        List<PropertyModel> customProperties = propertiesUtil.getCustomProperties();
        Optional<PropertyModel> propertyModel = customProperties.stream().filter(e -> e.getKey().equals(propertyKey)).findFirst();

        return propertyModel.orElse(null);
    }

    /**
     * Check all properties to see if any immutable system properties have changed and a restart is required.
     * @return true if any immutable system properties have changed and a restart is required, false otherwise.
     * @throws IOException
     */
    @ApiOperation(value = "Returns true if any immutable system properties have changed and a restart is required, false otherwise.", produces = "application/json", response=Boolean.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successful response"),
        @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @RequestMapping(value = "/restartRequired", method = RequestMethod.GET)
    public boolean isRestartRequired() {
         return checkForRestartRequired();
    }

    /**
     * Check to see if a change to any of the immutable system properties requires a restart to apply the change..
     * @return Returns true if any immutable system properties have changed and a restart is required, false otherwise
     */
    private boolean checkForRestartRequired() {

        // Get an updated list of property models. Each element contains current value. Check the flags to see if WFM restart is required to apply any change.
        Optional<PropertyModel> propertyModel = propertiesUtil.getCustomProperties().stream().filter(pm -> pm.getNeedsRestart()).findFirst();
        return propertyModel.isPresent();

    }

    @ApiOperation(value = "Gets a list of system properties. If optional parameter whichPropertySet is not specified or is set to 'all', then all system properties are returned. "
        + "If whichPropertySet is 'mutable', then only the system properties that may be changed without OpenMPF restart are returned. "
        + "If whichPropertySet is 'immutable', then only the system properties that require restart of OpenMPF to apply property changes are returned.",
        produces = "application/json", response=PropertyModel.class, responseContainer="List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successful response"),
        @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @RequestMapping(value = "/properties", method = RequestMethod.GET)
    public List<PropertyModel> getProperties(@RequestParam(value = "whichPropertySet", required = false, defaultValue="all") String whichPropertySet) throws IOException {

        if ( whichPropertySet.equalsIgnoreCase("immutable") ) {

            // Get an updated list of property models. Each element contains current value. Return only the immutable system properties by
            // filtering out the mutable detection properties from the list.
            ImmutableConfiguration detectionSystemProperties = propertiesUtil.getDetectionConfiguration();
            log.info("getProperties(immutable), debug: dumping detectionSystemProperties keys");
            detectionSystemProperties.getKeys().forEachRemaining( key -> {
                log.info("getProperties(immutable), debug: processing detectionSystemProperties key " + key);
            } );
            return propertiesUtil.getCustomProperties().stream().peek(pm -> {
                log.info("getProperties(immutable), debug: pm is "+pm);
                log.info("getProperties(immutable), debug: detectionSystemProperties.containsKey(" + pm.getKey() + ") is "+detectionSystemProperties.containsKey(pm.getKey()));
            }).filter(pm -> !detectionSystemProperties.containsKey(pm.getKey())).collect(toList());

        } else if ( whichPropertySet.equalsIgnoreCase("mutable") ) {

            // Get an updated list of property models. Each element contains current value. Return only the mutable system properties by
            // filtering out the immutable detection properties from the list.
            ImmutableConfiguration detectionSystemProperties = propertiesUtil.getDetectionConfiguration();
            log.info("getProperties(mutable), debug: dumping detectionSystemProperties keys");
            detectionSystemProperties.getKeys().forEachRemaining( key -> {
                log.info("getProperties(mutable), debug: processing detectionSystemProperties key " + key);
            } );
            return propertiesUtil.getCustomProperties().stream().peek(pm -> {
                log.info("getProperties(mutable), debug: pm is "+pm);
                log.info("getProperties(mutable), debug: detectionSystemProperties.containsKey(" + pm.getKey() + ") is "+detectionSystemProperties.containsKey(pm.getKey()));
            }).filter(pm -> detectionSystemProperties.containsKey(pm.getKey())).collect(toList());

        } else {
            // by default, return all of the system properties - updated to contain current value.
            return propertiesUtil.getCustomProperties();
        }

    }

    @ApiOperation(value = "Saves the passed list of system properties. Note that the returned list may contain PropertyModels that may have a updated value of needsRestart.",
        produces = "application/json", response=PropertyModel.class, responseContainer="List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successful response"),
        @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
	@RequestMapping(value = "/properties", method = RequestMethod.PUT)
    /** Call this method to save system properties that have changed to the custom mpf properties file.
     * If any detection system properties have changed, that are identified as changeable without OpenMPF restart,
     * then update those detection system properties via PropertiesUtil. The updated detection system properties will also
     * be stored in REDIS, so updated values can be used in the construction of new pipelines (created using newly created tasks).
     * Add system message if a restart of OpenMPF is required for any other system property that is changed and
     * requires a restart to apply the change.
     * @param propertyModels list of system properties that have changed since OpenMPF startup.
     * @return updated property models are returned since the needsRestart property may have changed.
     */
	public List<PropertyModel> saveProperties(@RequestBody List<PropertyModel> propertyModels, HttpServletRequest request) {

		if (!LoginController.getAuthenticationModel(request).isAdmin()) {
			throw new IllegalStateException("A non-admin tried to modify properties.");
		}

		if (propertyModels.isEmpty()) {
			return propertyModels;
		}

        // Call method to iterate through the system properties and update any properties that may have been updated on the UI,
		propertiesUtil.setAndSaveCustomProperties(propertyModels);

		// After any dynamic detection system properties have been updated, then refresh the algorithm default values so
        // they will be applied to new pipelines
        pipelineService.refreshAlgorithmDefaultValues();

		// Add system message if a restart of OpenMPF is required.
        if ( checkForRestartRequired() ) {
            mpfService.addStandardSystemMessage("eServerPropertiesChanged");
        }

        // Get an updated list of property models. Adjust the returned list of PropertyModels so they will indicate
        // whether or not a WFM restart is required to apply a change.
        List<PropertyModel> updatedPropertyModels = propertiesUtil.getCustomProperties().stream().filter(updatedPM -> {
            // filter the returned list to only include the set of properties that were just saved.
            boolean isFound = false;
            for ( int i=0; i<propertyModels.size() && !isFound; i++ ) {
                isFound = updatedPM.getKey().equals(propertyModels.get(i).getKey());
            }
            if ( isFound ) {
                log.info("AdminPropertySettingsController.saveProperties: found updatedPM=" + updatedPM);
            }
            return isFound;
        }).collect(Collectors.toList());

        // Note that the returned list may contain PropertyModels that may have a updated value of needsRestart.
        log.info("AdminPropertySettingsController.saveProperties: returning updatedPropertyModels with size=" + updatedPropertyModels.size());
        return updatedPropertyModels;
	}

	//gets the current default job priority value
	@RequestMapping(value = "/properties/job-priority", method = RequestMethod.GET)
	@ResponseBody
	public int getDefaultJobPriority() {
		return propertiesUtil.getJmsPriority();
	}
}