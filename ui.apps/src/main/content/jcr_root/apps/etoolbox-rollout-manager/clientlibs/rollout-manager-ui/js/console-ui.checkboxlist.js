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
 * Checkbox tree actions definition.
 *
 * Each checkbox has states described below.
 * - 3 states for a checkbox if contains children: check itself + check children > check itself + uncheck children > uncheck itself
 * - 2 states for a checkbox if no children: check > uncheck
 *
 * In addition, checking the current checkbox also checks all parent checkboxes, since it shouldn't be possible
 * to rollout child path w/o rolling out parent target paths
 */

(function (document, $) {
    'use strict';

    const INTERMEDIATE_ATTR = 'intermediate';
    const CORAL_CHECKBOX_ITEM = 'coral-checkbox[name="liveCopyProperties[]"]';

    function parentIntermediateStateOn(parentCheckbox) {
        parentCheckbox.attr(INTERMEDIATE_ATTR, true);
        parentCheckbox.prop('checked', true);
    }

    function parentIntermediateStateOff(parentCheckbox) {
        const childCheckboxesChecked = parentCheckbox.closest('li')
            .find('coral-accordion-item-content')
            .find(CORAL_CHECKBOX_ITEM + '[checked]');
        if (!childCheckboxesChecked || childCheckboxesChecked.length === 0) {
            parentCheckbox.removeAttr(INTERMEDIATE_ATTR);
        }
    }

    function setClosestParentState(parentCheckbox, isChecked) {
        if (isChecked) {
            parentIntermediateStateOn(parentCheckbox);
        } else {
            parentIntermediateStateOff(parentCheckbox);
        }
    }

    function setParentsState(el, isChecked) {
        const parent = el.closest('.rollout-manager-nestedcheckboxlist')
            .closest('li.rollout-manager-nestedcheckboxlist-item');
        if (parent.length > 0) {
            const parentCheckbox = parent.find('coral-checkbox').first();
            if (parentCheckbox.length > 0) {
                setClosestParentState(parentCheckbox, isChecked);
                setParentsState(parent, isChecked);
            }
        }
    }

    function setCurrentAndChildrenState(currentCheckbox, isChecked) {
        const parentLi = currentCheckbox.closest('li');
        const childCheckboxes = parentLi.find('coral-accordion-item-content')
            .find(CORAL_CHECKBOX_ITEM);
        if (isChecked) {
            currentCheckbox.attr(INTERMEDIATE_ATTR, true);
            childCheckboxes.attr(INTERMEDIATE_ATTR, true);
        } else {
            const isIntermediateState = currentCheckbox.attr(INTERMEDIATE_ATTR);
            if (isIntermediateState && childCheckboxes.length > 0) {
                currentCheckbox.prop('checked', true);
                currentCheckbox.removeAttr(INTERMEDIATE_ATTR);
                childCheckboxes.removeAttr(INTERMEDIATE_ATTR);
            }
        }
        childCheckboxes.prop('checked', isChecked);
    }

    $(document).off('change.rollout-manager')
        .on('change.rollout-manager', CORAL_CHECKBOX_ITEM, function (e) {
            e.stopPropagation();

            const coralCheckbox = $(this);
            const isChecked = coralCheckbox.prop('checked');

            setCurrentAndChildrenState(coralCheckbox, isChecked);
            setParentsState(coralCheckbox, isChecked);
        });
})(document, Granite.$);
