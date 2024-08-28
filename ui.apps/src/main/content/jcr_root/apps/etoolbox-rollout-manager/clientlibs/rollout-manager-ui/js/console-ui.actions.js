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

    const COLLECT_LIVE_COPIES_COMMAND = '/content/etoolbox-rollout-manager/servlet/collect-live-copies';

    /**
     * Retrieves data related to eligible for synchronization live copies as a json array. The data is
     * used for building 'Targets' tree in the UI dialog
     * @param path - path of the page selected in Sites
     * @returns {*}
     */
    function collectLiveCopies(path) {
        return $.ajax({
            url: COLLECT_LIVE_COPIES_COMMAND,
            type: 'POST',
            data: {
                _charset_: 'UTF-8',
                path
            }
        });
    }

    const BLUEPRINT_CHECK_COMMAND = '/content/etoolbox-rollout-manager/servlet/blueprint-check';

    /**
     * Checks if selected page has live relationships eligible for synchronization and thus can be rolled out.
     * The 'Rollout' button is displayed in the Sites toolbar based on this condition.
     * @param path - path of the page selected in Sites
     * @returns {Promise<boolean>}
     */
    function isAvailableForRollout(path) {
        let result = false;
        $.ajax({
            url: BLUEPRINT_CHECK_COMMAND,
            type: 'POST',
            async: false,
            data: {
                _charset_: 'UTF-8',
                path
            }
        }).done((data) => {
            result = data && data.isAvailableForRollout;
        });
        return result;
    }

    const PROCESSING_LABEL = Granite.I18n.get('Processing');
    const ROLLOUT_IN_PROGRESS_LABEL = Granite.I18n.get('Rollout in progress ...');

    /**
     * Performs rollout based on data collected in the Rollout dialog.
     *
     * @param data - selected live copies data and isDeepRollout param retrieved from the Rollout dialog
     * @param rolloutRequest - {@link #buildRolloutRequest}
     * @returns {*}
     */
    function doItemsRollout(data, rolloutRequest) {
        const logger = ns.createLoggerDialog(PROCESSING_LABEL, ROLLOUT_IN_PROGRESS_LABEL, data.path);
        return $.Deferred()
            .resolve()
            .then(rolloutRequest(data, logger))
            .always(() => {
                logger.finished();
            });
    }

    const ROLLOUT_COMMAND = '/content/etoolbox-rollout-manager/servlet/rollout';
    const PROCESSING_ERROR_MSG = Granite.I18n.get('Rollout failed');
    const PROCESSING_ERROR_FAILED_PATHS_MSG = Granite.I18n.get('Rollout failed for the following paths:');
    const SUCCESS_MSG = Granite.I18n.get('Completed');

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
     * @returns {function(): *}
     */
    function buildRolloutRequest(data, logger) {
        return function () {
            return $.ajax({
                url: ROLLOUT_COMMAND,
                type: 'POST',
                data: {
                    _charset_: 'UTF-8',
                    selectionJsonArray: JSON.stringify(data.selectionJsonArray),
                    isDeepRollout: data.isDeepRollout,
                    shouldActivate: data.shouldActivate
                }
            }).fail((xhr) => {
                logger.log(getProcessingErrorMsg(xhr), false);
            }).done(() => {
                logger.log(SUCCESS_MSG, false);
            });
        };
    }

    /** Action handler for the 'Rollout' button */
    function onShowRolloutDialog(name, el, config, collection, selections) {
        const selectedPath = selections[0].dataset.foundationCollectionItemId;
        const foundationUi = $(window).adaptTo('foundation-ui');
        // Show a wait mask before the live copies data is fully collected
        foundationUi.wait();
        collectLiveCopies(selectedPath)
            .then((liveCopiesJsonArray) => {
                // Clears the wait mask once the dialog is loaded
                foundationUi.clearWait();
                ns.showRolloutDialog(liveCopiesJsonArray, selectedPath)
                    .then((data) => {
                        doItemsRollout(data, buildRolloutRequest);
                    });
            });
    }

    /** Active condition for the 'Rollout' button */
    function onRolloutActiveCondition(name, el, config, collection, selections) {
        const selectedPath = selections[0].dataset.foundationCollectionItemId;
        return isAvailableForRollout(selectedPath);
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
