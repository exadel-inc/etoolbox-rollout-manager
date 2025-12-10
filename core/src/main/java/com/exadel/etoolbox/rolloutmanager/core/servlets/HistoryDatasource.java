package com.exadel.etoolbox.rolloutmanager.core.servlets;

import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.exadel.etoolbox.rolloutmanager.core.models.LogEvent;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutItem;
import com.exadel.etoolbox.rolloutmanager.core.services.impl.RolloutExecutor;
import com.exadel.etoolbox.rolloutmanager.core.utils.RolloutLogUtil;
import com.exadel.etoolbox.rolloutmanager.core.utils.RolloutPlanUtil;
import com.exadel.etoolbox.rolloutmanager.core.utils.ThrottledLogger;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component(
    service = Servlet.class,
    property = ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=etoolbox-rollout-manager/components/historyDatasource"
)
public class HistoryDatasource extends SlingSafeMethodsServlet {

    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_OFFSET = "offset";

    private static final int DEFAULT_LIMIT = 40;
    private static final int NO_LIMIT = 0;
    private static final Map<String,Object>[] NO_FILTER = null;

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Reference
    private transient JobManager jobManager;

    @Override
    protected void doGet(
        SlingHttpServletRequest request,
        SlingHttpServletResponse response) {

        String offsetParam = request.getParameter(PARAM_OFFSET);
        int offset = StringUtils.isNumeric(offsetParam) ? Integer.parseInt(offsetParam) : 0;
        String limitParam = request.getParameter(PARAM_LIMIT);
        if (StringUtils.isEmpty(limitParam)) {
            limitParam = request.getResource().getValueMap().get(PARAM_LIMIT, String.class);
        }
        int limit = (StringUtils.isNumeric(limitParam) ? Integer.parseInt(limitParam) : DEFAULT_LIMIT) + 1;

        List<Job> jobs = new ArrayList<>(jobManager.findJobs(JobManager.QueryType.ALL, RolloutExecutor.TOPIC, NO_LIMIT, NO_FILTER));
        jobs.addAll(jobManager.findJobs(JobManager.QueryType.HISTORY, RolloutExecutor.TOPIC, NO_LIMIT, NO_FILTER));
        jobs.sort((j1, j2) -> Long.compare(j2.getCreated().getTimeInMillis(), j1.getCreated().getTimeInMillis()));

        jobs = jobs.subList(offset, Math.min(jobs.size(), offset + limit));

        List<Resource> resources = new ArrayList<>();
        SimpleDateFormat dateFormat = null;

        for (Job job : jobs) {
            if (dateFormat == null) {
                dateFormat = new SimpleDateFormat(DATE_FORMAT);
            }

            Map<String, Object> props = new HashMap<>();
            props.put(
                "status",
                getStatusString(job));
            props.put(
                "dateCreated",
                dateFormat.format(job.getCreated().getTime()));
            props.put(
                "dateStarted",
                job.getProcessingStarted() != null ? dateFormat.format(job.getProcessingStarted().getTime()) : null);
            props.put(
                "dateFinished",
                job.getFinishedDate() != null ? dateFormat.format(job.getFinishedDate().getTime()) : null);
            props.put(
                "initiator",
                job.getProperty(RolloutExecutor.PROPERTY_USER, String.class));
            JobDetails jobDetails = new JobDetails(job);
            props.put(
                "source",
                jobDetails.getSource());
            props.put(
                "target",
                jobDetails.getTargets().stream().map(t -> getResultHtml(t, jobDetails)).collect(Collectors.toList()));

            ValueMap valueMap = new ValueMapDecorator(props);
            Resource resource = new ValueMapResource(
                request.getResourceResolver(),
                request.getRequestPathInfo().getResourcePath() + "/item_" + job.getId(),
                JcrConstants.NT_UNSTRUCTURED,
                valueMap);
            resources.add(resource);
        }

        if (resources.isEmpty()) {
            return;
        }
        request.setAttribute(DataSource.class.getName(), new SimpleDataSource(resources.iterator()));
    }

    private static String getStatusString(Job job) {
        Job.JobState state = job.getJobState();
        if (state == Job.JobState.SUCCEEDED) {
            return "success";
        }
        if (state == Job.JobState.ERROR
            || state == Job.JobState.GIVEN_UP
            || state == Job.JobState.DROPPED
            || state == Job.JobState.STOPPED) {
            return "failure";
        }
        return state.name().toLowerCase();
    }

    private static String getResultHtml(String target, JobDetails details) {
        LogEvent.Result activationResult = details.getActivationResult(target);
        LogEvent.Result rolloutResult = details.getRolloutResult(target);
        StringBuilder sb = new StringBuilder();
        if (rolloutResult != null) {
            sb.append("<coral-icon icon=\"")
                .append(rolloutResult == LogEvent.Result.SUCCESS ? "check" : "close")
                .append("\"></coral-icon>");
        }
        sb.append(target);
        if (activationResult != null) {
            sb.append(" <span class=\"activation")
                .append(activationResult == LogEvent.Result.SUCCESS ? " success" : " error")
                .append("\">Activate</span>");
        }
        return sb.toString();
    }

    private static class JobDetails {
        private Map<String, LogEvent.Result> activationReports;
        private Map<String, LogEvent.Result> rolloutReports;
        private String source;
        private List<String> targets;

        JobDetails(Job job) {
            String plan = job.getProperty(RolloutExecutor.PROPERTY_PLAN, String.class);
            RolloutItem[] items = RolloutPlanUtil.getItems(plan);
            if (ArrayUtils.isEmpty(items)) {
                return;
            }
            assert items != null;
            Arrays.sort(items);
            source = items[0].getMaster();
            targets = Stream.of(items)
                .map(RolloutItem::getTarget)
                .distinct()
                .collect(Collectors.toList());

            String[] log = job.getProgressLog();
            if (ArrayUtils.isEmpty(log)) {
                return;
            }
            Stream.of(log)
                .flatMap(entry -> StringUtils.contains(entry, ThrottledLogger.ENTRY_SEPARATOR)
                    ? Arrays.stream(StringUtils.split(entry, ThrottledLogger.ENTRY_SEPARATOR))
                    : Stream.of(entry))
                .filter(StringUtils::isNotBlank)
                .map(RolloutLogUtil::getEvent)
                .filter(Objects::nonNull)
                .forEach(e -> {
                    if (e.getType() == LogEvent.Type.ACTIVATION) {
                        if (activationReports == null) {
                            activationReports = new HashMap<>();
                        }
                        activationReports.put(e.getPath(), e.getResult());
                    } else if (e.getType() == LogEvent.Type.ROLLOUT) {
                        if (rolloutReports == null) {
                            rolloutReports = new HashMap<>();
                        }
                        rolloutReports.put(e.getPath(), e.getResult());
                    }
                });
        }

        public LogEvent.Result getActivationResult(String path) {
            return activationReports != null ? activationReports.get(path) : null;
        }

        public LogEvent.Result getRolloutResult(String path) {
            return rolloutReports != null ? rolloutReports.get(path) : null;
        }

        public String getSource() {
            return source;
        }

        public List<String> getTargets() {
            return targets;
        }
    }
}
