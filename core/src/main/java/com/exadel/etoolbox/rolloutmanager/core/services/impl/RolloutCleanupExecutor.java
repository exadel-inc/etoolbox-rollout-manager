package com.exadel.etoolbox.rolloutmanager.core.services.impl;

import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.ScheduledJobInfo;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component(
        service = JobConsumer.class,
        immediate = true,
        property = JobExecutor.PROPERTY_TOPICS + "=" + RolloutCleanupExecutor.TOPIC)
public class RolloutCleanupExecutor implements JobConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(RolloutCleanupExecutor.class);

    static final String TOPIC = "com/exadel/etoolbox/rolloutmanager/rollout/cleanup";

    private static final int NO_LIMIT = 0;
    private static final Map<String,Object>[] NO_FILTER = null;

    private static final int JOB_GRACE_PERIOD = 10; // minutes
    private static final String CRON_EXPRESSION = "0 */" + JOB_GRACE_PERIOD + " * * * ?";

    @Reference
    private transient JobManager jobManager;

    private ScheduledJobInfo jobInfo;

    @Activate
    private void activate() {
        LOG.info("Starting RolloutExecutor job cleanup task. Cron expression is {}", CRON_EXPRESSION);
        jobManager.getScheduledJobs()
                .stream()
                .filter(job -> TOPIC.equals(job.getJobTopic()))
                .forEach(ScheduledJobInfo::unschedule);
        jobInfo = jobManager.createJob(TOPIC).schedule().cron(CRON_EXPRESSION).add();
    }

    @Deactivate
    private void deactivate() {
        if (jobInfo != null) {
            LOG.info("Terminating RolloutExecutor job cleanup task. Cron expression is {}", CRON_EXPRESSION);
            jobInfo.unschedule();
            jobInfo = null;
        }
    }

    @Override
    public JobResult process(Job job) {
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(JOB_GRACE_PERIOD);
        Collection<Job> jobs = jobManager.findJobs(JobManager.QueryType.HISTORY, RolloutExecutor.TOPIC, NO_LIMIT, NO_FILTER);
        jobs.stream()
                .filter(j -> {
                    Calendar finished = j.getFinishedDate();
                    return finished != null && finished.getTimeInMillis() < cutoff;
                })
                .forEach(j -> {
                    LOG.debug("Found a stale RolloutExecutor job ID={}, removing it", j.getId());
                    jobManager.removeJobById(j.getId());
                });
        return JobResult.OK;
    }
}
