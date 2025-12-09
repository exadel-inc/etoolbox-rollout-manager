(function ($, ns, Granite) {
    'use strict';

    const ACTIVE_TASKS_ID ='erm-id';
    const OPEN_DIALOG_KEY = 'erm-open-id';

    function parseData(key) {
        try {
            return JSON.parse(sessionStorage.getItem(key)) || [];
        } catch(e) {
            return [];
        }
    }

    function getItemsData() {
        return parseData(ACTIVE_TASKS_ID);
    }
    ns.getItemsData = getItemsData;

    function changeItemsData(type, id, offset, path) {
        const data = getItemsData();
        if (!data.length && type !== 'add') return;
        let newData;
        switch (type) {
            case 'add':
                newData = addItemData(data, id, offset, path);
                break;
            case 'update':
                newData = updateItemData(data, id, offset);
                break;
            case 'remove':
                newData = removeItemData(data, id);
                break;
        }
        sessionStorage[ACTIVE_TASKS_ID] = JSON.stringify(newData);
    }
    ns.changeItemsData = changeItemsData;

    function addItemData(data, id, offset, path) {
        data.push({id, offset, path});
        return data;
    }

    function updateItemData(data, id, offset) {
        data.forEach(item => {
            if (item.id === id) item.offset = offset;
        })
        return data;
    }

    function removeItemData(data, id) {
        return data.filter((item) => item.id !== id);
    }

    function removeOpenDialogKey() {
        sessionStorage.removeItem(OPEN_DIALOG_KEY);
    }
    ns.removeOpenDialogKey = removeOpenDialogKey;

    function setOpenDialogKey(data) {
        sessionStorage.setItem(OPEN_DIALOG_KEY, data);
    }
    ns.setOpenDialogKey = setOpenDialogKey;

    function getOpenDialogData() {
        return parseData(OPEN_DIALOG_KEY);
    }
    ns.getOpenDialogData = getOpenDialogData;

})(Granite.$, window.ERM = (window.ERM || {}), Granite);
