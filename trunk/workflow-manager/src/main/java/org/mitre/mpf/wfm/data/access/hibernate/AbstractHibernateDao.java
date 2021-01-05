/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.data.access.hibernate;

import org.apache.commons.lang3.Validate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.mitre.mpf.wfm.data.access.JpaDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.REQUIRED)
public abstract class AbstractHibernateDao<T> implements JpaDao<T> {

	/**
	 * <p>The type of elements managed by this dao.</p>
	 */
	protected Class<T> clazz;
	protected String profilerName = String.format("%s.generic", this.getClass().getName());
	private static final Logger log = LoggerFactory.getLogger(AbstractHibernateDao.class);

	/** <p>The factory which creates connections to the underlying database.</p> */
	@Autowired
	private SessionFactory sessionFactory;

	@Override
	public void setClass(final Class<T> classToSet) {
		clazz = classToSet;
		Validate.notNull(clazz);
		profilerName = String.format("%s-%s", this.getClass().getName(), clazz.getSimpleName());
	}

	@Override
	public T findById(final long id) {
		Validate.notNull(clazz);
		Split split = SimonManager.getStopwatch(profilerName + ".findById(long)").start();
		try {
			return (T) getCurrentSession().get(clazz, id);
		} finally {
			split.stop();
		}
	}

	@Override
	public List<T> findByIds(Collection<Long> ids) {
		Validate.notNull(clazz);
		Split split = SimonManager.getStopwatch(profilerName+".findByIds(List)").start();
		try {
			return getCurrentSession().createQuery("from " + clazz.getName() + " where id in (:ids)")
					.setParameterList("ids", ids).list();
		} finally {
			split.stop();
		}
	}

	@Override
	public void refresh(final T entity) {
		Split split = SimonManager.getStopwatch(profilerName+".refresh(Object)").start();
		try {
			getCurrentSession().refresh(entity);
		} finally {
			split.stop();
		}
	}

	@Override
	public List<T> findAll() {
		Validate.notNull(clazz);
		Split split = SimonManager.getStopwatch(profilerName+".findAll()").start();
		try {
			return getCurrentSession().createQuery("from " + clazz.getName())
					.list();
		} finally {
			split.stop();
		}
	}

	@Override
	public long countAll() {
		Validate.notNull(clazz);
		Split split = SimonManager.getStopwatch(profilerName+".countAll()").start();
		try {
			return (long) getCurrentSession()
					.createQuery("select count(*) from " + clazz.getName()).list().get(0);
		} finally {
			split.stop();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public T persist(final T entity) {
		Validate.notNull(entity);
		Split split = SimonManager.getStopwatch(profilerName+".persist(Object)").start();
		try {
			return (T) getCurrentSession().merge(entity);
		} finally {
			split.stop();
		}
	}

	@Override
	public void delete(final T entity) {
		Validate.notNull(entity);
		Split split = SimonManager.getStopwatch(profilerName+".delete(Object)").start();
		try {
			getCurrentSession().delete(entity);
		} finally {
			split.stop();
		}
	}

	@Override
	public void deleteAll(final Collection<T> entities) {
		Validate.notNull(entities);
		for(T entity : entities) {
			getCurrentSession().delete(entity);
		}
	}

	@Override
	public void deleteById(final long entityId) {
		final T entity = findById(entityId);
		Validate.validState(entity != null);
		Split split = SimonManager.getStopwatch(profilerName+".deleteById(long)").start();
		try {
			delete(entity);
		} finally {
			split.stop();
		}
	}

	@Override
	public void deleteByIds(final Collection<Long> entityIds) {
		final Collection<T> entities = findByIds(entityIds);
		Validate.validState(entities != null);
		deleteAll(entities);
	}

	@Override
	public T createProxy(long id) {
		Split split = SimonManager.getStopwatch(profilerName+".createProxy(long)").start();
		try {
			return (T) getCurrentSession().load(clazz, id);
		} finally {
			split.stop();
		}
	}

	@Override
	public void update(final T entity) {
		Split split = SimonManager.getStopwatch(profilerName+".update(Object)").start();
		try {
			getCurrentSession().update(entity);
		} finally {
			split.stop();
		}
	}

	public final Session getCurrentSession() {
		Split split = SimonManager.getStopwatch(profilerName+".getCurrentSession()").start();
		try {
			return sessionFactory.getCurrentSession();
		} finally {
			split.stop();
		}
	}

	public SessionFactory getSessionFactory() {
		Split split = SimonManager.getStopwatch(profilerName+".getSessionFactory()").start();
		try {
			return sessionFactory;
		} finally {
			split.stop();
		}
	}

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}


}

