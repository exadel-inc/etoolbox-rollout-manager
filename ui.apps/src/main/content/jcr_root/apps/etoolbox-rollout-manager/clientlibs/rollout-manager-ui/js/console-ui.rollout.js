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
            rolloutItems(data, buildRolloutRequest);
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
                    liveCopiesArray: JSON.stringify(data.liveCopiesArray),
                    isDeepRollout: data.isDeepRollout
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

    function appendTargetsHeader(sourceElement) {
        // var span = $('<span>');
        var label = $('<h3 class="rollout-manager-targets-label">').text('Target paths');
        // var infoIcon = $('<coral-icon id="rollout-manager-targets-info-icon" icon="info" size="XS">');
        // var infoTooltip = $('<coral-tooltip placement="right" target="_prev" interaction="on" open>')
        //     .text('Live copies will be synchronized or created (if possible) for selected paths');
        // label.appendTo(span);
        // infoIcon.appendTo(span);
        // infoTooltip.appendTo(span);
        // span.appendTo(sourceElement);
        label.appendTo(sourceElement);
    }

    function appendRolloutScope(sourceElement) {
        var label = $('<h3>').text('Rollout scope');
        var isDeepCheckbox = $('<coral-checkbox name="isDeepRollout">').text('Rollout page and all sub pages');
        label.appendTo(sourceElement);
        isDeepCheckbox.appendTo(sourceElement);
    }

    function appendNestedCheckboxList(liveCopiesJsonArray, sourceElement) {
        if (liveCopiesJsonArray.length > 0) {
            var nestedList = $('<ul class="rollout-manager-nestedcheckboxlist" data-rollout-manager-nestedcheckboxlist-disconnected="false">');

            for (var i = 0; i < liveCopiesJsonArray.length; i++) {
                var liveCopyJson = liveCopiesJsonArray[i];

                var liItem = $('<li class="rollout-manager-nestedcheckboxlist-item">');
                var liveCopyCheckbox =
                    $('<coral-checkbox coral-interactive name="liveCopyProperties[]" data-master="' + liveCopyJson.master + '" data-depth="' + liveCopyJson.depth + '" value="' + liveCopyJson.path + '">')
                        .text(liveCopyJson.path);

                if (liveCopyJson.liveCopies && liveCopyJson.liveCopies.length > 0) {
                    var accordion = $('<coral-accordion variant="quiet">');

                    var accordionItem = $('<coral-accordion-item>');

                    var accordionItemLabel = $('<coral-accordion-item-label>');

                    liveCopyCheckbox.appendTo(accordionItemLabel);
                    accordionItemLabel.appendTo(accordionItem);

                    var accordionItemContent = $('<coral-accordion-item-content class="rollout-manager-coral-accordion-item-content">');
                    appendNestedCheckboxList(liveCopyJson.liveCopies, accordionItemContent);
                    accordionItemContent.appendTo(accordionItem);

                    accordionItem.appendTo(accordion);

                    accordion.appendTo(liItem);
                } else {
                    liveCopyCheckbox.addClass("inner-checkbox-option");
                    liveCopyCheckbox.appendTo(liItem);
                }

                liItem.appendTo(nestedList);
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

        appendTargetsHeader(el.content);

        appendNestedCheckboxList(liveCopiesJsonArray, el.content);

        appendRolloutScope(el.content);

        // function onValidate() {
        // }

        var onResolve = function () {
            var isDeepRollout = $("coral-checkbox[name='isDeepRollout']").prop("checked");
            console.log("isDeep: " + isDeepRollout);

            var selectedLiveCopies = [];
            $("coral-checkbox[name='liveCopyProperties[]']").each(function () {
                if ($(this).prop("checked")) {
                    var selectedLiveCopyJson = {};
                    selectedLiveCopyJson.master = $(this).data("master");
                    selectedLiveCopyJson.target = $(this).val();
                    selectedLiveCopyJson.depth = $(this).data("depth");
                    selectedLiveCopyJson.deepRollout = isDeepRollout;
                    selectedLiveCopies.push(selectedLiveCopyJson);
                }
            });
            var data = {
                path: path,
                isDeepRollout: isDeepRollout,
                liveCopiesArray: selectedLiveCopies
            }
            deferred.resolve(data);
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

    function rolloutItems(data, rolloutRequest) {
        var logger = ERM.createLoggerDialog(PROCESSING_LABEL, ROLLOUT_IN_PROGRESS_LABEL, data.path);
        var requests = $.Deferred().resolve().then(rolloutRequest(data, logger));
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