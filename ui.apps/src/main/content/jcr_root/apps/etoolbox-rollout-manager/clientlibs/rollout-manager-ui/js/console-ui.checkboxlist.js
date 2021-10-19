(function(document, $) {
    "use strict";

    function updateParents(el, checked) {
        var parent = el.closest(".rollout-manager-nestedcheckboxlist").closest("li.rollout-manager-nestedcheckboxlist-item");
        if (parent && parent.length > 0) {
            var parentCoralCheckbox = parent.find("coral-checkbox").first();
            if (parentCoralCheckbox && parentCoralCheckbox.length > 0) {
                parentCoralCheckbox.prop('checked', checked);
                updateParents(parent, checked);
            }
        }
    }

    $(document).on("change", "coral-checkbox[name='liveCopyProperties[]']", function(e) {
        e.stopPropagation();

        var coralCheckbox = $(this);
        var checked = coralCheckbox.prop("checked");

        $(this).closest("li").find("coral-checkbox[name='liveCopyProperties[]']").prop('checked', checked);

        if (checked) {
            updateParents($(this), checked);
        }
    });
})(document, Granite.$);