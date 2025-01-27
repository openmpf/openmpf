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

package org.mitre.mpf.wfm.data.access.hibernate;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.hibernate.engine.jdbc.dialect.internal.StandardDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.mitre.mpf.rest.api.AggregatePipelineStatsModel;
import org.mitre.mpf.rest.api.AllJobsStatisticsModel;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.util.CallbackStatus;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Singleton
@Transactional(propagation = Propagation.REQUIRED)
public class HibernateJobRequestDaoImpl extends AbstractHibernateDao<JobRequest> implements JobRequestDao {
    private static final Logger LOG = LoggerFactory.getLogger(HibernateJobRequestDaoImpl.class);

    private final JobStatusBroadcaster _jobStatusBroadcaster;

    private final AtomicLong _jobCount = new AtomicLong(0);

    private final CompletableFuture<Void> _startupCountFuture;

    @Inject
    public HibernateJobRequestDaoImpl(
            JobStatusBroadcaster jobStatusBroadcaster, SessionFactory sessionFactory) {
        super(JobRequest.class, sessionFactory);
        _jobStatusBroadcaster = jobStatusBroadcaster;

        _startupCountFuture = ThreadUtil.runAsync(() -> {
            // Manually create new session to avoid
            // "Could not obtain transaction-synchronized Session for current thread".
            try (var session = getSessionFactory().openSession()) {
                _jobCount.addAndGet(countAll(session));
            }
        });
    }

    @Override
    public void newJobCreated() {
        _jobCount.incrementAndGet();
    }

    @Override
    public long estimateNumberOfJobs() {
        // Getting the total number of jobs from postgres is a lot slower than the query to get
        // a single page of results so we try to keep track of the number of jobs in the database.
        _startupCountFuture.join();
        return _jobCount.get();
    }


    @Override
    public void cancelJobsInNonTerminalState() {
        var update = getCriteriaBuilder().createCriteriaUpdate(JobRequest.class);
        var root = update.from(JobRequest.class);

        var nonTerminalStatuses = BatchJobStatusType.getNonTerminalStatuses();
        update.set("status", BatchJobStatusType.CANCELLED_BY_SHUTDOWN)
                .set("timeCompleted", Instant.now())
                .where(root.get("status").in(nonTerminalStatuses));

        int numRowsUpdated = executeUpdate(update);
        if (numRowsUpdated > 0) {
            LOG.warn("{} jobs were in a non-terminal state and have been marked as {}",
                     numRowsUpdated, BatchJobStatusType.CANCELLED_BY_SHUTDOWN);
        }
    }


    @Override
    public List<JobRequest> findByPage(int pageSize,
                                       int offset,
                                       String searchTerm,
                                       String sortColumn,
                                       String sortOrderDirection) {
        var cb = getCriteriaBuilder();
        var query = cb.createQuery(JobRequest.class);
        var root = query.from(JobRequest.class);

        if (!StringUtils.isBlank(searchTerm)) {
            query.where(createSearchFilter(searchTerm, cb, root));
        }

        if (sortOrderDirection.equals("desc")) {
            query.orderBy(cb.desc(root.get(sortColumn)));
        }
        else {
            query.orderBy(cb.asc(root.get(sortColumn)));
        }

        return buildQuery(query)
                .setFirstResult(offset)
                .setMaxResults(pageSize)
                .list();
    }


    private static Predicate createSearchFilter(String searchTerm, CriteriaBuilder cb,
                                                Root<JobRequest> root) {

        var dateFormat = "YYYY-MM-DD HH24:MI:SS";
        var searchWithWildCard = '%' + searchTerm.toLowerCase() + '%';

        Function<Expression<String>, Predicate> ilike
                = expr -> cb.like(cb.lower(expr), searchWithWildCard);

        return cb.or(
                ilike.apply(root.get("id").as(String.class)),
                ilike.apply(root.get("pipeline")),
                ilike.apply(root.get("status").as(String.class)),
                ilike.apply(root.get("tiesDbStatus")),
                ilike.apply(root.get("callbackStatus")),
                ilike.apply(cb.function("to_char", String.class, root.get("timeReceived"),
                                        cb.literal(dateFormat))),
                ilike.apply(cb.function("to_char", String.class, root.get("timeCompleted"),
                                        cb.literal(dateFormat)))
        );
    }

