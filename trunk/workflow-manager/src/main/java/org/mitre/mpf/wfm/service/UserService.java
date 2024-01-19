/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Profile("!oidc")
@Service("mpfUserService")
public class UserService implements UserDetailsService {

    private static final int SALT_AND_CIPHER_TEXT_LENGTH = 53;

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserDao _userDao;

    @Inject
    public UserService(PropertiesUtil propertiesUtil, UserDao userDao) throws UserCreationException {
        _userDao = userDao;

        // Get the user properties file from the PropertiesUtil.
        // The PropertiesUtil will ensure that it is copied from the template, if necessary.
        FileSystemResource userFile = propertiesUtil.getUserFile();

        URL url;
        try {
            url = userFile.getURL();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot get URL from " + userFile + ".", e);
        }

        FileBasedConfigurationBuilder<PropertiesConfiguration> fileBasedConfigBuilder =
                new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class);

        Parameters configBuilderParameters = new Parameters();
        fileBasedConfigBuilder.configure(configBuilderParameters.fileBased().setURL(url));

        PropertiesConfiguration propertiesConfig;
        try {
            propertiesConfig = fileBasedConfigBuilder.getConfiguration();
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Cannot create configuration from " + userFile + ".", e);
        }

        String userName = null;
        String value = null;
        String creationError = null;

        for (Iterator<String> it = propertiesConfig.getKeys(); it.hasNext(); ) {
            userName = it.next();
            value = propertiesConfig.getString(userName);

            if (_userDao.findByUserName(userName).isPresent()) {
                continue; // this user already exists
            }

            if (userName.isEmpty()) {
                creationError = "Invalid user name \"" + userName + "\".";
                break;
            }

            String[] entryTokens = value.split(",", 2);

            if (entryTokens.length < 2) {
                creationError = "Entries should follow the format: <name>=<role>,<encoded-password>";
                break;
            }

            UserRole role;
            try {
                role = UserRole.valueOf(entryTokens[0].toUpperCase());
            }
            catch (IllegalArgumentException e) {
                creationError = "Invalid role \"" + entryTokens[0] + "\". Valid roles are: " +
                        Arrays.stream(UserRole.values())
                                .map(Enum::toString)
                                .collect(Collectors.joining(", "));
                break;
            }

            String encodedPassword = entryTokens[1];
            String[] encodedPasswordTokens = encodedPassword.split("\\$");

            if (encodedPasswordTokens.length != 4) {
                creationError = "Invalid encoded password \"" + encodedPassword + "\"." +
                        " Encoded passwords should follow the format:" +
                        " $<bcrypt-algorithm-version>$<encoding-strength>$<modified-base-64-salt-and-cipher-text>";
                break;
            }

            String saltAndCipherText = encodedPasswordTokens[3];

            if (saltAndCipherText.length() != SALT_AND_CIPHER_TEXT_LENGTH) {
                creationError = "Invalid modified base-64 salt and cipher text \"" + saltAndCipherText + "\"." +
                        " Text should be " + SALT_AND_CIPHER_TEXT_LENGTH + " characters long.";
                break;
            }

            User user = new User(userName, role, encodedPassword);

            log.info("Creating user \"" + user.getUserName() + "\" with roles: " +
                    user.getUserRoles().stream()
                            .map(UserRole::toString)
                            .collect(Collectors.joining(", ")));

            _userDao.persist(user);
        }

        if (creationError != null) {
            throw new UserCreationException("Invalid user entry in " + userFile.getPath() + ":\n" +
                    "\t" + userName + "=" + value + "\n" + creationError);
        }
    }

    @Transactional
    @Override
    public org.springframework.security.core.userdetails.User loadUserByUsername(
            final String userName) {
        log.debug("Loading user \"{}\".", userName);
        return _userDao.findByUserName(userName)
                .map(UserService::toSpringUser)
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("No user %s found.", userName)));
    }

    private static org.springframework.security.core.userdetails.User toSpringUser(User user) {
        return new org.springframework.security.core.userdetails.User(
                user.getUserName(),
                user.getPassword(),
                true,
                true,
                true,
                true,
                buildUserAuthorities(user.getUserRoles()));
    }

    private static List<GrantedAuthority> buildUserAuthorities(Set<UserRole> userRoles) {
        Set<GrantedAuthority> setAuths = new HashSet<>();
        log.debug("Building user authorities for {}.", userRoles);
        for (UserRole userRole : userRoles) {
            setAuths.add(new SimpleGrantedAuthority(userRole.springName));
        }
        return new ArrayList<>(setAuths);
    }
}
