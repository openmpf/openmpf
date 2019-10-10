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

package org.mitre.mpf.wfm.util;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.mitre.mpf.wfm.data.access.UserDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateUserDaoImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;

@Component(UserUtils.REF)
public class UserUtils {

    private static final Logger log = LoggerFactory.getLogger(UserUtils.class);
    public static final String REF = "userUtils";

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    @Qualifier(HibernateUserDaoImpl.REF)
    private UserDao userDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static FileSystemResource userFile;

    private static PropertiesConfiguration propertiesConfig;

    @PostConstruct
    private void init() {
        // get the user properties file from the PropertiesUtil;
        // the PropertiesUtil will ensure that it is copied from the template, if necessary
        userFile = propertiesUtil.getUserFile();

        URL url;
        try {
            url = userFile.getURL();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot get URL from " + userFile + ".", e);
        }

        FileBasedConfigurationBuilder<PropertiesConfiguration> fileBasedConfigBuilder =
                new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class);

        Parameters configBuilderParameters = new Parameters();
        fileBasedConfigBuilder.configure(configBuilderParameters.fileBased().setURL(url)
                .setListDelimiterHandler(new DefaultListDelimiterHandler(',')));

        try {
            propertiesConfig = fileBasedConfigBuilder.getConfiguration();
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Cannot create configuration from " + userFile + ".", e);
        }

        populateUserDatabase();
    }

    private void populateUserDatabase() {
        //propertiesConfig.get

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

    /*
    public static MediaType parse(String mimeType) {
        String trimmedMimeType = TextUtils.trim(mimeType);

        if (propertiesConfig == null) {
            log.warn("Media type properties could not be loaded from " + mediaTypesFile + ".");
        } else {
            String typeFromWhitelist = propertiesConfig.getString("whitelist." + mimeType);
            if (typeFromWhitelist != null) {
                log.debug("Media type found in whitelist: " + mimeType + " is " + typeFromWhitelist);
                MediaType type = MediaType.valueOf(typeFromWhitelist);
                if (type != null) {
                    return type;
                }
            }
        }

        if(StringUtils.startsWithIgnoreCase(trimmedMimeType, "AUDIO")) {
            return MediaType.AUDIO;
        } else if(StringUtils.startsWithIgnoreCase(trimmedMimeType, "IMAGE")) {
            return MediaType.IMAGE;
        } else if(StringUtils.startsWithIgnoreCase(trimmedMimeType, "VIDEO")) {
            return MediaType.VIDEO;
        } else {
            return MediaType.UNKNOWN;
        }
    }
    */
}
