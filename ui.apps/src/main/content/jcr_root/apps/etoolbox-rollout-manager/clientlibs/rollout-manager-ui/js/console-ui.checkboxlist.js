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
 * Nested checkboxes tree actions definition.
 *
 * Each checkbox has states described below.
 * - 3 states for a checkbox if contains children: check itself + check children > check itself + uncheck children > uncheck itself
 * - 2 states for a checkbox if no children: check > uncheck
 *
 * In addition, checking the current checkbox also checks all parent checkboxes, since it shouldn't be possible
 * to rollout child path w/o rolling out parent target paths
 */

(function (document, $) {
    "use strict";

    function updateParents(el, checked) {
        let parent = el.closest(".rollout-manager-nestedcheckboxlist")
            .closest("li.rollout-manager-nestedcheckboxlist-item");
        if (parent && parent.length > 0) {
            let parentCoralCheckbox = parent.find("coral-checkbox").first();
            if (parentCoralCheckbox && parentCoralCheckbox.length > 0) {
                if (checked) {
                    parentCoralCheckbox.attr('intermediate', true);
                    parentCoralCheckbox.prop('checked', true);
                } else {
                    let childCheckboxesChecked = parentCoralCheckbox.closest("li")
                        .find("coral-accordion-item-content")
                        .find("coral-checkbox[name='liveCopyProperties[]'][checked]")
                    if (!childCheckboxesChecked || childCheckboxesChecked.length === 0) {
                        parentCoralCheckbox.removeAttr('intermediate');
                    }
                }
                updateParents(parent, checked);
            }
        }
    }

    $(document).on("change", "coral-checkbox[name='liveCopyProperties[]']", function (e) {
        e.stopPropagation();

        let coralCheckbox = $(this);
        let isChecked = coralCheckbox.prop("checked");

        let parentLi = $(this).closest("li");
        let childCheckboxes = parentLi.find("coral-accordion-item-content")
            .find("coral-checkbox[name='liveCopyProperties[]']");

        if (isChecked) {
            $(this).attr('intermediate', true);
            childCheckboxes.attr('intermediate', true);
        } else {
            let isIntermediateState = coralCheckbox.attr("intermediate");
            if (isIntermediateState && childCheckboxes.length > 0) {
                $(this).prop('checked', true);
                $(this).removeAttr('intermediate');
                childCheckboxes.removeAttr('intermediate');
            }
        }
        updateParents($(this), isChecked);
        childCheckboxes.prop('checked', isChecked);
    });
})(document, Granite.$);