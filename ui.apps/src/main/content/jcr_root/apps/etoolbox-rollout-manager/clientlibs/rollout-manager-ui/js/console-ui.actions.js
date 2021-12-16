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
(function (window, document, $, ERM, Granite) {
    "use strict";

    var COLLECT_LIVE_COPIES_COMMAND = "/content/etoolbox-rollout-manager/servlet/collect-live-copies";

    /**
     * Retrieves data related to eligible for synchronization live copies as a json array. The data is
     * used for building 'Targets' tree in the UI dialog
     * @param path - path of the page selected in Sites
     * @returns {*}
     */
    function collectLiveCopies(path) {
        return $.ajax({
            url: COLLECT_LIVE_COPIES_COMMAND,
            type: "POST",
            data: {
                _charset_: "UTF-8",
                path: path
            }
        });
    }

    var BLUEPRINT_CHECK_COMMAND = "/content/etoolbox-rollout-manager/servlet/blueprint-check";

    /**
     * Checks if selected page has live relationships eligible for synchronization and thus can be rolled out.
     * The 'Rollout' button is displayed in the Sites toolbar based on this condition.
     * @param path - path of the page selected in Sites
     * @returns {boolean}
     */
    function isAvailableForRollout(path) {
        var result = false;
        $.ajax({
            url: BLUEPRINT_CHECK_COMMAND,
            type: "POST",
            async: false,
            data: {
                _charset_: "UTF-8",
                path: path
            }
        }).done(function (data) {
            result = data && data.isAvailableForRollout;
        });
        return result;
    }

    var PROCESSING_LABEL = Granite.I18n.get("Processing");
    var ROLLOUT_IN_PROGRESS_LABEL = Granite.I18n.get("Rollout in progress ...");

    /**
     * Performs rollout based on data collected in the Rollout dialog.
     *
     * @param data - selected live copies data and isDeepRollout param retrieved from the Rollout dialog
     * @param rolloutRequest - {@link #buildRolloutRequest}
     * @returns {*}
     */
    function doItemsRollout(data, rolloutRequest) {
        var logger = ERM.createLoggerDialog(PROCESSING_LABEL, ROLLOUT_IN_PROGRESS_LABEL, data.path);
        var requests = $.Deferred().resolve().then(rolloutRequest(data, logger));
        requests.always(function () {
            logger.finished();
        });
        return requests;
    }

    var ROLLOUT_COMMAND = "/content/etoolbox-rollout-manager/servlet/rollout";
    var PROCESSING_ERROR_MSG = Granite.I18n.get("Rollout failed");
    var PROCESSING_ERROR_FAILED_PATHS_MSG = Granite.I18n.get("Rollout failed for the following paths:");
    var SUCCESS_MSG = Granite.I18n.get("Selected live copies successfully synchronized");

    /**
     * Build a request to the servlet for rolling out items based on data collected in the Rollout dialog.
     * @param data - selected live copies data and isDeepRollout param retrieved from the Rollout dialog
     * @param logger - the logger dialog displaying progress of the rollout process
     * @returns {function(): *}
     */
    function buildRolloutRequest(data, logger) {
        return function () {
            return $.ajax({
                url: ROLLOUT_COMMAND,
                type: "POST",
                data: {
                    _charset_: "UTF-8",
                    selectionJsonArray: JSON.stringify(data.selectionJsonArray),
                    isDeepRollout: data.isDeepRollout
                }
            }).fail(function (xhr) {
                if (xhr.status === 400 && xhr.responseJSON && xhr.responseJSON.failedTargets) {
                    logger.log(PROCESSING_ERROR_FAILED_PATHS_MSG + "<br/><br/>" +
                        xhr.responseJSON.failedTargets.join("<br/>"), false);
                } else {
                    logger.log(PROCESSING_ERROR_MSG, false);
                }
            }).done(function () {
                logger.log(SUCCESS_MSG, false);
            });
        };
    }

    /** Action handler for the 'Rollout' button */
    function onShowRolloutDialog(name, el, config, collection, selections) {
        var selectedPath = selections[0].dataset.foundationCollectionItemId;
        var foundationUi = $(window).adaptTo("foundation-ui");
        // Show a wait mask before the live copies data is fully collected
        foundationUi.wait();
        collectLiveCopies(selectedPath).then(function (liveCopiesJsonArray) {
                // Clears the wait mask once the dialog is loaded
                foundationUi.clearWait();
                ERM.showRolloutDialog(liveCopiesJsonArray, selectedPath).then(function (data) {
                    doItemsRollout(data, buildRolloutRequest);
                });
            }
        );
    }

    /** Active condition for the 'Rollout' button */
    function onRolloutActiveCondition(name, el, config, collection, selections) {
        var selectedPath = selections[0].dataset.foundationCollectionItemId;
        return isAvailableForRollout(selectedPath);
    }

    // Init action handler for the 'Rollout' button
    $(window).adaptTo("foundation-registry").register("foundation.collection.action.action", {
        name: "etoolbox.rollout-manager.show-references-dialog",
        handler: onShowRolloutDialog
    });
    // Init active condition for the 'Rollout' button
    $(window).adaptTo("foundation-registry").register("foundation.collection.action.activecondition", {
        name: "etoolbox.rollout-manager.rollout-active-condition",
        handler: onRolloutActiveCondition
    });

})(window, document, Granite.$, Granite.ERM, Granite);