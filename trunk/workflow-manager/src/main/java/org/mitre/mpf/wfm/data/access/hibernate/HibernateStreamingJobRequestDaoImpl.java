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

package org.mitre.mpf.wfm.data.access.hibernate;

import org.hibernate.Query;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository(HibernateStreamingJobRequestDaoImpl.REF)
@Transactional(propagation = Propagation.REQUIRED)
public class HibernateStreamingJobRequestDaoImpl extends AbstractHibernateDao<StreamingJobRequest> implements HibernateStreamingJobRequestDao {
	private static final Logger log = LoggerFactory.getLogger(HibernateStreamingJobRequestDaoImpl.class);

	public static final String REF = "hibernateStreamingJobRequestDaoImpl";
	public HibernateStreamingJobRequestDaoImpl() { this.clazz = StreamingJobRequest.class; }

	@Override
	public void cancelJobsInNonTerminalState() {
		Query query = getCurrentSession().
				createQuery("UPDATE StreamingJobRequest set status = :status, status_detail = :statusDetail where status in (:nonTerminalStatuses)");
		query.setParameter("status", StreamingJobStatusType.CANCELLED_BY_SHUTDOWN);
		query.setParameter("statusDetail", "Job cancelled due to Workflow Manager shutdown.");
		query.setParameterList("nonTerminalStatuses", StreamingJobStatus.getNonTerminalStatuses());
		int updatedRows = query.executeUpdate();
		if ( updatedRows >= 0 ) {
			log.warn("{} streaming jobs were in a non-terminal state and have been marked as {}", updatedRows, StreamingJobStatusType.CANCELLED_BY_SHUTDOWN);
		}
	}
}
