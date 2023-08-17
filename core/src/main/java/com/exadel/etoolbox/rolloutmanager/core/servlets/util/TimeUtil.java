package com.exadel.etoolbox.rolloutmanager.core.servlets.util;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class TimeUtil {
    private static final String PLURAL_SUFFIX = "s";
    private static final String TIME_AGO_LABEL = " ago";

    public enum Time {
        YEAR(TimeUnit.DAYS.toMillis(365)),
        MONTH(TimeUnit.DAYS.toMillis(30)),
        DAY(TimeUnit.DAYS.toMillis(1)),
        HOUR(TimeUnit.HOURS.toMillis(1)),
        MINUTE(TimeUnit.MINUTES.toMillis(1)),
        SECOND(TimeUnit.SECONDS.toMillis(1));

        private final Long duration;

        Time(Long duration) {
            this.duration = duration;
        }

        public Long getDuration() {
            return duration;
        }
    }

    public static String timeSince(String date) {
        if (StringUtils.isEmpty(date)) {
            return StringUtils.EMPTY;
        }

        ZonedDateTime startDate = ZonedDateTime.parse(date, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(ZoneId.systemDefault());
        long secondsBetween = Duration.between(startDate, ZonedDateTime.now()).toMillis();

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Time.values().length; i++) {
            Long duration = Time.values()[i].getDuration();
            long time = secondsBetween / duration;

            if (time > 0) {
                result.append(time)
                        .append(StringUtils.SPACE)
                        .append(Time.values()[i].name().toLowerCase())
                        .append(time != 1 ? PLURAL_SUFFIX : StringUtils.EMPTY)
                        .append(TIME_AGO_LABEL);
                break;
            }
        }
        return result.toString();
    }
}
