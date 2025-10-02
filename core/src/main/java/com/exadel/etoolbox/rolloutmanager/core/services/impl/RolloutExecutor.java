package com.exadel.etoolbox.rolloutmanager.core.services.impl;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.day.cq.wcm.msm.api.RolloutManager;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutItem;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutStatus;
import com.exadel.etoolbox.rolloutmanager.core.services.PageReplicationService;
import com.exadel.etoolbox.rolloutmanager.core.utils.RolloutPlanUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes a rollout job asynchronously. The job is created in
 * {@link com.exadel.etoolbox.rolloutmanager.core.servlets.RolloutServlet}
 */
@Component(
        service = JobExecutor.class,
        property = JobExecutor.PROPERTY_TOPICS + "=" + RolloutExecutor.TOPIC)
public class RolloutExecutor implements JobExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RolloutExecutor.class);

    public static final String TOPIC = "com/exadel/etoolbox/rolloutmanager/rollout";

    public static final String PROPERTY_ACTIVATE = "activate";
    public static final String PROPERTY_DEEP = "deep";
    public static final String PROPERTY_PLAN = "plan";
    public static final String PROPERTY_USER = "user";

    private static final String PROPERTY_RESULT = "result";
    private static final String PROPERTY_TYPE = "type";

    private static final String ERROR_MISSING_USER = "User ID is missing";
    private static final String ERROR_MISSING_PLAN = "Rollout plan is missing";
    private static final String ERROR_NO_RESOLVER = "Could not retrieve a resource resolver";

    private static final String COMMA_SPACE = ", ";

    @Reference
    private transient PageReplicationService pageReplicationService;

    @Reference
    private transient RolloutManager rolloutManager;

    @Reference
    private transient ResourceResolverFactory resourceResolverFactory;

    @Reference
    private transient SlingRepository repository;

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
        PageManager pageManager = resolver.adaptTo(PageManager.class);
        RolloutItem[] rolloutItems = RolloutPlanUtil.getItems(plan);
        // Rollout
        List<RolloutStatus> rolloutStatuses = organizeAndRolloutAll(rolloutItems, pageManager, isDeep, context);
        List<RolloutStatus> failedRollouts = rolloutStatuses.stream()
                .filter(status -> !status.isSuccess())
                .collect(Collectors.toList());
        if (!failedRollouts.isEmpty()) {
            LOG.warn(
                    "Rollout failed for {} item(-s): {}",
                    failedRollouts.size(),
                    failedRollouts.stream().map(RolloutStatus::getTarget).collect(Collectors.joining(COMMA_SPACE)));
        }
        // Activation
        if (shouldActivate) {
            List<RolloutStatus> activationStatuses = pageReplicationService.replicateItems(resolver, rolloutItems, pageManager, isDeep);
            List<RolloutStatus> failedActivations = activationStatuses.stream()
                    .filter(status -> !status.isSuccess())
                    .collect(Collectors.toList());
            if (!failedActivations.isEmpty()) {
                LOG.warn(
                        "Activation failed for {} item(-s): {}",
                        failedActivations.size(),
                        failedActivations.stream().map(RolloutStatus::getTarget).collect(Collectors.joining(COMMA_SPACE)));
            }
        }
        return context.result().succeeded();
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

    /* -------------
       Rollout logic
       ------------- */

    private List<RolloutStatus> organizeAndRolloutAll(
            RolloutItem[] items,
            PageManager pageManager,
            boolean isDeep,
            JobExecutionContext context) {

        List<List<RolloutItem>> groupsSortedByDepth = Arrays.stream(items)
                .collect(Collectors.groupingBy(RolloutItem::getDepth))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        logTargets(
                context,
                groupsSortedByDepth.stream()
                        .flatMap(List::stream)
                        .map(RolloutItem::getTarget)
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toList()));

        return groupsSortedByDepth
                .stream()
                .flatMap(group -> rolloutGroup(group, pageManager, isDeep, context))
                .collect(Collectors.toList());
    }

    private Stream<RolloutStatus> rolloutGroup(
            List<RolloutItem> items,
            PageManager pageManager,
            boolean isDeep,
            JobExecutionContext context) {

        return items.stream()
                .filter(item -> StringUtils.isNotBlank(item.getTarget()))
                .map(item -> rolloutOne(item, pageManager, isDeep, context));
    }

    private RolloutStatus rolloutOne(
            RolloutItem targetItem,
            PageManager pageManager,
            boolean isDeep,
            JobExecutionContext context) {

        String targetPath = targetItem.getTarget();
        RolloutStatus status = new RolloutStatus(targetPath);

        if (isAutoTriggered(targetItem)) {
            status.setSuccess(true);
            logTarget(context, targetPath);
            return status;
        }

        String masterPath = targetItem.getMaster();
        Page masterPage = pageManager.getPage(masterPath);
        if (masterPage == null) {
            status.setSuccess(false);
            LOG.warn("Rollout failed: master page is missing at {}", masterPath);
            logTarget(context, targetPath, "Page is missing");
            return status;
        }

        RolloutManager.RolloutParams params = createRolloutParams(masterPage, targetPath, isDeep);
        try {
            LOG.debug("Rollout from {} to {} started", masterPath, targetPath);
            rolloutManager.rollout(params);
            status.setSuccess(true);
            LOG.debug("Rollout from {} to {} finished", masterPath, targetPath);
            logTarget(context, targetPath);
        } catch (WCMException e) {
            status.setSuccess(false);
            LOG.error("Rollout from {} to {} failed", masterPath, targetPath, e);
            logTarget(context, targetPath, e.getMessage());
            discardUnsavedChanges(masterPage);
        }
        return status;
    }

    /* ------------------
       Rollout item logic
       ------------------ */

    private static boolean isAutoTriggered(RolloutItem item) {
        boolean result = item.getDepth() != 0 && item.isAutoRolloutTrigger();
        if (result) {
            LOG.debug(
                    "Rollout from {} to {} skipped because an automatic rollout is triggered at this path",
                    item.getMaster(),
                    item.getTarget());
        }
        return result;
    }

    private static RolloutManager.RolloutParams createRolloutParams(Page masterPage, String targetPath, boolean isDeep) {
        RolloutManager.RolloutParams params = new RolloutManager.RolloutParams();
        params.master = masterPage;
        params.targets = new String[]{targetPath};
        params.isDeep = isDeep;
        params.trigger = RolloutManager.Trigger.ROLLOUT;
        return params;
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

    /* -------
       Logging
       ------- */

    private static void logTargets(JobExecutionContext context, List<String> targets) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        targets.forEach(arrayBuilder::add);
        String message = Json.createObjectBuilder()
                .add(PROPERTY_TYPE, "targets")
                .add("items", arrayBuilder.build())
                .build()
                .toString();
        context.log("{0}", message);
    }

    private static void logTarget(JobExecutionContext context, String target) {
        logTarget(context, target, null);
    }

    private static void logTarget(JobExecutionContext context, String target, String errorMessage) {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add(PROPERTY_TYPE, "target")
                .add("path", target);
        if (StringUtils.isNotEmpty(errorMessage)) {
            builder.add(PROPERTY_RESULT, "error");
            builder.add("error", errorMessage);
        } else {
            builder.add(PROPERTY_RESULT, "success");
        }
        String message = builder.build().toString();
        context.log("{0}", message);
    }
}
