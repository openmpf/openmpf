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

package org.mitre.mpf.wfm.service;

import org.mitre.mpf.wfm.data.access.UserDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateUserDaoImpl;
import org.mitre.mpf.wfm.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service("mpfUserDetailsService")
public class MpfUserDetailsService implements UserDetailsService, ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(MpfUserDetailsService.class);
    private static final boolean enabled = true;
    private static final String mpfUser = "mpf";
    // TODO encrypt these passwords
    private static final String mpfPwd = "mpf123";
    private static final String adminUser = "admin";
    private static final String adminPwd = "mpfadm";
    private static final UserRole userRole[] = { UserRole.ROLE_USER };
    private static final Set userRoleSet = new HashSet(Arrays.asList(userRole));
    private static final UserRole userAdminRole[] = { UserRole.ROLE_USER, UserRole.ROLE_ADMIN };
    private static final Set userAdminRoleSet = new HashSet(Arrays.asList(userAdminRole));

    @Autowired
    @Qualifier(HibernateUserDaoImpl.REF)
    private UserDao userDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
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
    }

    @Transactional
    @Override
    public UserDetails loadUserByUsername(final String username)
            throws UsernameNotFoundException {
        log.debug("Loading user={}", username);
        org.mitre.mpf.wfm.data.entities.persistent.User user = userDao.findByUserName(username);
        List<GrantedAuthority> authorities = null;
        if (user != null) {
            authorities = buildUserAuthority(user.getUserRoles());
        } else {
            log.debug("No user found for {}", username);
            throw new UsernameNotFoundException(String.format("No user found for %s", username));
        }
        return buildUserForAuthentication(user, authorities);
    }

    // Converts User user to
    // org.springframework.security.core.userdetails.User
    private User buildUserForAuthentication(org.mitre.mpf.wfm.data.entities.persistent.User user,
                                            List<GrantedAuthority> authorities) {
        log.debug("Converting o.m.m.w.entity.entity.user.User to org.springframework.security.core.userdetails.User for user={}", user);
        User usr = new User(user.getUsername(), user.getPassword(), enabled, true, true, true, authorities);
        return new MpfUserDetails(usr);
    }

    private List<GrantedAuthority> buildUserAuthority(Set<UserRole> userRoles) {
        Set<GrantedAuthority> setAuths = new HashSet<GrantedAuthority>();

        log.debug("Building user authorities for {}", userRoles);
        // Build user's authorities
        for (UserRole userRole : userRoles) {
            // note: added toString() to match constructor
            setAuths.add(new SimpleGrantedAuthority(userRole.toString()));
        }
        List<GrantedAuthority> result = new ArrayList<GrantedAuthority>(setAuths);

        return result;
    }
}
