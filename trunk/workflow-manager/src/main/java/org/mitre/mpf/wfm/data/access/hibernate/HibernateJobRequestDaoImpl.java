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

package org.mitre.mpf.wfm.data.access.hibernate;

import org.apache.commons.lang3.Validate;
import org.hibernate.Query;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository(HibernateJobRequestDaoImpl.REF)
@Transactional(propagation = Propagation.REQUIRED)
public class HibernateJobRequestDaoImpl extends AbstractHibernateDao<JobRequest> implements HibernateJobRequestDao {
	private static final Logger log = LoggerFactory.getLogger(HibernateJobRequestDaoImpl.class);

	public static final String REF = "hibernateJobRequestDaoImpl";
	public HibernateJobRequestDaoImpl() { this.clazz = JobRequest.class; }

	@Override
	public void cancelJobsInNonTerminalState() {
		Query query = getCurrentSession().
				createQuery("UPDATE JobRequest set status = :newStatus where status in (:nonTerminalStatuses)");
		query.setParameter("newStatus", BatchJobStatusType.CANCELLED_BY_SHUTDOWN);
		query.setParameterList("nonTerminalStatuses", BatchJobStatusType.getNonTerminalStatuses());
		int updatedRows = query.executeUpdate();
		if(updatedRows >= 0) {
			log.warn("{} jobs were in a non-terminal state and have been marked as {}", updatedRows, BatchJobStatusType.CANCELLED_BY_SHUTDOWN);
		}
	}

	public List<JobRequest> findByPage(final int pageSize, final int offset, String searchTerm, String sortColumn,
							  String sortOrderDirection) {
		Validate.notNull(clazz);

		if ( searchTerm.equals("") ) {
			Split split = SimonManager.getStopwatch(profilerName + ".findByPage(int,int,String,String)").start();
			try {
				return getCurrentSession().createQuery("from " + clazz.getName() +
						" order by " + sortColumn + " " + sortOrderDirection)
						.setFirstResult(offset)
						.setMaxResults(pageSize)
						.list();
			} finally {
				split.stop();
			}
		} else {
			Split split = SimonManager.getStopwatch(profilerName +
					".findByPage(int,int,String,String,String,String,String)").start();
			try {
				return getCurrentSession().createQuery("from " + clazz.getName() +
						" where pipeline like concat('%', :searchTerm, '%')" +
						" or status like concat('%', :searchTerm, '%')" +
						" or time_received like concat('%', :searchTerm, '%')" +
						" or time_completed like concat('%', :searchTerm, '%')" +
						" order by " + sortColumn + " " + sortOrderDirection)
						.setParameter("searchTerm", searchTerm)
						.setFirstResult(offset)
						.setMaxResults(pageSize)
						.list();
			} finally {
				split.stop();
			}
		}
	}



	public long countFiltered(String searchTerm) {
		Validate.notNull(clazz);
		Split split = SimonManager.getStopwatch(profilerName+".countFiltered(String)").start();
		try {
			return (long) getCurrentSession()
					.createQuery("select count(*) from " + clazz.getName() +
							" where pipeline like concat('%', :searchTerm, '%')" +
							" or status like concat('%', :searchTerm, '%')" +
							" or time_received like concat('%', :searchTerm, '%')" +
							" or time_completed like concat('%', :searchTerm, '%')")
					.setParameter("searchTerm", searchTerm).list().get(0);
		} finally {
			split.stop();
		}
	}
}
