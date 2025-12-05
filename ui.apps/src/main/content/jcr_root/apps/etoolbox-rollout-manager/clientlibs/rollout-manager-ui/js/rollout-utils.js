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
    const PROCESSING_ERROR_MSG = Granite.I18n.get('Failed because of');
    const STATUS_UPDATE_INTERVAL = 5000;

    $(function () {
        const data = ns.getItemsData();
        const dialogData = ns.getOpenDialogData();
        if (!data.length && !dialogData.length) return;

        let logger;
        if (dialogData.length) {
            const loggerData = dialogData[0];
            const shouldUpdateData = data.every(item => item.id !== loggerData.id);
            if (shouldUpdateData) ns.changeItemsData('add', loggerData.id, 0, loggerData.path);
            logger = ns.createLoggerDialog(loggerData.path);
        }
        const startIdArray = getStartIdArray();
        createStatusUpdater(logger, startIdArray, !!dialogData.length)
            .catch((e) => {
                logger ? logger.finished(`${PROCESSING_ERROR_MSG} ${e}`) : console.log(`${PROCESSING_ERROR_MSG} ${e}`)
            });
    });

    async function doItemsRollout(data) {
        const logger = ns.createLoggerDialog();
        try {
            const response = await buildRolloutRequest(data);
            if (response.task) {
                ns.setOpenDialogKey(JSON.stringify([{id: response.task, path: data.path}]));
                ns.changeItemsData('add', response.task, 0, data.path);

                if (!ns.getItemsData().length) throw new Error('No active tasks found');
                const startIdArray = getStartIdArray();
                await promisifyTimeout(1000);
                await createStatusUpdater(logger, startIdArray);
            }
        } catch (e) {
           if (e.statusText === 'Aborted requested') return;
           logger.finished(`${PROCESSING_ERROR_MSG} ${e}`);
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

    async function createStatusUpdater(logger, startIdArray = [], offsetFromStart = false) {
        let response = { tasks: [{'status': 'active'}]};
        while (response.tasks && response.tasks.some(item => item.status === 'active')) {
            const data = ns.getItemsData();
            if (!data.length) return;

            const id = data.map(item => item.id).join(';');
            const offset =  data.map(item => {
                if (!offsetFromStart) return item.offset;
                return isOpenDialogTask(item.id) ? 0 : item.offset
            }).join(';');
            const currentIdArray = id.split(';');
            const isAbortRequest = currentIdArray.some(item => !startIdArray.includes(item));
            response = await getStatusInfo(isAbortRequest, id, offset);
            response.tasks.forEach((task) => handleTaskResponse(task, logger));
            await promisifyTimeout(STATUS_UPDATE_INTERVAL);
        }
    }

    async function getStatusInfo(isAbortRequest, taskId, offset = '0') {
        try {
            const params = new URLSearchParams({ task: taskId, 'offset': offset });
            const url = `${CHECK_STATUS_COMMAND}?${params}`;
            const request = $.ajax({ url });
            if (isAbortRequest) return request.abort('Aborted requested');
            return await request;
        } catch (e) {
            throw new Error(e.responseJSON.error || 'Job was not found');
        }
    }

    function getStartIdArray() {
        return ns.getItemsData().map(item => item.id);
    }

    function isOpenDialogTask(id) {
        const openDialogData = ns.getOpenDialogData(id);
        return openDialogData.length && openDialogData[0].id === id
    }

    function handleTaskResponse(task, logger) {
        const isOpenDialog = isOpenDialogTask(task.id);
        let { offset, path } = ns.getItemsData().find(item => item.id === task.id);

        if (task.error) {
            handleTaskError(task, isOpenDialog, logger, path)
            return;
        }

        if (task.messages && task.messages.length) {
            offset = updateTaskOffset(task, offset, isOpenDialog, logger);
        }

        const isActiveTask = task.status && task.status === 'active';
        ns.changeItemsData(isActiveTask ? 'update' : 'remove', task.id, offset);
        if (isActiveTask) return;
        isOpenDialog ? logger.finished(`${task.result}`) : ns.showStatusMessage(path, `${task.result}`, !task.status ? 'error' : 'success');
    }

    function handleTaskError(task, isOpenDialog, logger, path) {
        isOpenDialog ? logger.finished(`${task.error}`) : ns.showStatusMessage(path, `${task.error}`, 'error');
        ns.changeItemsData('remove', task.id);
    }

    function updateTaskOffset(task, offset, isOpenDialog, logger) {
        return task.messages.reduce((total, msg) => {
            isOpenDialog && logger.log(msg, task.queue);
            return msg.id > total ? msg.id : total;
        }, offset);
    }

    function promisifyTimeout(interval) {
        return new Promise((resolve) => setTimeout(resolve, interval));
    }
})(Granite.$, window.ERM = (window.ERM || {}), Granite);
