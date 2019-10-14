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

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.mitre.mpf.wfm.data.access.UserDao;
import org.mitre.mpf.wfm.data.entities.persistent.User;
import org.mitre.mpf.wfm.enums.UserRole;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;

@Service
public class UserService {

    public static final int SALT_AND_CIPHER_TEXT_LENGTH = 53;

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final PropertiesUtil _propertiesUtil;

    private final UserDao _userDao;

    private FileSystemResource _userFile;

    private PropertiesConfiguration _propertiesConfig;

    @Inject
    public UserService(PropertiesUtil propertiesUtil, UserDao userDao) {
        _propertiesUtil = propertiesUtil;
        _userDao = userDao;

        // Get the user properties file from the PropertiesUtil.
        // The PropertiesUtil will ensure that it is copied from the template, if necessary.
        _userFile = propertiesUtil.getUserFile();

        URL url;
        try {
            url = _userFile.getURL();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot get URL from " + _userFile + ".", e);
        }

        FileBasedConfigurationBuilder<PropertiesConfiguration> fileBasedConfigBuilder =
                new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class);

        Parameters configBuilderParameters = new Parameters();
        fileBasedConfigBuilder.configure(configBuilderParameters.fileBased().setURL(url));

        try {
            _propertiesConfig = fileBasedConfigBuilder.getConfiguration();
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Cannot create configuration from " + _userFile + ".", e);
        }

        populateUserDatabase();
    }

    private void populateUserDatabase() {
        for (Iterator<String> it = _propertiesConfig.getKeys(); it.hasNext(); ) {
            String userName = it.next();

            String value = _propertiesConfig.getString(userName);
            String[] entryTokens = value.split(",", 2);

            if (entryTokens.length < 2) {
                log.warn("Invalid user entry in " + _userFile.getPath() + ":\n\t" + userName + "=" + value +
                         "\nEntries should follow the format: <name>=<role>,<encoded-password>");
                continue;
            }

            UserRole role;
            try {
                role = UserRole.valueOf(entryTokens[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid user entry in " + _userFile.getPath() + ":\n\t" + userName + "=" + value +
                         "\nInvalid role \"" + entryTokens[0] + "\"." +
                         " Valid roles are: " + Arrays.toString(UserRole.values()).toLowerCase());
                continue;
            }

            String encodedPassword = entryTokens[1];
            String[] encodedPasswordTokens = encodedPassword.split("\\$");

            if (encodedPasswordTokens.length != 4) {
                log.warn("Invalid user entry in " + _userFile.getPath() + ":\n\t" + userName + "=" + value +
                         "\nInvalid encoded password \"" + encodedPassword + "\"." +
                         " Encoded passwords should follow the format:" +
                         " $<bcrypt-algorithm-version>$<encoding-strength>$<modified-base-64-salt-and-cipher-text>");
                continue;
            }

            String saltAndCipherText = encodedPasswordTokens[3];

            if (saltAndCipherText.length() != SALT_AND_CIPHER_TEXT_LENGTH) {
                log.warn("Invalid user entry in " + _userFile.getPath() + ":\n\t" + userName + "=" + value +
                         "\nInvalid modified base-64 salt and cipher text \"" + saltAndCipherText + "\"." +
                         " Text should be " + SALT_AND_CIPHER_TEXT_LENGTH + " characters long.");
                continue;
            }

            log.info("Creating user \"" + userName + "\" with role \"" + role + "\".");

            User user = new User(userName, role, encodedPassword);
            _userDao.persist(user);
        }

        /*
        log.debug("Checking whether user table is populated");
        org.mitre.mpf.wfm.data.entities.persistent.User user = userDao.findByUserName(mpfUser);
        if (user == null) {  // this will only be true once
            log.debug("About to initialize db with users {} & {}", mpfUser, adminUser);

            //mpf
            String saltedAndHashedMpfPassword = passwordEncoder.encode(mpfPwd);
            user = new org.mitre.mpf.wfm.data.entities.persistent.User(mpfUser, saltedAndHashedMpfPassword);
            user.setUserRoles(userRoleSet);
            userDao.persist(user);

            //admin
            String saltedAndHashedAdminPwd = passwordEncoder.encode(adminPwd);
            org.mitre.mpf.wfm.data.entities.persistent.User userAdmin =
                    new org.mitre.mpf.wfm.data.entities.persistent.User(adminUser, saltedAndHashedAdminPwd);
            userAdmin.setUserRoles(userAdminRoleSet);
            userDao.persist(userAdmin);

            log.debug("Successfully persisted users {} & {}", mpfUser, adminUser);
        }
        */
    }
}
