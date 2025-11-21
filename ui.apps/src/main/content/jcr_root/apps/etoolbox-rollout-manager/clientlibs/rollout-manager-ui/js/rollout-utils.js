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

(function ($, ns, Granite) {
    'use strict';

    const ROLLOUT_COMMAND = '/content/etoolbox/rollout-manager/servlet/rollout';
    const CHECK_STATUS_COMMAND = '/content/etoolbox/rollout-manager/servlet/rollout/status';
  //  const SUCCESS_MSG = Granite.I18n.get('Rollout completed');
    const PROCESSING_ERROR_MSG = Granite.I18n.get('Failed because of');
    const STATUS_UPDATE_INTERVAL = 5000;

    $(function () {
        // const data = ns.getItemsData();
        // const dialogData = ns.getOpenDialogData();
        // if (!data.length && !dialogData.length) return;
        //
        // let logger;
        // if (dialogData.length) {
        //     const shouldUpdate = data.every(item => item.id !== dialogData.id);
        //     if (shouldUpdate) ns.changeItemsData('add', dialogData.id, 0, dialogData.path);
        //     logger = ns.createLoggerDialog(dialogData.path);
        // }
        // createStatusUpdater(logger)
        //     .catch((e) => logger.finished(`${PROCESSING_ERROR_MSG} ${e}`));
    });

    async function doItemsRollout(data) {
        const logger = ns.createLoggerDialog();
        try {
            const response = await buildRolloutRequest(data);
            if (response.task) {
                ns.setOpenDialogKey(JSON.stringify({id: response.task, path: data.path}));
                ns.changeItemsData('add', response.task, 0, data.path);
                await promisifyTimeout(1000);
                await createStatusUpdater(logger);
            }
        } catch (e) {
            logger ? logger.finished(`${PROCESSING_ERROR_MSG} ${e}`) : console.log(`${PROCESSING_ERROR_MSG} ${e}`);
        }
    }
    ns.doItemsRollout = doItemsRollout;

    async function buildRolloutRequest(dialogData) {
        const data = {
            _charset_: 'UTF-8',
            selectionJsonArray: JSON.stringify(dialogData.selectionJsonArray),
            isDeepRollout: dialogData.isDeepRollout,
            shouldActivate: dialogData.shouldActivate
        };

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

    async function createStatusUpdater(logger) {
        let response = { tasks: [{'status': 'active'}]};
        while (response.tasks && response.tasks.some(item => item.status === 'active')) {
            const data = ns.getItemsData();
            if (!data.length) throw new Error('No active tasks found');

            const id = data.map(item => item.id).join(';');
            const offset = data.map(item => item.offset).join(';');

            response = await getStatusInfo(id, offset);
            let limit = id.split(';').length;
            if (response.tasks.length < limit) {
                console.log('aborting due to missing tasks in response');
                response.abort();
            }
            response.tasks.forEach((task) => handleTaskResponse(task, logger));
            await promisifyTimeout(STATUS_UPDATE_INTERVAL);
        }
    }

    function handleTaskResponse(task, logger) {
        const openDialogData = ns.getOpenDialogData(task.id);
        const isOpenDialog = openDialogData.id && openDialogData.id === task.id;
        let { offset, path } = ns.getItemsData().find(item => item.id === task.id);

        if (task.error) {
            isOpenDialog ? logger.finished(`${task.error}`) : ns.showStatusMessage(path, `${task.error}`, 'error');
            ns.changeItemsData('remove', task.id);
            return;
        }

        if (task.messages && task.messages.length) {
            offset = task.messages.reduce((total, msg) => {
                isOpenDialog && logger.log(msg, task.queue);
                return msg.id > total ? msg.id : total;
            }, offset);
        }

        if (task.status && task.status === 'active') {
            ns.changeItemsData('update', task.id, offset);
        } else {
            isOpenDialog ? logger.finished(`${task.result}`) : ns.showStatusMessage(path, `${task.result}`, !task.status ? 'error' : 'success');
            ns.changeItemsData('remove', task.id);
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
