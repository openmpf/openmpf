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

package org.mitre.mpf.wfm.service.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static org.mitre.mpf.test.TestUtil.whereArg;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestComponentStateService {

    @InjectMocks
    private ComponentStateServiceImpl _componentStateService;

    @Mock
    private PropertiesUtil _mockProperties;

    @Mock
    private ObjectMapper _mockObjectMapper;

    private String _testPackageName = "TestComponent.tar.gz";
    private String _testComponentName = "TestComponent";
    private File _componentInfoFile = new File("/tmp/whatever/components.json");

    private RegisterComponentModel _testRegisterModel;


    @Before
    public void init() throws IOException {
        MockitoAnnotations.initMocks(this);

        _testRegisterModel = new RegisterComponentModel();
        _testRegisterModel.setComponentName(_testComponentName);
        _testRegisterModel.setPackageFileName(_testPackageName);
        _testRegisterModel.setComponentState(ComponentState.REGISTERED);
        _testRegisterModel.setFullUploadedFilePath(Paths.get(_testPackageName).toAbsolutePath().toString());

        RegisterComponentModel otherRcm = new RegisterComponentModel();
        otherRcm.setComponentName("OtherComponentName");

        RegisterComponentModel otherRcm2 = new RegisterComponentModel();
        otherRcm2.setPackageFileName("Whatever.tar.gz");

        when(_mockObjectMapper.readValue(eq(_componentInfoFile), any(TypeReference.class)))
                .thenReturn(Lists.newArrayList(otherRcm, _testRegisterModel, otherRcm2));


        when(_mockProperties.getComponentInfoJsonFile())
                .thenReturn(_componentInfoFile);

    }


    @Test
    public void canGetAllModels() {
        List<RegisterComponentModel> results = _componentStateService.get();

        Assert.assertEquals(1, results.stream()
                .filter(rcm -> _testComponentName.equals(rcm.getComponentName()))
                .count());

        Assert.assertEquals(3, results.size());
    }


    @Test
    public void canGetByPackageFile() {
        assertCorrectModel(_componentStateService.getByPackageFile(_testPackageName));
    }

    @Test
    public void returnsEmptyWhenNoMatchingPackageName() {
        Assert.assertFalse(_componentStateService.getByPackageFile("foo").isPresent());
    }

    @Test
    public void canGetByComponentName() {
        assertCorrectModel(_componentStateService.getByComponentName(_testComponentName));
    }

    @Test
    public void returnsEmptyWhenNoMatchingComponentName() {
        Assert.assertFalse(_componentStateService.getByComponentName("foo").isPresent());

    }


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void assertCorrectModel(Optional<RegisterComponentModel> rcm) {
        Assert.assertTrue(rcm.isPresent());
        Assert.assertEquals(_testRegisterModel.getComponentName(), rcm.get().getComponentName());
        Assert.assertEquals(_testRegisterModel.getPackageFileName(), rcm.get().getPackageFileName());
    }


    @Test
    public void canUpdate() throws IOException {
        RegisterComponentModel modelToUpdate = new RegisterComponentModel();
        modelToUpdate.setComponentName(_testComponentName);
        modelToUpdate.setComponentState(ComponentState.REGISTER_ERROR);

        _componentStateService.update(modelToUpdate);
        verifySavedModelList(ms -> ms.size() == 3
                && getTargetModel(ms).getComponentState() == ComponentState.REGISTER_ERROR);
    }


    @Test
    public void canReplacePackageState() throws IOException {
        ComponentState oldState = _componentStateService.replacePackageState(_testPackageName, ComponentState.REGISTER_ERROR);

        verifySavedModelList(ms -> ms.size() == 3
                && getTargetModel(ms).getComponentState() == ComponentState.REGISTER_ERROR);

        Assert.assertEquals(oldState, ComponentState.REGISTERED);
    }


    @Test
    public void canReplaceComponentState() throws IOException {
        _componentStateService.replaceComponentState(_testComponentName, ComponentState.REGISTER_ERROR);
        verifySavedModelList(ms -> ms.size() == 3
                && getTargetModel(ms).getComponentState() == ComponentState.REGISTER_ERROR);
    }


    @Test
    public void canRemoveComponent() throws IOException {
        _componentStateService.removeComponent(_testComponentName);
        verifySavedModelList(ms -> ms.size() == 2 && getTargetModel(ms) == null);
    }


    @Test
    public void canRemovePackage() throws IOException {
        _componentStateService.removePackage(_testPackageName);
        verifySavedModelList(ms -> ms.size() == 2 && getTargetModel(ms) == null);
    }


    @Test
    public void canAddEntryForUploadedPackage() throws IOException {
        String newPackage = "NewComponent.tar.gz";
        _componentStateService.addEntryForUploadedPackage(Paths.get(newPackage));

        verifySavedModelList(ms -> ms.size() == 4 &&
                getModel(newPackage, ms).getComponentState() == ComponentState.UPLOADED);
    }


    @Test(expected = IllegalStateException.class)
    public void throwsExceptionWhenAddingDuplicateModel() {
        _componentStateService.addEntryForUploadedPackage(Paths.get(_testPackageName));
    }

    @Test
    public void canAddUploadErrorEntry() throws IOException {
        String newPackage = "NewComponent.tar.gz";
        _componentStateService.addUploadErrorEntry(newPackage);

        verifySavedModelList(ms -> ms.size() == 4
                && getModel(newPackage, ms).getComponentState() == ComponentState.UPLOAD_ERROR);

    }


    private void verifySavedModelList(Predicate<List<RegisterComponentModel>> predicate) throws IOException {
        //noinspection unchecked
        verify(_mockObjectMapper)
                .writeValue(eq(_componentInfoFile),
                        whereArg(objs -> predicate.test((List<RegisterComponentModel>) objs)));
    }



    private RegisterComponentModel getTargetModel(List<RegisterComponentModel> models) {
        List<RegisterComponentModel> matchingModels = models
                .stream()
                .filter(rcm -> _testComponentName.equals(rcm.getComponentName()))
                .collect(toList());

        Assert.assertTrue(matchingModels.size() < 2);
        return matchingModels
                .stream()
                .findAny()
                .orElse(null);
    }


    private static RegisterComponentModel getModel(String packageName, List<RegisterComponentModel> models) {
        List<RegisterComponentModel> matchingModels = models
                .stream()
                .filter(rcm -> packageName.equals(rcm.getPackageFileName()))
                .collect(toList());

        Assert.assertTrue(matchingModels.size() < 2);
        return matchingModels
                .stream()
                .findAny()
                .orElse(null);

    }
}
