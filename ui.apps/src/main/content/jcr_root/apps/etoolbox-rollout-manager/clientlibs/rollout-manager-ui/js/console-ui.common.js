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
 * Common utilities
 */
(function (window, document, $, Granite) {
    'use strict';

    var Utils = Granite.ERM = (Granite.ERM || {});

    /**
     * @param {string} text - text to format
     * @param {object} dictionary - dictionary object to replace '{{key}}' injections
     * @return {string}
     */
    function format(text, dictionary) {
        return text.replace(/{{(\w+)}}/g, function (match, term) {
            if (term in dictionary) return String(dictionary[term]);
            return match;
        });
    }
    Utils.format = format;

    let sharableDialog;
    /** Common sharable dialog instance getter */
    function getDialog() {
        if (!sharableDialog) {
            sharableDialog = new Coral.Dialog().set({
                backdrop: Coral.Dialog.backdrop.STATIC,
                interaction: 'off',
                closable: 'on'
            }).on('coral-overlay:close', function (e) {
                e.target.remove();
            });
            sharableDialog.classList.add('rollout-manager-dialog');
        }
        return sharableDialog;
    }
    Utils.getSharableDlg = getDialog;

    var CLOSE_LABEL = Granite.I18n.get('Close');
    var FINISHED_LABEL = Granite.I18n.get('Rollout');

    /**
     * Create {@return ProcessLogger} wrapper
     * @return {ProcessLogger}
     *
     * @typedef ProcessLogger
     * @method finished
     * @method log
     */
    function createLoggerDialog(title, processingMsg, selectedPath) {
        var el = getDialog();
        el.variant = 'default';
        el.header.textContent = title;
        el.header.insertBefore(new Coral.Wait(), el.header.firstChild);
        el.footer.innerHTML = '';
        el.content.innerHTML = '';

        var processingLabel = document.createElement('p');
        processingLabel.textContent = processingMsg;
        el.content.append(processingLabel);

        document.body.appendChild(el);
        el.show();

        return {
            dialog: el,
            finished: function () {
                el.header.textContent = FINISHED_LABEL + ' ' + selectedPath;
                processingLabel.remove();

                var closeBtn = new Coral.Button();
                closeBtn.variant = 'primary';
                closeBtn.label.textContent = CLOSE_LABEL;
                closeBtn.on('click', function () {
                    el.hide();
                });

                el.footer.appendChild(closeBtn);
            },
            log: function (message, safe) {
                var logItem = document.createElement('div');
                logItem.className = 'rollout-manager-log-item';
                logItem[safe ? 'textContent' : 'innerHTML'] = message;
                el.content.insertAdjacentElement('beforeend', logItem);
            }
        };
    }
    Utils.createLoggerDialog = createLoggerDialog;

})(window, document, Granite.$, Granite);