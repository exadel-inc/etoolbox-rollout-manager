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
        let selectedPath = selections[0].dataset.foundationCollectionItemId;
        let foundationUi = $(window).adaptTo('foundation-ui');
        foundationUi.wait();
        collectLiveCopies(selectedPath).then((liveCopiesJsonArray) => {
                foundationUi.clearWait();
                showDialog(liveCopiesJsonArray, selectedPath).then(function (data) {
                    rolloutItems(data, buildRolloutRequest);
                })
            }
        );
    }

    const ROLLOUT_COMMAND = '/content/etoolbox-rollout-manager/servlet/rollout';
    const PROCESSING_ERROR_MSG = 'Rollout failed';

    function buildRolloutRequest(data, logger) {
        return function () {
            return $.ajax({
                url: ROLLOUT_COMMAND,
                type: "POST",
                data: {
                    selectionJsonArray: JSON.stringify(data.selectionJsonArray),
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

    const TARGET_PATHS_LABEL = 'Target paths';

    function appendTargetsHeader(sourceElement) {
        let span = $('<span>');

        let selectAll = $('<a is="coral-anchorbutton" variant="quiet" class="rollout-manager-select-all">')
            .text(SELECT_ALL_LABEL);
        selectAll.appendTo(span);

        let label = $('<h3 class="rollout-manager-targets-label">').text(TARGET_PATHS_LABEL);
        label.appendTo(span);

        span.appendTo(sourceElement);
    }

    function appendRolloutScope(sourceElement) {
        let label = $('<h3>').text('Rollout scope');
        let isDeepCheckbox = $('<coral-checkbox name="isDeepRollout">').text('Include subpages');
        label.appendTo(sourceElement);
        isDeepCheckbox.appendTo(sourceElement);
    }

    function appendNestedCheckboxList(liveCopiesJsonArray, sourceElement) {
        if (liveCopiesJsonArray.length > 0) {
            let nestedList = $('<ul class="rollout-manager-nestedcheckboxlist" data-rollout-manager-nestedcheckboxlist-disconnected="false">');

            for (let i = 0; i < liveCopiesJsonArray.length; i++) {
                let liveCopyJson = liveCopiesJsonArray[i];

                let liItem = $('<li class="rollout-manager-nestedcheckboxlist-item">');
                let liveCopyCheckbox =
                    $('<coral-checkbox coral-interactive name="liveCopyProperties[]" data-master="' + liveCopyJson.master + '" data-depth="' + liveCopyJson.depth + '" value="' + liveCopyJson.path + '">')
                        .text(liveCopyJson.path);

                if (liveCopyJson.isNew) {
                    let newLabel = $('<i class="rollout-manager-new-label">').text(" new");
                    liveCopyCheckbox.append(newLabel);
                }

                if (liveCopyJson.liveCopies && liveCopyJson.liveCopies.length > 0) {
                    let accordion = $('<coral-accordion variant="quiet">');

                    let accordionItem = $('<coral-accordion-item>');

                    let accordionItemLabel = $('<coral-accordion-item-label>');

                    liveCopyCheckbox.appendTo(accordionItemLabel);
                    accordionItemLabel.appendTo(accordionItem);

                    let accordionItemContent = $('<coral-accordion-item-content class="rollout-manager-coral-accordion-item-content">');
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

    const CANCEL_LABEL = Granite.I18n.get('Cancel');
    const DIALOG_LABEL = Granite.I18n.get('Rollout');
    const SELECT_ALL_LABEL = Granite.I18n.get('Select all');
    const UNSELECT_ALL_LABEL = Granite.I18n.get('Unselect all');

    function showDialog(liveCopiesJsonArray, path) {
        let deferred = $.Deferred();

        let el = ERM.getSharableDlg();
        el.variant = 'notice';
        el.header.textContent = DIALOG_LABEL + " " + path;
        el.footer.innerHTML = ''; // Clean content
        el.content.innerHTML = ''; // Clean content

        let $cancelBtn = $('<button is="coral-button" variant="default" coral-close>').text(CANCEL_LABEL);
        let $updateBtn = $('<button data-dialog-action is="coral-button" variant="primary" coral-close>').text(DIALOG_LABEL);
        $cancelBtn.appendTo(el.footer);
        $updateBtn.appendTo(el.footer);

        appendTargetsHeader(el.content);

        appendNestedCheckboxList(liveCopiesJsonArray, el.content);

        appendRolloutScope(el.content);

        function onCheckboxChange() {
            let hasSelection = $("coral-checkbox[name='liveCopyProperties[]'][checked]").length > 0;
            changeSelectAllLabel(hasSelection);
            validateSelection(hasSelection);
        }

        function validateSelection(hasSelection) {
            $updateBtn.attr('disabled', !hasSelection);
        }

        function onSelectAllClick() {
            selectUnselectAll();
            onCheckboxChange();
        }

        let onResolve = function () {
            let isDeepRollout = $("coral-checkbox[name='isDeepRollout']").prop("checked");
            let selectedLiveCopies = [];
            $("coral-checkbox[name='liveCopyProperties[]']").each(function () {
                if ($(this).prop("checked")) {
                    let selectedLiveCopyJson = {};
                    selectedLiveCopyJson.master = $(this).data("master");
                    selectedLiveCopyJson.target = $(this).val();
                    selectedLiveCopyJson.depth = $(this).data("depth");
                    selectedLiveCopies.push(selectedLiveCopyJson);
                }
            });
            let data = {
                path: path,
                isDeepRollout: isDeepRollout,
                selectionJsonArray: selectedLiveCopies
            }
            deferred.resolve(data);
        };

        el.on('change', 'coral-checkbox', onCheckboxChange);
        el.on('click', '.rollout-manager-select-all', onSelectAllClick);
        el.on('click', '[data-dialog-action]', onResolve);
        el.on('coral-overlay:close', function () {
            el.off('change', 'coral-checkbox', onCheckboxChange);
            el.off('click', '.rollout-manager-select-all', onSelectAllClick);
            el.off('click', '[data-dialog-action]', onResolve);
            deferred.reject();
        });

        el.show().center();

        onCheckboxChange();

        return deferred.promise();
    }

    function changeSelectAllLabel(hasSelection) {
        let selectAllEl = $('.rollout-manager-select-all');
        if (hasSelection) {
            selectAllEl.text(UNSELECT_ALL_LABEL);
        } else {
            selectAllEl.text(SELECT_ALL_LABEL);
        }
    }

    function selectUnselectAll() {
        let hasSelection = $("coral-checkbox[name='liveCopyProperties[]'][checked]").length > 0;
        if (hasSelection) {
            $("coral-checkbox[name='liveCopyProperties[]']").prop('checked', false);
        } else {
            $("coral-checkbox[name='liveCopyProperties[]']").prop('checked', true);
        }
    }

    function onRolloutActiveCondition(name, el, config, collection, selections) {
        let selectedPath = selections[0].dataset.foundationCollectionItemId;
        return isAvailableForRollout(selectedPath);
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

    const COLLECT_LIVE_COPIES_COMMAND = '/content/etoolbox-rollout-manager/servlet/collect-live-copies';

    function collectLiveCopies(path) {
        return $.ajax({
            url: COLLECT_LIVE_COPIES_COMMAND,
            type: 'POST',
            data: {
                _charset_: "UTF-8",
                path: path
            }
        });
    }

    const BLUEPRINT_CHECK_COMMAND = '/content/etoolbox-rollout-manager/servlet/blueprint-check';

    function isAvailableForRollout(path) {
        let isAvailableForRollout = false;
        $.ajax({
            url: BLUEPRINT_CHECK_COMMAND,
            type: 'POST',
            async: false,
            data: {
                _charset_: "UTF-8",
                path: path
            }
        }).done(function (data) {
            isAvailableForRollout = data && data.isAvailableForRollout;
        });
        return isAvailableForRollout;
    }

    const PROCESSING_LABEL = Granite.I18n.get('Processing');
    const ROLLOUT_IN_PROGRESS_LABEL = Granite.I18n.get('Rollout in progress ...');

    function rolloutItems(data, rolloutRequest) {
        let logger = ERM.createLoggerDialog(PROCESSING_LABEL, ROLLOUT_IN_PROGRESS_LABEL, data.path);
        let requests = $.Deferred().resolve().then(rolloutRequest(data, logger));
        requests.always(function () {
            logger.finished();
        });
        return requests;
    }

})(window, document, Granite.$, Granite.ERM, Granite);