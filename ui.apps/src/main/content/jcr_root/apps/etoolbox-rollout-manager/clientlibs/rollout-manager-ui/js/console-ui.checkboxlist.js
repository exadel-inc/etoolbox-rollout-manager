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