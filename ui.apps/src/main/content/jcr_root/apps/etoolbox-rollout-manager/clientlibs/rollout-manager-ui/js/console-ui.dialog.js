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
    const FINISHED_LABEL = Granite.I18n.get('Rollout');

    function loggerDialogFinished(dialog, selectedPath, processingLabel) {
        dialog.closable = 'on';
        dialog.header.textContent = `${FINISHED_LABEL} ${selectedPath}`;
        processingLabel.remove();

        const closeBtn = new Coral.Button();
        closeBtn.variant = 'primary';
        closeBtn.label.textContent = CLOSE_LABEL;
        closeBtn.on('click', function () {
            dialog.hide();
        });

        dialog.footer.appendChild(closeBtn);
    }

    function insertLogItem(dialog, message, safe) {
        const logItem = document.createElement('div');
        logItem.className = 'rollout-manager-log-item';
        logItem[safe ? 'textContent' : 'innerHTML'] = message;
        dialog.content.insertAdjacentElement('beforeend', logItem);
    }

    /**
     * Creates {@return ProcessLogger} wrapper indicating that the rollout process is in progress
     * @return {ProcessLogger}
     *
     * @typedef ProcessLogger
     * @method finished
     * @method log
     */
    function createLoggerDialog(title, processingMsg, selectedPath) {
        const dialog = getBaseDialog();
        dialog.variant = 'default';
        dialog.header.textContent = title;
        dialog.header.insertBefore(new Coral.Wait(), dialog.header.firstChild);
        dialog.footer.innerHTML = '';
        dialog.content.innerHTML = '';
        dialog.classList.add(LOGGER_DIALOG_CLASS);
        dialog.closable = 'off';

        const processingLabel = document.createElement('p');
        processingLabel.textContent = processingMsg;
        dialog.content.append(processingLabel);

        document.body.appendChild(dialog);
        dialog.show();

        return {
            dialog,
            finished: function () {
                loggerDialogFinished(dialog, selectedPath, processingLabel);
            },
            log: function (message, safe) {
                insertLogItem(dialog, message, safe);
            }
        };
    }
    ns.createLoggerDialog = createLoggerDialog;

    // Rollout dialog related constants
    const CANCEL_LABEL = Granite.I18n.get('Cancel');
    const DIALOG_LABEL = Granite.I18n.get('Rollout');
    const ROLLOUT_AND_PUBLISH_LABEL = Granite.I18n.get('Rollout and Publish');
    const EXPAND_ALL = Granite.I18n.get('Expand All');
    const COLLAPSE_ALL = Granite.I18n.get('Collapse All');
    const SELECT_ALL_LABEL = Granite.I18n.get('Select All');
    const UNSELECT_ALL_LABEL = Granite.I18n.get('Unselect All');
    const TARGET_PATHS_LABEL = Granite.I18n.get('Target paths');
    const ROLLOUT_SCOPE_LABEL = Granite.I18n.get('Rollout scope');
    const INCLUDE_SUBPAGES_LABEL = Granite.I18n.get('Include subpages');
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

    function appendTargetsHeader(sourceElement) {
        const $div = $('<div>');
        $('<h3>')
            .text(TARGET_PATHS_LABEL)
            .appendTo($div);
        const $toolbar = $('<div class="rollout-manager-toolbar">');
        $(`<coral-checkbox class="rollout-manager-select-all">${SELECT_ALL_LABEL}</coral-checkbox>`).appendTo($toolbar);
        $('<button is="coral-button" variant="quiet" class="rollout-manager-expand">')
            .text(COLLAPSE_ALL)
            .appendTo($toolbar);
        $toolbar.appendTo($div);
        $div.appendTo(sourceElement);
    }

    function appendRolloutScope(sourceElement) {
        $('<h3>').text(ROLLOUT_SCOPE_LABEL).appendTo(sourceElement);
        $('<coral-checkbox name="isDeepRollout">').text(INCLUDE_SUBPAGES_LABEL).appendTo(sourceElement);
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

    function onResolve($btn, path, deferred) {
        const shouldActivate = $btn.closest('[data-dialog-action]').data('dialogAction') === 'rolloutPublish';
        const isDeepRollout = $('coral-checkbox[name="isDeepRollout"]').filter(':not([disabled])').prop('checked');
        const selectionJsonArray = [];
        $(CORAL_CHECKBOX_ITEM).each(function () {
            const $checkbox = $(this);
            if ($checkbox.prop('checked')) {
                selectionJsonArray.push(checkBoxToJsonData($checkbox));
            }
        });
        const data = {
            path,
            isDeepRollout,
            selectionJsonArray,
            shouldActivate
        };
        deferred.resolve(data);
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
     * Shows the dialog with the checkbox tree of live copy paths for the selected page path
     * @param liveCopiesJsonArray - the json array containing data related to live copies for the selected page
     * @param selectedPath - path of the selected page
     * @returns {*|jQuery}
     */
    function showRolloutDialog(liveCopiesJsonArray, selectedPath) {
        const deferred = $.Deferred();

        const dialog = initRolloutDialog(selectedPath);
        const $rolloutBtn = $('<button id="rolloutButton" data-dialog-action="rollout" is="coral-button" variant="primary" coral-close>').text(DIALOG_LABEL);
        const $submitBtn = $('<button id="rolloutAndPublishButton" data-dialog-action="rolloutPublish" is="coral-button" variant="primary" coral-close>').text(ROLLOUT_AND_PUBLISH_LABEL);
        $rolloutBtn.appendTo(dialog.footer);
        $submitBtn.appendTo(dialog.footer);

        appendTargetsHeader(dialog.content);
        const $checkboxListContainer = $('<div class="rollout-manager-nestedcheckboxlist-container">').appendTo(dialog.content);
        appendNestedCheckboxList(liveCopiesJsonArray, $checkboxListContainer);
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
