/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Profile("!docker")
public class StreamingServiceManagerImpl implements StreamingServiceManager {

	private final PropertiesUtil _propertiesUtil;

	private final ObjectMapper _objectMapper;

	private final List<StreamingServiceModel> _serviceModels;


	@Autowired
	public StreamingServiceManagerImpl(PropertiesUtil propertiesUtil, ObjectMapper objectMapper) {
		_propertiesUtil = propertiesUtil;
		_objectMapper = objectMapper;
		_serviceModels = loadServiceModels();
	}


	@Override
	public List<StreamingServiceModel> getServices() {
		return Collections.unmodifiableList(_serviceModels);
	}


	@Override
	public void addService(StreamingServiceModel newService) {
		boolean serviceAlreadyExists = _serviceModels.stream()
				.anyMatch(sm -> sm.getServiceName().equals(newService.getServiceName()));

		if (serviceAlreadyExists) {
			throw new IllegalStateException(
					"Unable to add StreamingService named " + newService.getServiceName()
					+ ", because a StreamingService with that name already exists.");
		}

		_serviceModels.add(newService);
		save();
	}


	@Override
	public void deleteService(String serviceName) {
		_serviceModels.removeIf(s -> s.getServiceName().equals(serviceName));
		save();
	}


	private static final TypeReference<ArrayList<StreamingServiceModel>> _serviceListTypeRef
			= new TypeReference<ArrayList<StreamingServiceModel>>() { };


	private List<StreamingServiceModel> loadServiceModels() {
		try (InputStream inputStream = _propertiesUtil.getStreamingServices().getInputStream()) {
			return _objectMapper.readValue(inputStream, _serviceListTypeRef);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to load streaming services JSON.", e);
		}
	}


	private void save() {
		try (OutputStream outputStream = _propertiesUtil.getStreamingServices().getOutputStream()) {
			_objectMapper.writerWithDefaultPrettyPrinter()
					.writeValue(outputStream, _serviceModels);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to write to streaming services file.", e);
		}
	}




}
