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

import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository(HibernateMarkupResultDaoImpl.REF)
@Transactional(propagation = Propagation.REQUIRED)
public class HibernateMarkupResultDaoImpl extends AbstractHibernateDao<MarkupResult> implements MarkupResultDao {
	private static final Logger log = LoggerFactory.getLogger(HibernateMarkupResultDaoImpl.class);
	public static final String REF = "hibernateMarkupResultDaoImpl";
	public HibernateMarkupResultDaoImpl() {
		this.clazz = MarkupResult.class;
	}

	@Override
	public MarkupResult findByJobIdAndMediaIndex(long jobId, int mediaIndex) {
		List results = getCurrentSession().createQuery("from " + MarkupResult.class.getSimpleName() + " where jobId = :jobId and mediaIndex = :mediaIndex")
			.setParameter("jobId", jobId)
			.setParameter("mediaIndex", mediaIndex)
			.setMaxResults(1)
			.list();

		return (results.size() != 0) ? (MarkupResult)(results.get(0)) : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<MarkupResult> findByJobId(long jobId) {
		return getCurrentSession().createQuery("from " + MarkupResult.class.getSimpleName() + " where jobId = :jobId")
				.setParameter("jobId", jobId)
				//.setMaxResults(1)
				.list();
	}

	@Override
	public void deleteByJobId(long jobId) {
		getCurrentSession().createQuery("delete from "+MarkupResult.class.getSimpleName()+" where jobId = :jobId")
				.setParameter("jobId", jobId)
				.executeUpdate();
	}
}
