/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * EToolbox Rollout Manager Time Utility.
 * Contains utilities for time data processing
 */
(function ($, ns) {
    'use strict';
    ns.TimeUtil = {};

    const NOT_ROLLED_OUT_LABEL = Granite.I18n.get('Not Rolled Out');
    const TIME_AGO_LABEL = Granite.I18n.get('ago');
    const PLURAL_SUFFIX = 's';

    /**
     * Object containing time unit durations in milliseconds.
     */
    const Time = {
        YEAR: 365 * 24 * 60 * 60 * 1000,
        MONTH: 30 * 24 * 60 * 60 * 1000,
        DAY: 24 * 60 * 60 * 1000,
        HOUR: 60 * 60 * 1000,
        MINUTE: 60 * 1000,
        SECOND: 1000
    };

    /**
     * Options for formatting a timestamp.
     */
    const TIME_FORMATTER_OPTIONS = {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: 'numeric',
        hourCycle: 'h23',
        minute: '2-digit',
        second: '2-digit'
    };

    /**
     * Calculate and format the time difference between the given date and the current date.
     * @param {string} date - The date to calculate the time difference from (ISO 8601 format).
     * @returns {string} A formatted string indicating the time difference.
     */
    ns.TimeUtil.timeSince = function (date) {
        if (!date) {
            return NOT_ROLLED_OUT_LABEL;
        }

        const startDate = new Date(date);
        const now = new Date();
        const millisecondsBetween = now - startDate;

        let result = '';
        for (const timeUnit in Time) {
            if (millisecondsBetween >= Time[timeUnit]) {
                const time = Math.floor(millisecondsBetween / Time[timeUnit]);

                result = time + ' ' + Granite.I18n.get(timeUnit.toLowerCase() + (time !== 1 ? PLURAL_SUFFIX : '')) + ' ' + TIME_AGO_LABEL;
                break;
            }
        }

        return result;
    };

    /**
     * Display a timestamp in a formatted way.
     * @param {string} date - The date to format (ISO 8601 format).
     * @returns {string} A formatted timestamp string.
     */
    ns.TimeUtil.displayLastRolledOut = function (date) {
        if (!date) {
            return '';
        }
        return new Date(date).toLocaleString(undefined, TIME_FORMATTER_OPTIONS);
    };

})(Granite.$, window.erm = (window.erm || {}));
