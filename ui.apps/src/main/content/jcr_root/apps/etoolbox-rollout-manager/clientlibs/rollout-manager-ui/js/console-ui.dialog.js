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
 * Contains helper functions to showing the rollout process dialogs.
 */
(function (document, $, Granite, ns) {
    'use strict';

    const LOGGER_DIALOG_CLASS = 'rollout-manager-logger-dialog';
    const BASE_DIALOG_CLASS = 'rollout-manager-dialog';

    let baseDialog;

    /** Common base Coral dialog instance getter */
    function getBaseDialog() {
        if (!baseDialog) {
            baseDialog = new Coral.Dialog().set({
                backdrop: Coral.Dialog.backdrop.STATIC,
                interaction: 'off'
            }).on('coral-overlay:close', function (e) {
                baseDialog.classList.remove(LOGGER_DIALOG_CLASS);
                e.target.remove();
            });
            baseDialog.classList.add(BASE_DIALOG_CLASS);
        }
        baseDialog.closable = 'on';
        return baseDialog;
    }

    // Logger dialog related constants
    const CLOSE_LABEL = Granite.I18n.get('Close');
    const PUBLISH_SUCCESS_MSG = Granite.I18n.get('Publishing started');
    const PUBLISH_ERROR_MSG = Granite.I18n.get('Publishing is denied.');
    const ROLLOUT_IN_PROGRESS_LABEL = Granite.I18n.get('Rollout in progress ...');
    const ROLLOUT_IS_PENDING_LABEL = Granite.I18n.get('Pending (position in queue: {X} of {Y})');

    function isLoggerDialog(dialog) {
        return dialog.classList.contains(LOGGER_DIALOG_CLASS);
    }

    function loggerDialogFinished(dialog, statusText) {
        if (!isLoggerDialog(dialog)) return;
        dialog.querySelector('.rollout-processing-status').textContent = statusText;
    }

    function updateLoggerDialogStatus(dialog, queue) {
        const processingLabel = dialog.querySelector('.rollout-processing-label');
        if (!processingLabel.textContent.trim() || processingLabel.textContent.trim() !== ROLLOUT_IN_PROGRESS_LABEL) {
            processingLabel.innerText = '';
            const labelText = queue ? ROLLOUT_IS_PENDING_LABEL.replace('{X}', queue.position).replace('{Y}', queue.total) : ROLLOUT_IN_PROGRESS_LABEL;
            processingLabel.insertAdjacentText('beforeend', labelText);
        }
    }

    function updateLog(dialog, message) {
        if (message.type !== 'rollout' && message.type !== 'activation') return;
        const itemToUpdate = $(dialog)
            .find('.rollout-log-item')
            .filter((i, item) => $.trim($(item).text()) === message.path)
            .first();
        if (!itemToUpdate.length) return;

        const { type, result } = message;
        switch (type) {
            case 'rollout':
                handleRollout(itemToUpdate, result);
                break;

            case 'activation':
                handleActivation(itemToUpdate, result);
                break;
        }
    }

    function handleRollout(item, result) {
        const $icon = item.find('coral-icon');
        if ($icon.hasClass('updated')) return;
        $icon[0].set('icon', result === 'success' ? 'checkmark' : 'close');
        $icon.addClass('updated');
    }

    function handleActivation(item, result) {
        if (item.find('.rollout-activation-status').length) return;

        const isError = result === 'error';
        const message = isError ? PUBLISH_ERROR_MSG : PUBLISH_SUCCESS_MSG;

        $('<i>')
            .addClass('rollout-activation-status')
            .toggleClass('error', isError)
            .text(message)
            .appendTo(item);
    }

    function createLogItem(message) {
        const $item = $('<li class="rollout-log-item">').text(message);
        const $icon = new Coral.Icon();
        $item.prepend($icon);
        return $item;
    }

    function createLogList(dialog, message) {
        if (message.type !== 'targets') return;
        const $logList = $('<ul class="rollout-logs-list">');
        message.items.forEach(item => createLogItem(item).appendTo($logList));
        $logList.appendTo(dialog.content);
    }

    function rolloutLog(dialog, message, queue) {
        if (!isLoggerDialog(dialog)) return;
        if (!dialog.content.querySelector('.rollout-logs-list')) createLogList(dialog, message);
        updateLoggerDialogStatus(dialog, queue);
        updateLog(dialog, message);
    }

    /**
     * Creates {@return ProcessLogger} wrapper indicating that the rollout process is in progress
     * @return {ProcessLogger}
     *
     * @typedef ProcessLogger
     * @method finished
     * @method log
     */
    function createLoggerDialog(rolloutPath) {
        const dialog = getBaseDialog();
        dialog.variant = 'default';
        if (rolloutPath) {
            dialog.header.textContent = `${DIALOG_LABEL} ${rolloutPath}`;
        }
        dialog.content.innerHTML = '';
        dialog.footer.innerHTML = '';
        const waitIcon = new Coral.Wait().set({ size: 'S' });
        const $label = $('<span class="rollout-processing-label">')
        $('<div class="rollout-processing-status">').append(waitIcon, $label).appendTo(dialog.content);
        dialog.classList.add(LOGGER_DIALOG_CLASS);
        const closeBtn = new Coral.Button();
        closeBtn.variant = 'primary';
        closeBtn.label.textContent = CLOSE_LABEL;
        closeBtn.on('click', function () {
            dialog.hide();
        });

        dialog.footer.appendChild(closeBtn);
        document.body.appendChild(dialog);
        dialog.show();
        dialog.closable = 'on';

        return {
            dialog,
            finished: function (statusText) {
                loggerDialogFinished(dialog, statusText);
            },
            log: function (message, queue) {
                rolloutLog(dialog, message, queue);
            }
        };
    }
    ns.createLoggerDialog = createLoggerDialog;

    // Rollout dialog related constants
    const CANCEL_LABEL = Granite.I18n.get('Cancel');
    const DIALOG_LABEL = Granite.I18n.get('Rollout');
    const ROLLOUT_AND_PUBLISH_LABEL = Granite.I18n.get('Rollout and Publish');
    const ROLLOUT_AND_PUBLISH_CONFIRMATION = Granite.I18n.get('Warning: Publishing action');
    const CONFIRMATION_MESSAGE = Granite.I18n.get(
        `You are about to publish page(s) after rollout.<br><br>
        Continue?`);
    const EXPAND_ALL = Granite.I18n.get('Expand All');
    const COLLAPSE_ALL = Granite.I18n.get('Collapse All');
    const SELECT_ALL_LABEL = Granite.I18n.get('Select All');
    const UNSELECT_ALL_LABEL = Granite.I18n.get('Unselect All');
    const ROLLOUT_SCOPE_LABEL = Granite.I18n.get('Rollout scope');
    const INCLUDE_SUBPAGES_LABEL = Granite.I18n.get('Include subpages');
    const NO_MATCHES_LABEL = Granite.I18n.get('No matches found');
    const SEARCH_TARGET_LABEL = Granite.I18n.get('Filter targets...');

    const CORAL_CHECKBOX_ITEM = 'coral-checkbox[name="liveCopyProperties[]"]';
    const CHECKBOX_SELECT_ALL = '.rollout-manager-select-all';
    const MASTER_DATA_ATTR = 'master';
    const DEPTH_DATA_ATTR = 'depth';
    const AUTO_ROLLOUT_DATA_ATTR = 'auto-rollout';

    function initRolloutDialog(path) {
        const dialog = getBaseDialog();
        dialog.variant = 'notice';
        dialog.header.textContent = `${DIALOG_LABEL} ${path}`;
        dialog.footer.innerHTML = '';
        dialog.content.innerHTML = '';
        $('<button is="coral-button" variant="default" coral-close>')
            .text(CANCEL_LABEL)
            .appendTo(dialog.footer);
        return dialog;
    }

    function appendTargetsHeader(sourceElement, hasNestedItems, onSearchInput) {
        const $div = $('<div>');

        if (onSearchInput) {
            $('<coral-search class="rollout-manager-search"></coral-search>')
                .attr('placeholder', SEARCH_TARGET_LABEL)
                .on('coral-search:input', function (e) {
                    onSearchInput(e.target.value);
                })
                .on('coral-search:clear', () => onSearchInput(''))
                .appendTo($div);
        }

        const $toolbar = $('<div class="rollout-manager-toolbar">');
        $(`<coral-checkbox class="rollout-manager-select-all">${SELECT_ALL_LABEL}</coral-checkbox>`).appendTo($toolbar);
        if (hasNestedItems) {
            $('<button is="coral-button" variant="quiet" class="rollout-manager-expand">')
                .text(COLLAPSE_ALL)
                .appendTo($toolbar);
        }
        $toolbar.appendTo($div);

        $div.appendTo(sourceElement);
    }

    function appendRolloutScope(sourceElement) {
        $('<h3>').text(ROLLOUT_SCOPE_LABEL).appendTo(sourceElement);
        $('<coral-checkbox name="isDeepRollout" class="rollout-manager-scope">').text(INCLUDE_SUBPAGES_LABEL).appendTo(sourceElement);
    }

    function initNestedAccordion(currentCheckbox, liveCopiesJsonArray) {
        const $accordion = $('<coral-accordion variant="quiet">');
        const $accordionItem = $('<coral-accordion-item selected>');
        const $accordionItemLabel = $('<coral-accordion-item-label>');

        currentCheckbox.appendTo($accordionItemLabel);
        $accordionItemLabel.appendTo($accordionItem);

        const $accordionItemContent = $('<coral-accordion-item-content class="rollout-manager-coral-accordion-item-content">');
        appendNestedCheckboxList(liveCopiesJsonArray, $accordionItemContent);
        $accordionItemContent.appendTo($accordionItem);

        $accordionItem.appendTo($accordion);
        return $accordion;
    }

    function jsonToCheckboxListItem(liveCopyJson) {
        const $liItem = $('<li class="rollout-manager-nestedcheckboxlist-item">');
        const liveCopyCheckbox =
            $(`<coral-checkbox
                  coral-interactive
                  name="liveCopyProperties[]"
                  data-master="${liveCopyJson.master}"
                  data-depth="${liveCopyJson.depth}"
                  data-auto-rollout="${liveCopyJson.autoRolloutTrigger}"
                  value="${liveCopyJson.path}">`
            ).text(liveCopyJson.path).attr('disabled', !!liveCopyJson.disabled);
        const lastRolledOutTimeAgo =
            $(`<i
                title="${ns.TimeUtil.displayLastRolledOut(liveCopyJson.lastRolledOut)}"
                class="rollout-manager-last-rollout-date">`
            ).text(ns.TimeUtil.timeSince(liveCopyJson.lastRolledOut));
        liveCopyCheckbox.append(lastRolledOutTimeAgo);
        if (liveCopyJson.liveCopies && liveCopyJson.liveCopies.length > 0) {
            initNestedAccordion(liveCopyCheckbox, liveCopyJson.liveCopies).appendTo($liItem);
        } else {
            liveCopyCheckbox.addClass('inner-checkbox-option').appendTo($liItem);
        }
        return $liItem;
    }

    function appendNestedCheckboxList(liveCopiesJsonArray, sourceElement) {
        if (!liveCopiesJsonArray.length) return;
        const $nestedList = $('<ul class="rollout-manager-nestedcheckboxlist" data-rm-nested-checkbox-list>');
        liveCopiesJsonArray.forEach(liveCopyJson => {
            jsonToCheckboxListItem(liveCopyJson).appendTo($nestedList);
        });
        $nestedList.appendTo(sourceElement);
    }

    function checkBoxToJsonData(checkbox) {
        return {
            master: checkbox.data(MASTER_DATA_ATTR),
            target: checkbox.val(),
            depth: checkbox.data(DEPTH_DATA_ATTR),
            autoRolloutTrigger: checkbox.data(AUTO_ROLLOUT_DATA_ATTR)
        };
    }

    function hasSelection() {
        return $(CORAL_CHECKBOX_ITEM + '[checked]').length > 0;
    }

    function validateDialog(actionBtns) {
        actionBtns.attr('disabled', !hasSelection());
    }

    function isAllSelected() {
        const $enabled = $(CORAL_CHECKBOX_ITEM).filter(':not([disabled])');
        return $enabled.length > 0 && $enabled.length === $enabled.filter('[checked]').length;
    }

    function toggleSelectAll(checked) {
        $(CORAL_CHECKBOX_ITEM).filter(':not([disabled])').prop('checked', !checked);
    }

    function onTreeChange(actionBtns) {
        const $selectAll = $(CHECKBOX_SELECT_ALL);
        const $selectAllLabel = $selectAll.find('label');
        const allSelected = isAllSelected();
        const hasAnySelection = hasSelection();

        $(CHECKBOX_SELECT_ALL).prop({
            checked: allSelected,
            indeterminate: hasAnySelection && !allSelected
        });

        $selectAllLabel.text(allSelected ? UNSELECT_ALL_LABEL : SELECT_ALL_LABEL);
        validateDialog(actionBtns);
    }

    function onSelectAllClick(actionBtns) {
        const $selectAll = $(CHECKBOX_SELECT_ALL);
        const isAllSelected = $selectAll.prop('checked');
        toggleSelectAll(isAllSelected);
        onTreeChange(actionBtns);
    }

    function onExpandButtonClick() {
        const $expandBtn = $('.rollout-manager-expand');
        const isExpand = $expandBtn.text() === EXPAND_ALL;
        $('coral-accordion-item').prop('selected', isExpand);
        $expandBtn.text(isExpand ? COLLAPSE_ALL : EXPAND_ALL);
    }

    /**
     * Displays a confirmation dialog for rollout and publish action.
     * Calls onConfirm callback if the user confirms.
     */
    function showConfirmRolloutPublishDialog(onConfirm) {
        const dialog = new Coral.Dialog().set({
            variant: 'error',
            header: {
                textContent: ROLLOUT_AND_PUBLISH_CONFIRMATION
            },
            content: {
                innerHTML: CONFIRMATION_MESSAGE
            }
        });

        $('<button is="coral-button" variant="default" coral-close>')
            .text(CANCEL_LABEL)
            .appendTo(dialog.footer);

        $('<button is="coral-button" variant="primary" coral-close>')
            .text(ROLLOUT_AND_PUBLISH_LABEL)
            .appendTo(dialog.footer)
            .on('click', () => onConfirm());

        document.body.appendChild(dialog);
        dialog.show();
    }

    function getSelectionJsonArray() {
        return $(CORAL_CHECKBOX_ITEM + '[checked]').map(function () {
            return checkBoxToJsonData($(this));
        }).get();
    }

    function onResolve($btn, path, deferred) {
        const action = $btn.data('dialogAction');
        const isDeepRollout = $('coral-checkbox[name="isDeepRollout"]:not([disabled])').prop('checked');
        const resolveData = (shouldActivate) => {
            deferred.resolve({
                path,
                isDeepRollout,
                selectionJsonArray: getSelectionJsonArray(),
                shouldActivate
            });
        };

        if (action === 'rolloutPublish') {
            showConfirmRolloutPublishDialog(() => resolveData(true));
        } else {
            resolveData(false);
        }
    }

    function initEventHandlers(dialog, deferred, onTreeChange, onSelectAllClick, onResolve) {
        dialog.on('treechange.rm-dialog', onTreeChange);
        dialog.on('click.rm-dialog', CHECKBOX_SELECT_ALL, onSelectAllClick);
        dialog.on('click.rm-dialog', '.rollout-manager-expand', onExpandButtonClick);
        dialog.on('click.rm-dialog', '[data-dialog-action]', onResolve);
        dialog.one('coral-overlay:close', function () {
            dialog.off('.rm-dialog');
            deferred.reject();
        });
    }

    /**
     * Filters the live copies tree based on the search term
     * @param liveCopies - the live copies tree to filter
     * @param searchTerm - the search term to filter by
     * @returns {Array} - the filtered live copies tree
     */
    function filterLiveCopiesTree(liveCopies, searchTerm) {
        if (!searchTerm) return liveCopies;
        const term = searchTerm.toLowerCase();

        const filterNode = ({ path, liveCopies: children = [], ...rest }) => {
            const pathMatch = path && path.toLowerCase().includes(term);
            const filteredChildren = (children.length > 0) ? children.map(filterNode).filter(Boolean) : [];
            if (pathMatch || filteredChildren.length > 0) {
                return { path, liveCopies: filteredChildren, ...rest };
            }
            return null;
        };

        return liveCopies.map(filterNode).filter(Boolean);
    }

    /**
     * Shows the dialog with the checkbox tree of live copy paths for the selected page path
     * @param liveCopiesJsonArray - the json array containing data related to live copies for the selected page
     * @param selectedPath - path of the selected page
     * @returns {*|jQuery}
     */
    function showRolloutDialog(liveCopiesJsonArray, selectedPath) {
        const deferred = $.Deferred();

        const dialog = initRolloutDialog(selectedPath);
        const $rolloutBtn = $('<button id="rolloutButton" data-dialog-action="rollout" is="coral-button" variant="primary" coral-close>').text(DIALOG_LABEL);
        const $submitBtn = $('<button id="rolloutAndPublishButton" data-dialog-action="rolloutPublish" is="coral-button" variant="primary">').text(ROLLOUT_AND_PUBLISH_LABEL);
        $rolloutBtn.appendTo(dialog.footer);
        $submitBtn.appendTo(dialog.footer);

        let currentFilter = '';
        let filteredLiveCopies = liveCopiesJsonArray;
        const hasNestedItems = liveCopiesJsonArray.some(item => item.liveCopies && item.liveCopies.length > 0);

        // Render tree with filter
        function renderTree() {
            const $checkboxListContainer = $(dialog.content).find('.rollout-manager-nestedcheckboxlist-container');
            $checkboxListContainer.empty();
            if (!filteredLiveCopies.length) {
                $('<div class="rollout-manager-no-matches">').text(NO_MATCHES_LABEL).appendTo($checkboxListContainer);
            } else {
                appendNestedCheckboxList(filteredLiveCopies, $checkboxListContainer);
            }
        }

        function handleSearchInput(searchTerm) {
            currentFilter = searchTerm;
            filteredLiveCopies = filterLiveCopiesTree(liveCopiesJsonArray, currentFilter);
            renderTree();
            onTreeChange($actionBtns);
        }

        appendTargetsHeader(dialog.content, hasNestedItems, handleSearchInput);
        const $checkboxListContainer = $('<div class="rollout-manager-nestedcheckboxlist-container">').appendTo(dialog.content);
        appendNestedCheckboxList(filteredLiveCopies, $checkboxListContainer);
        appendRolloutScope(dialog.content);

        const $actionBtns = $submitBtn.add($rolloutBtn);

        initEventHandlers(
            $(dialog),
            deferred,
            () => onTreeChange($actionBtns),
            () => onSelectAllClick($actionBtns),
            (e) => onResolve($(e.target), selectedPath, deferred)
        );

        dialog.show();
        validateDialog($actionBtns);

        return deferred.promise();
    }
    ns.showRolloutDialog = showRolloutDialog;
})(document, Granite.$, Granite, (window.ERM = (window.ERM || {})));
