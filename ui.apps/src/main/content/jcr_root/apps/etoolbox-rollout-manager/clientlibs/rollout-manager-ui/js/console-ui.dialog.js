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
(function (window, document, $, Granite) {
    'use strict';

    const Utils = Granite.ERM = (Granite.ERM || {});

    let baseDialog;

    /** Common base Coral dialog instance getter */
    function getBaseDialog() {
        if (!baseDialog) {
            baseDialog = new Coral.Dialog().set({
                backdrop: Coral.Dialog.backdrop.MODAL,
                interaction: 'off',
                closable: 'on'
            }).on('coral-overlay:close', function (e) {
                e.target.remove();
            });
            baseDialog.classList.add('rollout-manager-dialog');
        }
        return baseDialog;
    }

    // Logger dialog related constants
    const CLOSE_LABEL = Granite.I18n.get('Close');
    const FINISHED_LABEL = Granite.I18n.get('Rollout');

    function loggerDialogFinished(dialog, selectedPath, processingLabel) {
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

        const processingLabel = document.createElement('p');
        processingLabel.textContent = processingMsg;
        dialog.content.append(processingLabel);

        document.body.appendChild(dialog);
        dialog.show();

        return {
            dialog: dialog,
            finished: function () {
                loggerDialogFinished(dialog, selectedPath, processingLabel);
            },
            log: function (message, safe) {
                insertLogItem(dialog, message, safe);
            }
        };
    }
    Utils.createLoggerDialog = createLoggerDialog;

    // Rollout dialog related constants
    const CANCEL_LABEL = Granite.I18n.get('Cancel');
    const DIALOG_LABEL = Granite.I18n.get('Rollout');
    const SELECT_ALL_LABEL = Granite.I18n.get('Select all');
    const UNSELECT_ALL_LABEL = Granite.I18n.get('Unselect all');
    const TARGET_PATHS_LABEL = Granite.I18n.get('Target paths');
    const ROLLOUT_SCOPE_LABEL = Granite.I18n.get('Rollout scope');
    const INCLUDE_SUBPAGES_LABEL = Granite.I18n.get('Include subpages');
    const NEW_LABEL = Granite.I18n.get('new');
    const CORAL_CHECKBOX_ITEM = 'coral-checkbox[name="liveCopyProperties[]"]';
    const MASTER_DATA_ATTR = 'master';
    const DEPTH_DATA_ATTR = 'depth';

    function initRolloutDialog(path) {
        const dialog = getBaseDialog();
        dialog.variant = 'notice';
        dialog.header.textContent = `${DIALOG_LABEL} ${path}`;
        dialog.footer.innerHTML = ''; // Clean content
        dialog.content.innerHTML = ''; // Clean content
        const $cancelBtn = $('<button is="coral-button" variant="default" coral-close>')
            .text(CANCEL_LABEL);
        $cancelBtn.appendTo(dialog.footer);

        return dialog;
    }

    function appendTargetsHeader(sourceElement) {
        const span = $('<span>');

        const selectAll = $('<a is="coral-anchorbutton" variant="quiet" class="rollout-manager-select-all">')
            .text(SELECT_ALL_LABEL);
        selectAll.appendTo(span);

        const label = $('<h3 class="rollout-manager-targets-label">')
            .text(TARGET_PATHS_LABEL);
        label.appendTo(span);

        span.appendTo(sourceElement);
    }

    function appendRolloutScope(sourceElement) {
        const label = $('<h3>')
            .text(ROLLOUT_SCOPE_LABEL);
        const isDeepCheckbox = $('<coral-checkbox name="isDeepRollout">')
            .text(INCLUDE_SUBPAGES_LABEL);
        label.appendTo(sourceElement);
        isDeepCheckbox.appendTo(sourceElement);
    }

    function initNestedAccordion(currentCheckbox, liveCopiesJsonArray) {
        const accordion = $('<coral-accordion variant="quiet">');
        const accordionItem = $('<coral-accordion-item>');
        const accordionItemLabel = $('<coral-accordion-item-label>');

        currentCheckbox.appendTo(accordionItemLabel);
        accordionItemLabel.appendTo(accordionItem);

        const accordionItemContent = $('<coral-accordion-item-content class="rollout-manager-coral-accordion-item-content">');
        appendNestedCheckboxList(liveCopiesJsonArray, accordionItemContent);
        accordionItemContent.appendTo(accordionItem);

        accordionItem.appendTo(accordion);

        return accordion;
    }

    function jsonToCheckboxListItem(liveCopyJson) {
        const liItem = $('<li class="rollout-manager-nestedcheckboxlist-item">');
        const liveCopyCheckbox =
            $(`<coral-checkbox
                  coral-interactive
                  name="liveCopyProperties[]"
                  data-master="${liveCopyJson.master}"
                  data-depth="${liveCopyJson.depth}"
                  value="${liveCopyJson.path}">`
            ).text(liveCopyJson.path);
        if (liveCopyJson.isNew) {
            const newLabel = $('<i class="rollout-manager-new-label">')
                .text(` ${NEW_LABEL}`);
            liveCopyCheckbox.append(newLabel);
        }
        if (liveCopyJson.liveCopies && liveCopyJson.liveCopies.length > 0) {
            const accordion = initNestedAccordion(liveCopyCheckbox, liveCopyJson.liveCopies);
            accordion.appendTo(liItem);
        } else {
            liveCopyCheckbox.addClass('inner-checkbox-option');
            liveCopyCheckbox.appendTo(liItem);
        }
        return liItem;
    }

    function appendNestedCheckboxList(liveCopiesJsonArray, sourceElement) {
        if (liveCopiesJsonArray.length > 0) {
            const nestedList = $('<ul class="rollout-manager-nestedcheckboxlist" data-rollout-manager-nestedcheckboxlist-disconnected="false">');
            liveCopiesJsonArray.forEach((liveCopyJson) => {
                const liItem = jsonToCheckboxListItem(liveCopyJson);
                liItem.appendTo(nestedList);
            });
            nestedList.appendTo(sourceElement);
        }
    }

    function checkBoxToJsonData(checkbox) {
        return {
            master: checkbox.data(MASTER_DATA_ATTR),
            target: checkbox.val(),
            depth: checkbox.data(DEPTH_DATA_ATTR)
        };
    }

    function changeSelectAllLabel(hasSelection) {
        const selectAllEl = $('.rollout-manager-select-all');
        selectAllEl.text(hasSelection ? UNSELECT_ALL_LABEL : SELECT_ALL_LABEL);
    }

    function hasSelection() {
        return $(CORAL_CHECKBOX_ITEM + '[checked]').length > 0;
    }

    function selectUnselectAll() {
        $(CORAL_CHECKBOX_ITEM).prop('checked', !hasSelection());
    }

    function validateSelection(hasSelection, submitBtn) {
        submitBtn.attr('disabled', !hasSelection);
    }

    function onCheckboxChange(submitBtn) {
        const hasAnySelection = hasSelection();
        changeSelectAllLabel(hasAnySelection);
        validateSelection(hasAnySelection, submitBtn);
    }

    function onSelectAllClick(submitBtn) {
        selectUnselectAll();
        onCheckboxChange(submitBtn);
    }

    function onResolve(path, deferred) {
        const isDeepRollout = $('coral-checkbox[name="isDeepRollout"]').prop('checked');
        const selectionJsonArray = [];
        $(CORAL_CHECKBOX_ITEM).each(function () {
            const checkbox = $(this);
            if (checkbox.prop('checked')) {
                selectionJsonArray.push(checkBoxToJsonData(checkbox));
            }
        });
        const data = {
            path,
            isDeepRollout,
            selectionJsonArray
        };
        deferred.resolve(data);
    }

    function initEventHandlers(dialog, deferred, onCheckboxChange, onSelectAllClick, onResolve) {
        dialog.on('change', 'coral-checkbox', onCheckboxChange);
        dialog.on('click', '.rollout-manager-select-all', onSelectAllClick);
        dialog.on('click', '[data-dialog-action]', onResolve);
        dialog.on('coral-overlay:close', function () {
            dialog.off('change', 'coral-checkbox', onCheckboxChange);
            dialog.off('click', '.rollout-manager-select-all', onSelectAllClick);
            dialog.off('click', '[data-dialog-action]', onResolve);
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
        const $submitBtn = $('<button data-dialog-action is="coral-button" variant="primary" coral-close>')
            .text(DIALOG_LABEL);
        $submitBtn.appendTo(dialog.footer);

        appendTargetsHeader(dialog.content);
        appendNestedCheckboxList(liveCopiesJsonArray, dialog.content);
        appendRolloutScope(dialog.content);

        initEventHandlers(
            dialog,
            deferred,
            () => onCheckboxChange($submitBtn),
            () => onSelectAllClick($submitBtn),
            () => onResolve(selectedPath, deferred)
        );

        dialog.show().center();
        validateSelection(hasSelection(), $submitBtn);

        return deferred.promise();
    }
    Utils.showRolloutDialog = showRolloutDialog;
})(window, document, Granite.$, Granite);
