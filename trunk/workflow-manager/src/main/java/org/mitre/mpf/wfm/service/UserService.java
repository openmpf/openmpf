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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.*;

@Service
public class UserService implements UserDetailsService {

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

        populateDatabase();
    }

    private void populateDatabase() {
        for (Iterator<String> it = _propertiesConfig.getKeys(); it.hasNext(); ) {
            String userName = it.next();
            String value = _propertiesConfig.getString(userName);

            User user;
            try {
                user = parseEntry(userName, value);
            } catch (UserCreationException e) {
                log.warn("Invalid user entry in " + _userFile.getPath() + ":\n\t" + userName + "=" + value + "\n" + e.getMessage());
                continue;
            }

            log.info("Creating user \"" + user.getUserName() + "\" with roles \"" + user.getUserRoles() + "\".");
            _userDao.persist(user);
        }
    }

    // protected to enable unit test access
    protected static User parseEntry(String userName, String value) throws UserCreationException {
        if (userName.isEmpty()) {
            throw new UserCreationException("Invalid user name \"" + userName + "\".");
        }

        String[] entryTokens = value.split(",", 2);

        if (entryTokens.length < 2) {
            throw new UserCreationException("Entries should follow the format: <name>=<role>,<encoded-password>");
        }

        UserRole role;
        try {
            role = UserRole.valueOf(entryTokens[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UserCreationException("Invalid role \"" + entryTokens[0] + "\"." +
                    " Valid roles are: " + Arrays.toString(UserRole.values()));
        }

        String encodedPassword = entryTokens[1];
        String[] encodedPasswordTokens = encodedPassword.split("\\$");

        if (encodedPasswordTokens.length != 4) {
            throw new UserCreationException("Invalid encoded password \"" + encodedPassword + "\"." +
                    " Encoded passwords should follow the format:" +
                    " $<bcrypt-algorithm-version>$<encoding-strength>$<modified-base-64-salt-and-cipher-text>");
        }

        String saltAndCipherText = encodedPasswordTokens[3];

        if (saltAndCipherText.length() != SALT_AND_CIPHER_TEXT_LENGTH) {
            throw new UserCreationException("Invalid modified base-64 salt and cipher text \"" + saltAndCipherText + "\"." +
                    " Text should be " + SALT_AND_CIPHER_TEXT_LENGTH + " characters long.");
        }

        return new User(userName, role, encodedPassword);
    }

    @Transactional
    @Override
    public UserDetails loadUserByUsername(final String userName) throws UsernameNotFoundException {
        log.debug("Loading user \"{}\".", userName);
        User user = _userDao.findByUserName(userName);
        List<GrantedAuthority> authorities;
        if (user != null) {
            authorities = buildUserAuthority(user.getUserRoles());
        } else {
            log.debug("No user \"{}\" found.", userName);
            throw new UsernameNotFoundException(String.format("No user %s found.", userName));
        }
        return buildUserForAuthentication(user, authorities);
    }

    private org.springframework.security.core.userdetails.User buildUserForAuthentication(org.mitre.mpf.wfm.data.entities.persistent.User user,
                                                                                          List<GrantedAuthority> authorities) {
        org.springframework.security.core.userdetails.User springUser =
                new org.springframework.security.core.userdetails.User(user.getUserName(),
                        user.getPassword(), true, true, true, true, authorities);
        return new org.mitre.mpf.wfm.service.UserDetails(springUser);
    }

    private List<GrantedAuthority> buildUserAuthority(Set<UserRole> userRoles) {
        Set<GrantedAuthority> setAuths = new HashSet<>();
        log.debug("Building user authorities for {}.", userRoles);
        for (UserRole userRole : userRoles) {
            setAuths.add(new SimpleGrantedAuthority(userRole.toString()));
        }
        return new ArrayList<>(setAuths);
    }
}
