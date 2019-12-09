/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.access.UserDao;
import org.mitre.mpf.wfm.data.entities.persistent.User;
import org.mitre.mpf.wfm.enums.UserRole;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;

import static org.mockito.Mockito.*;

public class TestUserService {

    private static final String ENCODED_ADMIN_PASSWORD = "$2a$12$kxtY8QmxrI.ZB6qtfXf01.8oiT3UXOiKq8e4Kz/gLwpZBEEUHub/O";
    private static final String ENCODED_USER_PASSWORD  = "$2a$12$fXmzuWECXwvAVmXyKDdT/.XGLAd0aA9xoGzdE7VUa4nK9FI6eo6aW";

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final UserDao _mockUserDao = mock(UserDao.class);

    @Rule
    public final TemporaryFolder _temporaryFolder = new TemporaryFolder();

    private File userFile;

    @Before
    public void init() throws IOException {
        userFile = _temporaryFolder.newFile();
        when(_mockPropertiesUtil.getUserFile())
                .thenReturn(new FileSystemResource(userFile));
    }


    // Not initialized in init() so tests have a chance to populate userFile
    private UserService createUserService() throws UserCreationException {
        return new UserService(_mockPropertiesUtil, _mockUserDao);
    }

    private UserService createUserServiceWithContent(String content) throws IOException, UserCreationException {
        FileUtils.writeStringToFile(userFile, content, "UTF-8");
        return createUserService();
    }

    private String toUserEntry(User user) {
        return user.getUserName() + "=" +
               user.getUserRoles().iterator().next() + "," +
               user.getPassword() + "\n";
    }


    @Test
    public void handleValidFile() throws IOException, UserCreationException {
        User nonAdminUser = new User("test.user", UserRole.USER, ENCODED_USER_PASSWORD);
        User adminUser = new User("test.admin", UserRole.ADMIN, ENCODED_ADMIN_PASSWORD);
        createUserServiceWithContent(
                toUserEntry(nonAdminUser) +
                toUserEntry(adminUser)
        );
        verify(_mockUserDao, times(2)).persist(any());
        verify(_mockUserDao).persist(eq(nonAdminUser));
        verify(_mockUserDao).persist(eq(adminUser));
    }

    @Test
    public void handleMissingFile() {
        userFile.delete();
        Exception ex = TestUtil.assertThrows(IllegalStateException.class,
                () -> createUserService());
        Assert.assertTrue(ex.getCause().getMessage().contains("Unable to load the configuration"));
        Assert.assertTrue(ex.getCause().getCause().getMessage().contains("No such file"));
        verify(_mockUserDao, never()).persist(any());
    }

    @Test
    public void handleMissingUserName() {
        User adminUser = new User("test.admin", UserRole.ADMIN, ENCODED_ADMIN_PASSWORD);
        User otherUser = new User("test.other", UserRole.USER, ENCODED_USER_PASSWORD);

        Exception ex = TestUtil.assertThrows(UserCreationException.class,
                () -> createUserServiceWithContent(
                        toUserEntry(adminUser) +
                        "=user," + ENCODED_USER_PASSWORD + "\n" +
                        toUserEntry(otherUser)));

        Assert.assertTrue(ex.getMessage().contains("Invalid user name"));
        verify(_mockUserDao, times(1)).persist(any());
        verify(_mockUserDao).persist(eq(adminUser)); // ensure users before the bad entry are handled
    }

    @Test
    public void handleMissingRole() {
        Exception ex = TestUtil.assertThrows(UserCreationException.class,
                () -> createUserServiceWithContent("test.user=," + ENCODED_USER_PASSWORD));
        Assert.assertTrue(ex.getMessage().contains("Invalid role"));
        verify(_mockUserDao, never()).persist(any());
    }

    @Test
    public void handleMissingPassword() {
        Exception ex = TestUtil.assertThrows(UserCreationException.class,
                () -> createUserServiceWithContent("test.user=user,"));
        Assert.assertTrue(ex.getMessage().contains("Invalid encoded password"));
        verify(_mockUserDao, never()).persist(any());
    }

    @Test
    public void handleInvalidRole() {
        Exception ex = TestUtil.assertThrows(UserCreationException.class,
                () -> createUserServiceWithContent("test.user=foo," + ENCODED_USER_PASSWORD));
        Assert.assertTrue(ex.getMessage().contains("Invalid role"));
        verify(_mockUserDao, never()).persist(any());
    }

    @Test
    public void handleInvalidPasswordFormat() {
        Exception ex = TestUtil.assertThrows(UserCreationException.class,
                () -> createUserServiceWithContent("test.user=user,garbage-pass"));
        Assert.assertTrue(ex.getMessage().contains("Invalid encoded password"));
        verify(_mockUserDao, never()).persist(any());
    }

    @Test
    public void handleInvalidPasswordLength() {
        Exception ex = TestUtil.assertThrows(UserCreationException.class,
                () -> createUserServiceWithContent("test.user=user," +
                        ENCODED_USER_PASSWORD.substring(0, ENCODED_USER_PASSWORD.length()-1)));
        Assert.assertTrue(ex.getMessage().contains("Invalid modified base-64 salt and cipher text"));
        Assert.assertTrue(ex.getMessage().contains("characters long"));
        verify(_mockUserDao, never()).persist(any());
    }
}
