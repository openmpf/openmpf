/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm;

import javax.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.mitre.mpf.wfm.data.access.JpaPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.support.OpenSessionInViewFilter;
import org.springframework.transaction.support.TransactionTemplate;

import com.querydsl.jpa.hibernate.HibernateQueryFactory;

@Configuration
@EnableJpaRepositories(basePackageClasses = JpaPackage.class, considerNestedRepositories = true)
public class JpaConfiguration {

    @Bean
    public EntityManagerFactory entityManagerFactory(SessionFactory sessionFactory) {
        // The Spring Data JPA Repositories need an EntityManagerFactory and Hibernate's
        // SessionFactory implements the EntityManagerFactory interface.
        return sessionFactory;
    }

    @Bean
    public HibernateQueryFactory hibernateQueryFactory(SessionFactory sessionFactory) {
        // Configure the Querydsl library to use Hibernate.
        return new HibernateQueryFactory(sessionFactory::getCurrentSession);
    }

    @Bean
    public OpenSessionInViewFilter openSessionInViewFilter() {
        // Allows loading of lazy entity properties during serialization.
        return new OpenSessionInViewFilter();
    }

    @Bean
    public TransactionTemplate transactionTemplate(HibernateTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}
