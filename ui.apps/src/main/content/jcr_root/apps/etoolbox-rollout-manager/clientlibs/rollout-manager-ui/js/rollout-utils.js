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
 * EToolbox Rollout Manager clientlib.
 * 'Rollout' button and dialog actions definition.
 */
(function ($, ns, Granite) {
    'use strict';

    const ROLLOUT_COMMAND = '/content/etoolbox/rollout-manager/servlet/rollout';
    const CHECK_STATUS_COMMAND = '/content/etoolbox/rollout-manager/servlet/rollout/status';
    const SUCCESS_REPLICATION_MSG = Granite.I18n.get('Rollout is completed. Publishing is in progress.');
    const SUCCESS_MSG = Granite.I18n.get('Rollout completed');
    const PROCESSING_ERROR_MSG = Granite.I18n.get('Rollout failed because of');
    const STATUS_UPDATE_INTERVAL = 5000;

    async function doItemsRollout(data) {
        const logger = ns.createLoggerDialog();
        try {
            const response = await buildRolloutRequest(data);
            logger.unblocked();
            if (response.task) {
                await createStatusUpdater(logger, response.task);
                logger.finished(data.shouldActivate ? SUCCESS_REPLICATION_MSG : SUCCESS_MSG);
            }
        } catch (e) {
            logger.finished(PROCESSING_ERROR_MSG + ' ' + e);
        }
    }
    ns.doItemsRollout = doItemsRollout;

    async function buildRolloutRequest(dialogData) {
        const data = {
            _charset_: 'UTF-8',
            selectionJsonArray: JSON.stringify(dialogData.selectionJsonArray),
            isDeepRollout: dialogData.isDeepRollout,
            shouldActivate: dialogData.shouldActivate
        }

        try {
            return await $.ajax({
                url: ROLLOUT_COMMAND,
                type: 'POST',
                data
            });
        } catch (e) {
            throw new Error(e.responseJSON.error);
        }
    }

    async function createStatusUpdater(logger, taskId) {
        let response = { status: 'active' };
        let offset = 0;
        while (response.status === 'active') {
            response = await getStatusInfo(taskId, offset);

            if (response.error) throw new Error(`${response.error}`);

            if (response.messages && response.messages.length) {
                offset = response.messages.reduce((total, msg) => {
                    logger.log(msg);
                    return msg.id > total ? msg.id : total;
                }, offset);
            }

            if (!response.status) throw new Error('Wrong response from the server');

            await promisifyTimeout(STATUS_UPDATE_INTERVAL);
        }
    }

    async function getStatusInfo(taskId, offset) {
        try {
            const params = new URLSearchParams({ task: taskId });
            if (offset) params.append('offset', offset);
            const url = `${CHECK_STATUS_COMMAND}?${params}`;
            return await $.ajax({ url });
        } catch (e) {
            throw new Error(e.responseJSON.error || 'Job was not found');
        }
    }

    function promisifyTimeout(interval) {
        return new Promise((resolve) => setTimeout(resolve, interval));
    }
})(Granite.$, window.ERM = (window.ERM || {}), Granite);
