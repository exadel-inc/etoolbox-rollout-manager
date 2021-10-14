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
 * "Rollout" action definition.
 */
(function (window, document, $, ERM, Granite) {
    'use strict';

    /** Root action handler */
    function onShowReferencesDialog(name, el, config, collection, selections) {
        var selectedPath = selections[0].dataset.foundationCollectionItemId;
        var liveCopiesJsonArray = collectLiveCopies(selectedPath);

        showDialog(liveCopiesJsonArray, selectedPath).then(function (data) {
            rolloutItems(data, selectedPath, buildRolloutRequest);
        });
    }

    var ROLLOUT_COMMAND = '/content/etoolbox-rollout-manager/servlet/rollout';
    var PROCESSING_ERROR_MSG = 'Rollout failed';

    function buildRolloutRequest(data, logger) {
        return function () {
            return $.ajax({
                url: ROLLOUT_COMMAND,
                type: "POST",
                data: {
                    liveCopiesArray: JSON.stringify(data)
                }
            }).fail(function (xhr, status, error) {
                if (xhr.status === 500) {
                    logger.log("Rollout failed for the following paths:<br/><br/>"
                        + xhr.responseJSON.failedTargets.join("<br/>"), false);
                } else {
                    logger.log(PROCESSING_ERROR_MSG, false);
                }
            }).done(function () {
                logger.log("Selected live copies successfully synchronized", false);
            });
        };
    }

    //todo checkboxes tree works improperly
    function appendNestedCheckboxList(liveCopiesJsonArray, sourceElement) {
        if (liveCopiesJsonArray.length > 0) {
            var nestedList = $('<ul class="foundation-nestedcheckboxlist" data-foundation-nestedcheckboxlist-disconnected="false">');
            for (var i = 0; i < liveCopiesJsonArray.length; i++) {
                var liveCopyJson = liveCopiesJsonArray[i];

                var item = $('<li class="foundation-nestedcheckboxlist-item">');
                var liveCopyCheckbox =
                    $('<coral-checkbox name="liveCopyProperties[]" data-master="' + liveCopyJson.master + '" data-depth="' + liveCopyJson.depth + '" value="' + liveCopyJson.path + '" checked>')
                        .text(liveCopyJson.path);
                liveCopyCheckbox.appendTo(item);
                appendNestedCheckboxList(liveCopyJson.liveCopies, item);

                item.appendTo(nestedList)
            }
            nestedList.appendTo(sourceElement);
        }
    }

    var CANCEL_LABEL = Granite.I18n.get('Cancel');
    var DIALOG_LABEL = Granite.I18n.get('Rollout');

    // Confirmation dialog common methods
    function showDialog(liveCopiesJsonArray, path) {
        var deferred = $.Deferred();

        var el = ERM.getSharableDlg();
        el.variant = 'notice';
        el.header.textContent = DIALOG_LABEL + " " + path;
        el.footer.innerHTML = ''; // Clean content
        el.content.innerHTML = ''; // Clean content

        var $cancelBtn = $('<button is="coral-button" variant="default" coral-close>').text(CANCEL_LABEL);
        var $updateBtn = $('<button data-dialog-action is="coral-button" variant="primary" coral-close>').text(DIALOG_LABEL);
        $cancelBtn.appendTo(el.footer);
        $updateBtn.appendTo(el.footer);

        appendNestedCheckboxList(liveCopiesJsonArray, el.content);

        // function onValidate() {
        // }

        var onResolve = function () {
            var selectedLiveCopies = [];
            //todo get only selected
            $("coral-checkbox[name='liveCopyProperties[]']").each(function () {
                var selectedLiveCopyJson = {};
                selectedLiveCopyJson.master = $(this).data("master");
                selectedLiveCopyJson.target = $(this).val();
                selectedLiveCopyJson.depth = $(this).data("depth");
                selectedLiveCopyJson.deepRollout = false;

                selectedLiveCopies.push(selectedLiveCopyJson);
            });
            console.log(selectedLiveCopies);
            deferred.resolve(selectedLiveCopies);
        };

        // el.on('change', 'input', onValidate);
        el.on('click', '[data-dialog-action]', onResolve);
        el.on('coral-overlay:close', function () {
            // el.off('change', 'input', onValidate);
            el.off('click', '[data-dialog-action]', onResolve);
            deferred.reject();
        });

        el.show();
        // onValidate();

        return deferred.promise();
    }

    function onRolloutActiveCondition(name, el, config, collection, selections) {
        console.log("onRolloutActiveCondition")
        var selectedPath = selections[0].dataset.foundationCollectionItemId;
        return blueprintCheck(selectedPath);
    }

    // INIT
    $(window).adaptTo("foundation-registry").register("foundation.collection.action.action", {
        name: "etoolbox.rollout-manager.show-references-dialog",
        handler: onShowReferencesDialog
    });
    $(window).adaptTo("foundation-registry").register("foundation.collection.action.activecondition", {
        name: "etoolbox.rollout-manager.rollout-active-condition",
        handler: onRolloutActiveCondition
    });

    var COLLECT_LIVE_COPIES_COMMAND = '/content/etoolbox-rollout-manager/servlet/collect-live-copies';

    function collectLiveCopies(path) {
        var result = [];
        $.ajax({
            url: COLLECT_LIVE_COPIES_COMMAND,
            type: 'POST',
            async: false,
            data: {
                _charset_: "UTF-8",
                path: path
            },
            success: function (data) {
                result = data;
            }
        });
        return result;
    }

    var BLUEPRINT_CHECK_COMMAND = '/content/etoolbox-rollout-manager/servlet/blueprint-check';

    function blueprintCheck(path) {
        console.log("blueprintCheck")
        var isBlueprint = false;
        $.ajax({
            url: BLUEPRINT_CHECK_COMMAND,
            type: 'POST',
            async: false,
            data: {
                _charset_: "UTF-8",
                path: path
            },
            success: function (data) {
                isBlueprint = data && data.isBlueprint;
            }
        });
        return isBlueprint;
    }

    var PROCESSING_LABEL = Granite.I18n.get('Processing');
    var ROLLOUT_IN_PROGRESS_LABEL = Granite.I18n.get('Rollout in progress ...');

    function rolloutItems(selectedLiveCopies, blueprintPath, rolloutRequest) {
        var logger = ERM.createLoggerDialog(PROCESSING_LABEL, ROLLOUT_IN_PROGRESS_LABEL, blueprintPath);
        var requests = $.Deferred().resolve().then(rolloutRequest(selectedLiveCopies, logger));
        requests.always(function () {
            logger.finished();
            logger.dialog.on('coral-overlay:close', function () {
                $(window).adaptTo('foundation-ui').wait();
                window.location.reload();
            });
        });
        return requests;
    }

})(window, document, Granite.$, Granite.ERM, Granite);