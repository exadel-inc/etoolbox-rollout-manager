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
(function (window, document, $, ns, Granite) {
    'use strict';

    const foundationUi = $(window).adaptTo('foundation-ui');

    const COLLECT_LIVE_COPIES_COMMAND = '/content/etoolbox/rollout-manager/servlet/collect-live-copies';
    const ROLLOUT_DIALOG_ERROR_MSG = Granite.I18n.get('Rollout process failed for path:');
    const AJAX_TIMEOUT = 30000; // 30 seconds timeout

    /**
     * Retrieves data related to eligible for synchronization live copies as a json array. The data is
     * used for building 'Targets' tree in the UI dialog
     * @param path - path of the page selected in Sites
     * @returns {Promise}
     */
    async function collectLiveCopies(path) {
        try {
            const result = await $.ajax({
                url: COLLECT_LIVE_COPIES_COMMAND,
                type: 'POST',
                data: { _charset_: 'UTF-8', path },
                timeout: AJAX_TIMEOUT
            });

            if (result) return result;
        } catch (e) {
            // Handle the error below
        }

        foundationUi.alert('Error', `${ROLLOUT_DIALOG_ERROR_MSG} ${path}`, 'error');
    }

    const BLUEPRINT_CHECK_COMMAND = '/content/etoolbox/rollout-manager/servlet/blueprint-check';

    /**
     * Checks if selected page has live relationships eligible for synchronization and thus can be rolled out.
     * The 'Rollout' button is displayed in the Sites toolbar based on this condition.
     * @param path - path of the page selected in Sites
     * @returns {Promise<boolean>}
     */
    async function isAvailableForRollout(path) {
        try {
            const data = await $.ajax({
                url: BLUEPRINT_CHECK_COMMAND,
                type: 'POST',
                data: { _charset_: 'UTF-8', path },
                timeout: AJAX_TIMEOUT
            });
            return data && data.isAvailableForRollout;
        } catch (e) {
            console.error('Failed to check if page is available for rollout. Path: ', path, e);
            return false;
        }
    }

    const PROCESSING_LABEL = Granite.I18n.get('Processing');
    const ROLLOUT_IN_PROGRESS_LABEL = Granite.I18n.get('Rollout in progress ...');

    /**
     * Performs rollout based on data collected in the Rollout dialog.
     *
     * @param data - selected live copies data and isDeepRollout param retrieved from the Rollout dialog
     * @param rolloutRequest - {@link #buildRolloutRequest}
     * @returns {Promise<void>}
     */
    async function doItemsRollout(data, rolloutRequest) {
        const logger = ns.createLoggerDialog(PROCESSING_LABEL, ROLLOUT_IN_PROGRESS_LABEL, data.path);
        try {
            await rolloutRequest(data, logger)();
        } finally {
            logger.finished();
        }
    }

    const ROLLOUT_COMMAND = '/content/etoolbox/rollout-manager/servlet/rollout';
    const PROCESSING_ERROR_MSG = Granite.I18n.get('Rollout failed');
    const PROCESSING_ERROR_FAILED_PATHS_MSG = Granite.I18n.get('Rollout failed for the following paths:');
    const SUCCESS_MSG = Granite.I18n.get('Completed');
    const SUCCESS_REPLICATION_MSG = Granite.I18n.get('Rollout is completed. Publishing is in progress.');

    function getProcessingErrorMsg(xhr) {
        if (xhr.status === 400 && xhr.responseJSON && xhr.responseJSON.failedTargets) {
            return `${PROCESSING_ERROR_FAILED_PATHS_MSG}<br/><br/>${xhr.responseJSON.failedTargets.join('<br/>')}`;
        }
        return PROCESSING_ERROR_MSG;
    }

    /**
     * Builds a request to the servlet for rolling out items based on data collected in the Rollout dialog.
     * @param data - selected live copies data and isDeepRollout param retrieved from the Rollout dialog
     * @param logger - the logger dialog displaying progress of the rollout process
     * @returns {function(): Promise<void>}
     */
    function buildRolloutRequest(data, logger) {
        return async function () {
            try {
                await $.ajax({
                    url: ROLLOUT_COMMAND,
                    type: 'POST',
                    data: {
                        _charset_: 'UTF-8',
                        selectionJsonArray: JSON.stringify(data.selectionJsonArray),
                        isDeepRollout: data.isDeepRollout,
                        shouldActivate: data.shouldActivate
                    },
                    timeout: AJAX_TIMEOUT * 4 // Rollout operations may take longer
                });
                data.shouldActivate ? logger.log(SUCCESS_REPLICATION_MSG, false) : logger.log(SUCCESS_MSG, false);
            } catch (xhr) {
                logger.log(getProcessingErrorMsg(xhr), false);
            }
        };
    }

    /** Action handler for the 'Rollout' button */
    async function onShowRolloutDialog(name, el, config, collection, selections) {
        const selectedPath = selections[0].dataset.foundationCollectionItemId;
        foundationUi.wait();

        const liveCopiesJsonArray = await collectLiveCopies(selectedPath);
        foundationUi.clearWait();

        try {
            const data = await ns.showRolloutDialog(liveCopiesJsonArray, selectedPath);
            await doItemsRollout(data, buildRolloutRequest);
        } catch {
            // The dialog is closed by user
        }
    }

    /** Active condition for the 'Rollout' button */
    async function onRolloutActiveCondition(name, el, config, collection, selections) {
        const selectedPath = selections[0].dataset.foundationCollectionItemId;
        return await isAvailableForRollout(selectedPath);
    }

    // Init action handler for the 'Rollout' button
    $(window).adaptTo('foundation-registry')
        .register('foundation.collection.action.action', {
            name: 'etoolbox.rollout-manager.show-references-dialog',
            handler: onShowRolloutDialog
        });
    // Init active condition for the 'Rollout' button
    $(window).adaptTo('foundation-registry')
        .register('foundation.collection.action.activecondition', {
            name: 'etoolbox.rollout-manager.rollout-active-condition',
            handler: onRolloutActiveCondition
        });
})(window, document, Granite.$, window.erm = (window.erm || {}), Granite);
