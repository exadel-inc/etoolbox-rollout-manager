package com.exadel.etoolbox.rolloutmanager.core.services.impl;

import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.day.cq.wcm.msm.api.LiveRelationshipManager;
import com.day.cq.wcm.msm.api.RolloutManager;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutItem;
import com.exadel.etoolbox.rolloutmanager.core.servlets.RolloutServlet;
import com.exadel.etoolbox.rolloutmanager.core.utils.RolloutLogUtil;
import com.exadel.etoolbox.rolloutmanager.core.utils.RolloutPlanUtil;
import com.exadel.etoolbox.rolloutmanager.core.utils.ThrottledLogger;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.ScheduledJobInfo;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Executes a rollout job asynchronously. The job is created in
 * {@link RolloutServlet}
 */
@Component(
        service = JobExecutor.class,
        immediate = true,
        property = JobExecutor.PROPERTY_TOPICS + "=" + RolloutExecutor.TOPIC)
public class RolloutExecutor implements JobExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RolloutExecutor.class);

    public static final String TOPIC = "com/exadel/etoolbox/rolloutmanager/rollout";

    private static final String CLEANUP_TOPIC = "org/apache/sling/event/impl/jobs/tasks/HistoryCleanUpTask";
    private static final int CLEANUP_THRESHOLD_HOURS = 12;
    private static final int CLEANUP_THRESHOLD_MINS = 60 * CLEANUP_THRESHOLD_HOURS;
    private static final String CLEANUP_CRON_EXPRESSION = "0 0 0/" + CLEANUP_THRESHOLD_HOURS + " * * ?";

    private static final String QUEUE_CONFIG_PID = "org.apache.sling.event.jobs.QueueConfiguration";

    public static final String PROPERTY_ACTIVATE = "activate";
    private static final String PROPERTY_AGE = "age";
    public static final String PROPERTY_DEEP = "deep";
    private static final String PROPERTY_INITIATOR = "initiator";
    public static final String PROPERTY_PLAN = "plan";
    public static final String PROPERTY_PRE_LOG = "prelog";
    private static final String PROPERTY_TOPIC = "topic";
    public static final String PROPERTY_USER = "user";

    private static final String EVENT_ROLLOUT = "rollout";
    private static final String EVENT_ACTIVATION = "activation";

    private static final String ERROR_MISSING_USER = "User ID is missing";
    private static final String ERROR_MISSING_PLAN = "Rollout plan is missing";
    private static final String ERROR_NO_RESOLVER = "Could not retrieve a resource resolver";

    private static final String COMMA_SPACE = ", ";

    @Reference
    private transient ConfigurationAdmin configurationAdmin;

    @Reference
    private transient LiveRelationshipManager liveRelationshipManager;

    @Reference
    private transient JobManager jobManager;

    @Reference
    private transient RolloutManager rolloutManager;

    @Reference
    private transient ResourceResolverFactory resourceResolverFactory;

    @Reference
    private transient Replicator replicator;

    @Reference
    private transient SlingRepository repository;

    /* --------------
       Initialization
       -------------- */


    @Activate
    private void activate() {
        provideQueue();
        provideCleanupTask();
    }

    private void provideQueue() {
        try {
            Configuration queueConfig = configurationAdmin.getFactoryConfiguration(
                QUEUE_CONFIG_PID,
                getClass().getName(),
                null);
            Map<String, Object> queueProperties = new HashMap<>();
            queueProperties.put("queue.keepJobs", true);
            queueProperties.put("queue.name", RolloutExecutor.class.getName());
            queueProperties.put("queue.retries", 0);
            queueProperties.put("queue.topics", new String[] { TOPIC });
            queueProperties.put("queue.type", "ORDERED");
            queueConfig.update(new Hashtable<>(queueProperties));
        } catch (Exception e) {
            LOG.error("Could initialize a queue configuration", e);
        }
    }

    private void provideCleanupTask() {
        jobManager.getScheduledJobs()
            .stream()
            .filter(job ->
                CLEANUP_TOPIC.equals(job.getJobTopic())
                    && RolloutExecutor.class.getName().equals(job.getJobProperties().get(PROPERTY_INITIATOR)))
            .forEach(ScheduledJobInfo::unschedule);
        Map<String, Object> properties = new HashMap<>();
        properties.put(PROPERTY_INITIATOR, RolloutExecutor.class.getName());
        properties.put(PROPERTY_TOPIC, TOPIC.replace('/', '.'));
        properties.put(PROPERTY_AGE, CLEANUP_THRESHOLD_MINS);
        jobManager
            .createJob(CLEANUP_TOPIC)
            .properties(properties)
            .schedule()
            .cron(CLEANUP_CRON_EXPRESSION)
            .add();
    }

    /* ---------------
       Main processing
       --------------- */

    @Override
    public JobExecutionResult process(Job job, JobExecutionContext context) {
        String user = job.getProperty(PROPERTY_USER, String.class);
        if (StringUtils.isBlank(user)) {
            LOG.error(ERROR_MISSING_USER);
            return context.result().message(ERROR_MISSING_USER).failed();
        }

        String plan = job.getProperty(PROPERTY_PLAN, String.class);
        if (StringUtils.isBlank(plan)) {
            LOG.error(ERROR_MISSING_PLAN);
            return context.result().message(ERROR_MISSING_PLAN).failed();
        }

        LOG.info("Starting rollout job ID={} initiated by {} with the plan: {}", job.getId(), user, plan);
        StopWatch sw = StopWatch.createStarted();

        try (ResourceResolver resolver = newResourceResolver(user)) {
            return process(job, context, resolver, plan);
        } catch (LoginException | RepositoryException e) {
            LOG.error(ERROR_NO_RESOLVER, e);
            return context.result().message(ERROR_NO_RESOLVER).failed();
        } finally {
            sw.stop();
            LOG.info("Rollout job ID={} finished in {} ms", job.getId(), sw.getTime(TimeUnit.MILLISECONDS));
        }
    }

    private JobExecutionResult process(Job job, JobExecutionContext context, ResourceResolver resolver, String plan) {
        boolean isDeep = job.getProperty(PROPERTY_DEEP, Boolean.class);
        boolean shouldActivate = job.getProperty(PROPERTY_ACTIVATE, Boolean.class);
        RolloutItem[] rolloutItems = RolloutPlanUtil.getItems(plan);

        try (ThrottledLogger logger = new ThrottledLogger(context)) {
            RolloutProcessing processing = new RolloutProcessing(resolver, isDeep, shouldActivate, logger);
            if (processing.getPageManager() == null) {
                String message = "Could not retrieve a page manager";
                LOG.error(message);
                return context.result().message(message).failed();
            }
            if (processing.getSession() == null) {
                String message = "Could not retrieve a JCR session";
                LOG.error(message);
                return context.result().message(message).failed();
            }
            processing.processAll(rolloutItems);

            StringBuilder result = new StringBuilder("Completed");
            if (!processing.getFailedRollouts().isEmpty()) {
                result.append(". Could not perform rollout to the following path(-s): ")
                        .append(String.join(COMMA_SPACE, processing.getFailedRollouts()));
            }
            if (!processing.getFailedActivations().isEmpty()) {
                result.append(". Could not activate the following path(-s): ")
                        .append(String.join(COMMA_SPACE, processing.getFailedActivations()));
            }
            return context.result().message(result.toString()).succeeded();
        }
    }

    /* ----------------------------
       Rollout and activation logic
       ---------------------------- */

    private class RolloutProcessing {
        private final ThrottledLogger logger;
        private final PageManager pageManager;
        private final ResourceResolver resolver;
        private final Session session;
        private final boolean isDeep;
        private final boolean shouldActivate;

        private final List<String> failedActivations = new ArrayList<>();
        private final List<String> failedRollouts = new ArrayList<>();

        RolloutProcessing(
                ResourceResolver resolver,
                boolean isDeep,
                boolean shouldActivate,
                ThrottledLogger logger) {
            this.resolver = resolver;
            this.pageManager = resolver.adaptTo(PageManager.class);
            this.session = resolver.adaptTo(Session.class);
            this.isDeep = isDeep;
            this.shouldActivate = shouldActivate;
            this.logger = logger;
        }

        public List<String> getFailedActivations() {
            return failedActivations;
        }

        public List<String> getFailedRollouts() {
            return failedRollouts;
        }

        public PageManager getPageManager() {
            return pageManager;
        }

        public Session getSession() {
            return session;
        }

        void processAll(RolloutItem[] items) {
            List<List<RolloutItem>> groupsSortedByDepth = Arrays.stream(items)
                    .collect(Collectors.groupingBy(RolloutItem::getDepth))
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
            groupsSortedByDepth.forEach(this::processGroup);
        }

        private void processGroup(List<RolloutItem> items) {
            items.sort(RolloutItem::compareTo);
            for (RolloutItem item : items) {
                if (StringUtils.isBlank(item.getTarget())) {
                    LOG.debug("Rollout skipped because the target path is blank for master {}", item.getMaster());
                    continue;
                }
                boolean successfullyRolledOut = rolloutOne(item);
                if (successfullyRolledOut && shouldActivate) {
                    if (isBlueprint(item, liveRelationshipManager, resolver)) {
                        LOG.debug("Activation skipped because the target is a blueprint page: {}", item.getTarget());
                    } else {
                        activateOne(item);
                    }
                }
            }
        }

        private boolean rolloutOne(RolloutItem item) {
            String targetPath = item.getTarget();

            String masterPath = item.getMaster();
            Page masterPage = pageManager.getPage(masterPath);
            if (masterPage == null) {
                LOG.warn("Rollout failed: master page is missing at {}", masterPath);
                RolloutLogUtil.logEvent(logger, EVENT_ROLLOUT, targetPath, "Source page is missing");
                return false;
            }

            // We don't do forced rollout for the paths that auto-trigger a rollout by themselves,
            // but we report such paths as "rollout done" to keep the user calm
            if (isAutoTrigger(item)) {
                LOG.debug(
                        "Rollout from {} to {} skipped because an automatic rollout is triggered at this path",
                        item.getMaster(),
                        item.getTarget());
                RolloutLogUtil.logEvent(logger, EVENT_ROLLOUT, targetPath);
                return true;
            }

            RolloutManager.RolloutParams params = createRolloutParams(masterPage, targetPath, isDeep);
            try {
                LOG.debug("Rollout from {} to {} started", masterPath, targetPath);
                rolloutManager.rollout(params);
                LOG.debug("Rollout from {} to {} finished", masterPath, targetPath);
                RolloutLogUtil.logEvent(logger, EVENT_ROLLOUT, targetPath);
                return true;
            } catch (WCMException e) {
                LOG.error("Rollout from {} to {} failed", masterPath, targetPath, e);
                RolloutLogUtil.logEvent(logger, EVENT_ROLLOUT, targetPath, e.getMessage());
                failedRollouts.add(targetPath);
                discardUnsavedChanges(masterPage);
            }
            return false;
        }

        private void activateOne(RolloutItem item) {
            String targetPath = item.getTarget();
            Page page = pageManager.getPage(targetPath);
            if (page == null) {
                LOG.warn("Activation skipped: page is missing at {}", targetPath);
                RolloutLogUtil.logEvent(logger, EVENT_ACTIVATION, targetPath, "Page is missing");
                failedActivations.add(targetPath);
                return;
            }
            activateOne(page);
        }

        private void activateOne(Page page) {
            try {
                LOG.debug("Activating {}", page.getPath());
                replicator.replicate(session, ReplicationActionType.ACTIVATE, page.getPath());
                RolloutLogUtil.logEvent(logger, EVENT_ACTIVATION, page.getPath());
            } catch (ReplicationException e) {
                LOG.error("Activation of {} failed", page.getPath(), e);
                RolloutLogUtil.logEvent(logger, EVENT_ACTIVATION, page.getPath(), e.getMessage());
                failedActivations.add(page.getPath());
                return;
            }
            if (isDeep) {
                for (Iterator<Page> children = page.listChildren(); children.hasNext(); ) {
                    Page childPage = children.next();
                    activateOne(childPage);
                }
            }
        }
    }

    /* ----------------
       Repository logic
       ---------------- */

    private ResourceResolver newResourceResolver(String user) throws RepositoryException, LoginException {
        Session session = repository.impersonateFromService(
                "msm-service",
                new SimpleCredentials(user, StringUtils.EMPTY.toCharArray()),
                null);
        AuthenticationInfo authMap = new AuthenticationInfo(null);
        authMap.put("user.jcr.session", session);
        return resourceResolverFactory.getResourceResolver(authMap);
    }

    /* ------------------
       Rollout item logic
       ------------------ */

    private static RolloutManager.RolloutParams createRolloutParams(Page masterPage, String targetPath, boolean isDeep) {
        RolloutManager.RolloutParams params = new RolloutManager.RolloutParams();
        params.master = masterPage;
        params.targets = new String[]{targetPath};
        params.isDeep = isDeep;
        params.trigger = RolloutManager.Trigger.ROLLOUT;
        return params;
    }

    private static boolean isAutoTrigger(RolloutItem item) {
        return item.getDepth() != 0 && item.isAutoRolloutTrigger();
    }

    private static boolean isBlueprint(
            RolloutItem item,
            LiveRelationshipManager relationshipManager,
            ResourceResolver resolver) {
        try {
            return relationshipManager.getLiveRelationships(resolver.getResource(item.getTarget()), null, null).hasNext();
        } catch (WCMException e) {
            LOG.debug("Could not retrieve live relationships for {}", item.getTarget(), e);
        }
        return false;
    }

    /* ----------------
       Failure handling
       ---------------- */

    private static void discardUnsavedChanges(Page masterPage) {
        Optional.of(masterPage)
                .map(page -> page.adaptTo(Resource.class))
                .map(Resource::getResourceResolver)
                .ifPresent(ResourceResolver::revert);
    }
}