    @Override
    public long getNextId() {
        return getCurrentSession().doReturningWork(connection -> {
            var dialectResolver = new StandardDialectResolver();
            var dialect = dialectResolver.resolveDialect(
                    new DatabaseMetaDataDialectResolutionInfoAdapter(connection.getMetaData()));

            var queryString = dialect.getSequenceNextValString(JOB_ID_SEQUENCE_NAME);
            try (PreparedStatement preparedStatement = connection.prepareStatement(queryString);
                 ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        });
    }


    @Override
    public void updateStatus(long jobId, BatchJobStatusType status) {
        var cb = getCriteriaBuilder();
        var update = cb.createCriteriaUpdate(JobRequest.class);
        var root = update.from(JobRequest.class);

        update.set("status", status)
                .where(cb.equal(root.get("id"), jobId));

        executeUpdate(update);
    }


    @Override
    public BatchJobStatusType getStatus(long jobId) {
        var cb = getCriteriaBuilder();
        var query = cb.createQuery(BatchJobStatusType.class);
        var root = query.from(JobRequest.class);

        query.select(root.get("status"))
                .where(cb.equal(root.get("id"), jobId));

        return buildQuery(query).getSingleResult();
    }


    @Override
    public void setTiesDbSuccessful(long jobId) {
        var newStatus = CallbackStatus.complete();
        setCallbackStatus(jobId, newStatus, "tiesDbStatus");
        _jobStatusBroadcaster.tiesDbStatusChanged(jobId, newStatus);
    }

    @Override
    public void setTiesDbError(long jobId, String status) {
        var newStatus = CallbackStatus.error(status);
        setCallbackStatus(jobId, newStatus, "tiesDbStatus");
        _jobStatusBroadcaster.tiesDbStatusChanged(jobId, newStatus);
    }


    @Override
    public void setCallbackSuccessful(long jobId) {
        var newStatus = CallbackStatus.complete();
        setCallbackStatus(jobId, newStatus, "callbackStatus");
        _jobStatusBroadcaster.callbackStatusChanged(jobId, newStatus);
    }

    @Override
    public void setCallbackError(long jobId, String status) {
        var newStatus = CallbackStatus.error(status);
        setCallbackStatus(jobId, newStatus, "callbackStatus");
        _jobStatusBroadcaster.callbackStatusChanged(jobId, newStatus);
    }

    private void setCallbackStatus(long jobId, String status, String column) {
        var cb = getCriteriaBuilder();
        var update = cb.createCriteriaUpdate(JobRequest.class);
        var root = update.from(JobRequest.class);

        update.set(column, status)
                .where(cb.equal(root.get("id"), jobId));

        executeUpdate(update);
    }


    @Override
    public AllJobsStatisticsModel getJobStats() {
        long start = System.currentTimeMillis();

        // Need to use createNativeQuery here because other methods were converting times to
        // integers, but we want decimals for sub-second precision.
        var query = getCurrentSession().createNativeQuery(
            "SELECT pipeline, status, count(*), min(duration), max(duration), sum(duration), " +
                    "sum(CASE WHEN duration IS NULL THEN 0 ELSE 1 END) as valid_count" +
            " FROM ( " +
                "SELECT pipeline, status, EXTRACT(EPOCH FROM (time_completed - time_received)) as duration " +
                "FROM job_request " +
            ") as sub " +
            " GROUP BY pipeline, status");

        Map<String, AggregatePipelineStatsModel> statsModels;
        try (var queryResults = (Stream<Object[]>) query.stream()) {
            statsModels = queryResults.collect(groupingBy(
                    row -> row[0].toString(),
                    collectingAndThen(toList(), HibernateJobRequestDaoImpl::getPipelineStats)));
        }

        long totalJobs = statsModels.values()
                .stream()
                .mapToLong(AggregatePipelineStatsModel::getCount)
                .sum();

        AllJobsStatisticsModel allJobsStatisticsModel = new AllJobsStatisticsModel();
        allJobsStatisticsModel.setTotalJobs((int) totalJobs);
        allJobsStatisticsModel.setJobTypes(statsModels.size());
        allJobsStatisticsModel.setAggregatePipelineStatsMap(statsModels);
        allJobsStatisticsModel.setElapsedTimeMs(System.currentTimeMillis() - start);

        return allJobsStatisticsModel;
    }

    // Decided to manually combine pipeline status entries so that we don't need to do a
    // "GROUP BY pipeline" query in addition to the "GROUP BY pipeline, status" we currently do.
    private static AggregatePipelineStatsModel getPipelineStats(Iterable<Object[]> rows) {
        long minDuration = 0;
        long maxDuration = 0;
        long totalDuration = 0;
        long count = 0;
        long validCount = 0;
        var stateCounts = new HashMap<String, Long>();
        for (Object[] row : rows) {
            var status = row[1].toString();
            long stateCount = ((Number) row[2]).longValue();
            stateCounts.put(status, stateCount);
            count += stateCount;

            if (status.equals("CANCELLED_BY_SHUTDOWN")
                    || status.equals("CANCELLED")
                    || status.equals("CANCELLING")) {
                continue;
            }

            validCount += ((Number) row[6]).longValue();
            long currentRowMin = toMillis(row[3]);
            if (currentRowMin > 0) {
                if (minDuration == 0) {
                    minDuration = currentRowMin;
                }
                else {
                    minDuration = Math.min(minDuration, currentRowMin);
                }
            }

            maxDuration = Math.max(maxDuration, toMillis(row[4]));
            totalDuration += toMillis(row[5]);
        }
        return new AggregatePipelineStatsModel(
                totalDuration, minDuration, maxDuration, count,
                validCount, stateCounts);
    }


    private static long toMillis(Object secondsObj) {
        if (secondsObj == null) {
            return 0;
        }
        // Depending on the version of PostgreSQL, secondsObj will either be a Double or BigDecimal.
        double seconds = ((Number) secondsObj).doubleValue();
        return (long) (seconds * 1000);
    }
}
