(function (document, $) {
    'use strict';

    function onRefreshClick() {
        $('#history').adaptTo('foundation-collection').reload();
    }

    function onShowExtraClick() {
        $(this).closest('td').addClass('shows-extra');
    }

    $(document).off('erm-history')
        .on('click.erm-history', '#refresh', onRefreshClick)
        .on('click.erm-history', '.show-extra', onShowExtraClick);
})(document, Granite.$);
