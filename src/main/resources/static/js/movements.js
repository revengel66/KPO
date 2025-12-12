(function () {
    const bootstrapRef = window.bootstrap;
    const emitToast = (message, type = 'success') => {
        if (typeof window.showAppToast === 'function' && message) {
            window.showAppToast(message, type);
        }
    };

    const MOVEMENT_CONFIG = {
        INBOUND: {
            basePath: '/deliveries',
            listTitle: 'Поставки',
            detailTitle: 'Поставка',
            listColumns: [
                {id: 'warehouse', label: 'Склад', getValue: (m) => m.warehouse?.name || '—'},
                {id: 'second', label: 'Поставщик', getValue: (m) => m.counterparty?.name || '—'},
                {id: 'third', label: 'Приёмщик', getValue: (m) => m.employee?.name || '—'}
            ],
            formFields: {
                requireCounterparty: true,
                requireTargetWarehouse: false,
                requireTargetEmployee: false
            },
            formLabels: {
                warehouse: 'Склад',
                counterparty: 'Поставщик',
                employee: 'Приёмщик',
                targetWarehouse: 'Склад приёмки',
                targetEmployee: 'Сотрудник приёмки'
            },
            detailLabels: {
                warehouse: 'Склад',
                counterparty: 'Поставщик',
                employee: 'Приёмщик',
                targetWarehouse: null,
                targetEmployee: null
            }
        },
        OUTBOUND: {
            basePath: '/shipments',
            listTitle: 'Отгрузки',
            detailTitle: 'Отгрузка',
            listColumns: [
                {id: 'warehouse', label: 'Склад', getValue: (m) => m.warehouse?.name || '—'},
                {id: 'second', label: 'Приёмщик', getValue: (m) => m.counterparty?.name || '—'},
                {id: 'third', label: 'Отгрузчик', getValue: (m) => m.employee?.name || '—'}
            ],
            formFields: {
                requireCounterparty: true,
                requireTargetWarehouse: false,
                requireTargetEmployee: false
            },
            formLabels: {
                warehouse: 'Склад',
                counterparty: 'Приёмщик',
                employee: 'Отгрузчик',
                targetWarehouse: 'Склад приёмки',
                targetEmployee: 'Сотрудник приёмки'
            },
            detailLabels: {
                warehouse: 'Склад',
                counterparty: 'Приёмщик',
                employee: 'Отгрузчик',
                targetWarehouse: null,
                targetEmployee: null
            }
        },
        TRANSFER: {
            basePath: '/transfers',
            listTitle: 'Трансферы',
            detailTitle: 'Трансфер',
            listColumns: [
                {id: 'warehouse', label: 'Склад отправки', getValue: (m) => m.warehouse?.name || '—'},
                {id: 'second', label: 'Склад приёмки', getValue: (m) => m.targetWarehouse?.name || '—'},
                {id: 'third', label: 'Отгрузчик', getValue: (m) => m.employee?.name || '—'},
                {id: 'fourth', label: 'Приёмщик', getValue: (m) => m.targetEmployee?.name || '—'}
            ],
            formFields: {
                requireCounterparty: false,
                requireTargetWarehouse: true,
                requireTargetEmployee: true
            },
            formLabels: {
                warehouse: 'Склад отправки',
                counterparty: '',
                employee: 'Отгрузчик',
                targetWarehouse: 'Склад приёмки',
                targetEmployee: 'Приёмщик'
            },
            detailLabels: {
                warehouse: 'Склад отправки',
                counterparty: null,
                employee: 'Отгрузчик',
                targetWarehouse: 'Склад приёмки',
                targetEmployee: 'Приёмщик'
            }
        }
    };

    const referenceCache = {
        data: null,
        promise: null,
        categoriesPromise: null
    };

    document.addEventListener('DOMContentLoaded', () => {
        initMovementListPage();
        initMovementDetailPage();
    });

    function initMovementListPage() {
        const root = document.getElementById('movementListPage');
        if (!root) {
            return;
        }
        if (!localStorage.getItem('authToken')) {
            window.location.href = '/login';
            return;
        }

        const movementType = root.dataset.movementType;
        const config = MOVEMENT_CONFIG[movementType];
        if (!config) {
            return;
        }
        const formLabels = config.formLabels || config.detailLabels || null;

        const loader = document.getElementById('movementListLoader');
        const tableWrapper = document.getElementById('movementListTableWrapper');
        const emptyState = document.getElementById('movementListEmpty');
        const tableBody = document.getElementById('movementListTableBody');
        const addForm = document.getElementById('movementAddForm');
        const editForm = document.getElementById('movementEditForm');
        const addModalElement = document.getElementById('movementAddModal');
        const editModalElement = document.getElementById('movementEditModal');
        const addModal = addModalElement && bootstrapRef ? new bootstrapRef.Modal(addModalElement) : null;
        const editModal = editModalElement && bootstrapRef ? new bootstrapRef.Modal(editModalElement) : null;
        const addSubmitButton = document.getElementById('movementAddSubmit');
        const addSubmitSpinner = addSubmitButton ? addSubmitButton.querySelector('.spinner-border') : null;
        const editSubmitButton = document.getElementById('movementEditSubmit');
        const editSubmitSpinner = editSubmitButton ? editSubmitButton.querySelector('.spinner-border') : null;
        const alertModal = createFeedbackModal('movementListAlertModal');
        setupDateInput(addForm?.date);
        setupDateInput(editForm?.date);

        const columnMap = {
            warehouse: root.querySelector('[data-column="warehouse"]'),
            second: root.querySelector('[data-column="second"]'),
            third: root.querySelector('[data-column="third"]'),
            fourth: root.querySelector('[data-column="fourth"]')
        };
        let movements = [];
        let editingMovement = null;

        const waitForReferences = async () => {
            try {
                await ensureReferences();
                return true;
            } catch (error) {
                showReferenceError(error, alertModal);
                return false;
            }
        };

        configureHeadings();
        const referencesPromise = waitForReferences();
        referencesPromise.then((ready) => {
            if (!ready) {
                return;
            }
            setupFormFields(addForm, config.formFields, formLabels);
            setupFormFields(editForm, config.formFields, formLabels);
            populateFormSelects(addForm);
            populateFormSelects(editForm);
            applyDateColumnLayout(addForm, movementType);
            applyDateColumnLayout(editForm, movementType);
        });
        loadMovements();

        if (addModalElement) {
            addModalElement.addEventListener('show.bs.modal', () => {
                if (addForm) {
                    addForm.reset();
                    addForm.classList.remove('was-validated');
                    if (addForm.date) {
                        addForm.date.value = getCurrentDateInputValue();
                    }
                    setupFormFields(addForm, config.formFields, formLabels);
                    populateFormSelects(addForm);
                }
            });
        }

        if (addForm) {
            addForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                const referencesLoaded = await waitForReferences();
                if (!referencesLoaded) {
                    return;
                }
                if (!addForm.checkValidity()) {
                    addForm.classList.add('was-validated');
                    return;
                }
                toggleLoading(addSubmitButton, addSubmitSpinner, true);
                try {
                    const fields = collectMovementFields(addForm);
                    const payload = createMovementPayload(fields, movementType, []);
                    const created = await apiRequest('/movements', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify(payload)
                    });
                    movements.push(created);
                    renderTable();
                    if (addModal) {
                        addModal.hide();
                    }
                    emitToast('Операция создана');
                } catch (error) {
                    handleAuthError(error, alertModal) || alertModal.show({
                        title: 'Не удалось сохранить операцию',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                } finally {
                    toggleLoading(addSubmitButton, addSubmitSpinner, false);
                }
            });
        }

        if (editForm) {
            editModalElement?.addEventListener('show.bs.modal', () => {
                if (!editingMovement) {
                    editModal?.hide();
                    return;
                }
                populateFormSelects(editForm);
                fillFormWithMovement(editForm, editingMovement);
            });
            editForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!editingMovement) {
                    return;
                }
                if (!editForm.checkValidity()) {
                    editForm.classList.add('was-validated');
                    return;
                }
                toggleLoading(editSubmitButton, editSubmitSpinner, true);
                try {
                    const fields = collectMovementFields(editForm);
                    const itemsPayload = (editingMovement.items || []).map((item) => ({
                        product: {id: item.product.id},
                        quantity: item.quantity
                    }));
                    const payload = createMovementPayload(fields, movementType, itemsPayload);
                    const updated = await apiRequest(`/movements/${editingMovement.id}`, {
                        method: 'PUT',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify(payload)
                    });
                    movements = movements.map((movement) => movement.id === updated.id ? updated : movement);
                    renderTable();
                    editModal?.hide();
                    editingMovement = null;
                    emitToast('Операция обновлена');
                } catch (error) {
                    handleAuthError(error, alertModal) || alertModal.show({
                        title: 'Не удалось обновить операцию',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                } finally {
                    toggleLoading(editSubmitButton, editSubmitSpinner, false);
                }
            });
        }

        function configureHeadings() {
            ['warehouse', 'second', 'third', 'fourth'].forEach((key) => {
                const th = columnMap[key];
                const descriptor = config.listColumns.find((column) => column.id === key);
                if (!th) {
                    return;
                }
                if (descriptor) {
                    th.textContent = descriptor.label;
                    th.classList.remove('d-none');
                } else if (key !== 'warehouse') {
                    th.classList.add('d-none');
                }
            });
        }

        function renderTable() {
            if (!tableBody) {
                return;
            }
            if (!movements.length) {
                tableBody.innerHTML = '';
                tableWrapper?.classList.add('d-none');
                emptyState?.classList.remove('d-none');
                return;
            }
            tableWrapper?.classList.remove('d-none');
            emptyState?.classList.add('d-none');

            const fragment = document.createDocumentFragment();
            movements.forEach((movement) => {
                const row = document.createElement('tr');
                const cells = [
                    `<td>${movement.id}</td>`,
                    `<td>${formatMovementDate(movement.date)}</td>`
                ];
                config.listColumns.forEach((column) => {
                    const align = column.align === 'right' ? 'text-end' : '';
                    cells.push(`<td class="${align}">${escapeHtml(column.getValue(movement))}</td>`);
                });
                cells.push(`
                    <td class="text-end">
                        <div class="table-actions">
                            <button type="button" class="btn-icon" data-action="view" data-id="${movement.id}" title="Просмотр">
                                <i class="bi bi-eye"></i>
                            </button>
                            <button type="button" class="btn-icon" data-action="edit" data-id="${movement.id}" title="Редактировать">
                                <i class="bi bi-pencil"></i>
                            </button>
                            <button type="button" class="btn-icon" data-action="delete" data-id="${movement.id}" title="Удалить">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                `);
                row.innerHTML = cells.join('');
                fragment.appendChild(row);
            });
            tableBody.innerHTML = '';
            tableBody.appendChild(fragment);

            tableBody.querySelectorAll('[data-action="view"]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = button.getAttribute('data-id');
                    window.location.href = `${config.basePath}/${id}`;
                });
            });
            tableBody.querySelectorAll('[data-action="edit"]').forEach((button) => {
                button.addEventListener('click', (event) => {
                    const id = Number(event.currentTarget.getAttribute('data-id'));
                    editingMovement = movements.find((movement) => movement.id === id) || null;
                    if (!editingMovement) {
                        return;
                    }
                    editModal?.show();
                });
            });
            tableBody.querySelectorAll('[data-action="delete"]').forEach((button) => {
                button.addEventListener('click', (event) => {
                    const id = Number(event.currentTarget.getAttribute('data-id'));
                    alertModal.show({
                        title: 'Удалить операцию?',
                        message: `Запись #${id} будет удалена.`,
                        actionText: 'Удалить',
                        closeText: 'Отмена',
                        onAction: () => deleteMovement(id)
                    });
                });
            });
        }

        async function loadMovements() {
            toggleSectionLoader(loader, tableWrapper, true);
            try {
                const data = await apiRequest(`/movements?type=${movementType}`);
                movements = Array.isArray(data) ? data : [];
                renderTable();
            } catch (error) {
                handleAuthError(error, alertModal) || alertModal.show({
                    title: 'Не удалось загрузить список',
                    message: error.message || 'Попробуйте повторить попытку позже.'
                });
            } finally {
                toggleSectionLoader(loader, tableWrapper, false);
            }
        }

        async function deleteMovement(id) {
            try {
                await apiRequest(`/movements/${id}`, {method: 'DELETE'});
                movements = movements.filter((movement) => movement.id !== id);
                renderTable();
                emitToast('Операция удалена');
            } catch (error) {
                handleAuthError(error, alertModal) || alertModal.show({
                    title: 'Не удалось удалить операцию',
                    message: error.message || 'Попробуйте повторить попытку позже.'
                });
            }
        }

    }

    function initMovementDetailPage() {
        const root = document.getElementById('movementDetailPage');
        if (!root) {
            return;
        }
        if (!localStorage.getItem('authToken')) {
            window.location.href = '/login';
            return;
        }

        const movementType = root.dataset.movementType;
        const config = MOVEMENT_CONFIG[movementType];
        if (!config) {
            return;
        }
        const formLabels = config.formLabels || config.detailLabels || null;

        const movementId = extractMovementId();
        const returnPath = root.dataset.returnPath || config.basePath;
        const titleElement = document.getElementById('movementDetailTitle');
        const infoDate = document.getElementById('movementInfoDate');
        const infoWarehouse = document.getElementById('movementInfoWarehouse');
        const infoTargetWarehouse = document.getElementById('movementInfoTargetWarehouse');
        const infoCounterparty = document.getElementById('movementInfoCounterparty');
        const infoEmployee = document.getElementById('movementInfoEmployee');
        const infoTargetEmployee = document.getElementById('movementInfoTargetEmployee');
        const infoDescription = document.getElementById('movementInfoDescription');
        const itemsWrapper = document.getElementById('movementItemsTableWrapper');
        const itemsBody = document.getElementById('movementItemsTableBody');
        const itemsLoader = document.getElementById('movementItemsLoader');
        const itemsEmpty = document.getElementById('movementItemsEmpty');
        const editButton = document.getElementById('movementEditButton');
        const deleteButton = document.getElementById('movementDeleteButton');
        const refreshButton = document.getElementById('movementRefreshButton');
        const addItemButton = document.getElementById('movementAddItemButton');
        const editModalElement = document.getElementById('movementDetailEditModal');
        const editForm = document.getElementById('movementDetailEditForm');
        const editModal = editModalElement && bootstrapRef ? new bootstrapRef.Modal(editModalElement) : null;
        const itemModalElement = document.getElementById('movementItemModal');
        const itemForm = document.getElementById('movementItemForm');
        const itemModal = itemModalElement && bootstrapRef ? new bootstrapRef.Modal(itemModalElement) : null;
        const alertModal = createFeedbackModal('movementDetailAlertModal');
        const employeeLabel = document.getElementById('movementEmployeeLabel');
        const targetEmployeeLabel = document.getElementById('movementTargetEmployeeLabel');
        const productSearchInput = document.getElementById('movementItemProductSearch');
        const productIdInput = document.getElementById('movementItemProductId');
        const productSuggestions = document.getElementById('movementItemProductSuggestions');
        const newProductToggle = document.getElementById('movementItemCreateProductToggle');
        const newProductSection = document.getElementById('movementItemNewProduct');
        const newProductNameInput = document.getElementById('movementItemNewProductName');
        const newProductCategorySelect = document.getElementById('movementItemNewProductCategory');
        const newProductSaveButton = document.getElementById('movementItemSaveNewProduct');
        const newProductCancelButton = document.getElementById('movementItemCancelNewProduct');
        let currentMovement = null;
        let itemMode = 'add';
        let editingItemId = null;

        setupDateInput(editForm?.date);
        setupProductSearch();

        function setupProductSearch() {
            if (productSearchInput) {
                productSearchInput.addEventListener('input', handleProductInputChange);
                productSearchInput.addEventListener('blur', handleProductInputChange);
            }
            newProductToggle?.addEventListener('click', () => toggleNewProductForm(true));
            newProductCancelButton?.addEventListener('click', () => toggleNewProductForm(false));
            newProductSaveButton?.addEventListener('click', saveNewProduct);
        }

        function updateProductSuggestions() {
            if (!productSuggestions) {
                return;
            }
            const products = referenceCache.data?.products || [];
            const options = products
                .slice()
                .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
                .map((product) => `<option value="${escapeHtml(product.name || '')}"></option>`);
            productSuggestions.innerHTML = options.join('');
        }

        async function populateNewProductCategories() {
            if (!newProductCategorySelect) {
                return;
            }
            const categories = await ensureCategories();
            const options = ['<option value="" disabled selected>Выберите категорию</option>'];
            categories.forEach((category) => {
                options.push(`<option value="${category.id}">${escapeHtml(category.name || '')}</option>`);
            });
            newProductCategorySelect.innerHTML = options.join('');
        }

        function handleProductInputChange() {
            if (!productSearchInput || !productIdInput) {
                return;
            }
            const product = findProductByName(productSearchInput.value);
            if (product) {
                selectProduct(product);
            } else {
                productIdInput.value = '';
            }
        }

        function findProductByName(name) {
            if (!name) {
                return null;
            }
            const normalized = name.trim().toLowerCase();
            return (referenceCache.data?.products || []).find((product) => (product.name || '').trim().toLowerCase() === normalized) || null;
        }

        function selectProduct(product) {
            if (!productSearchInput || !productIdInput || !product) {
                return;
            }
            productSearchInput.value = product.name || '';
            productIdInput.value = product.id || '';
            productSearchInput.setCustomValidity('');
            toggleNewProductForm(false);
        }

        function resetProductSelection() {
            if (productSearchInput) {
                productSearchInput.value = '';
                productSearchInput.disabled = false;
                productSearchInput.setCustomValidity('');
            }
            if (productIdInput) {
                productIdInput.value = '';
            }
            toggleNewProductForm(false);
        }

        function toggleNewProductForm(visible) {
            if (!newProductSection) {
                return;
            }
            newProductSection.classList.toggle('d-none', !visible);
            if (!visible) {
                newProductNameInput && (newProductNameInput.value = '');
                if (newProductCategorySelect) {
                    newProductCategorySelect.selectedIndex = 0;
                }
                newProductNameInput?.classList.remove('is-invalid');
                newProductCategorySelect?.classList.remove('is-invalid');
            } else {
                ensureCategories()
                    .then(populateNewProductCategories)
                    .then(() => newProductNameInput?.focus())
                    .catch((error) => {
                        handleAuthError(error, alertModal) || alertModal.show({
                            title: 'Не удалось загрузить категории',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                        toggleNewProductForm(false);
                    });
            }
        }

        async function saveNewProduct() {
            if (!newProductNameInput || !newProductCategorySelect || !productSearchInput || !productIdInput) {
                return;
            }
            const name = newProductNameInput.value.trim();
            const categoryId = Number(newProductCategorySelect.value);
            if (!name || !categoryId) {
                newProductNameInput.classList.toggle('is-invalid', !name);
                newProductCategorySelect.classList.toggle('is-invalid', !categoryId);
                return;
            }
            newProductNameInput.classList.remove('is-invalid');
            newProductCategorySelect.classList.remove('is-invalid');
            newProductSaveButton?.setAttribute('disabled', 'disabled');
            try {
                const payload = {
                    name,
                    info: null,
                    category: {id: categoryId}
                };
                const created = await apiRequest('/products', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(payload)
                });
                referenceCache.data = referenceCache.data || {};
                referenceCache.data.products = referenceCache.data.products || [];
                referenceCache.data.products.push(created);
                updateProductSuggestions();
                selectProduct(created);
                emitToast('Товар создан');
            } catch (error) {
                handleAuthError(error, alertModal) || alertModal.show({
                    title: 'Не удалось создать товар',
                    message: error.message || 'Попробуйте повторить попытку позже.'
                });
            } finally {
                newProductSaveButton?.removeAttribute('disabled');
                toggleNewProductForm(false);
            }
        }

        const waitForReferences = async () => {
            try {
                await ensureReferences();
                return true;
            } catch (error) {
                showReferenceError(error, alertModal);
                return false;
            }
        };

        const referencesPromise = waitForReferences();
        referencesPromise.then((ready) => {
            if (ready) {
                setupFormFields(editForm, config.formFields, formLabels);
                populateFormSelects(editForm);
                updateProductSuggestions();
                applyDateColumnLayout(editForm, movementType);
            }
        });
        loadMovement();
        if (editButton && editForm) {
            editButton.addEventListener('click', async () => {
                if (!currentMovement) {
                    return;
                }
                const ready = referenceCache.data ? true : await waitForReferences();
                if (!ready) {
                    return;
                }
                setupFormFields(editForm, config.formFields, formLabels);
                applyDateColumnLayout(editForm, movementType);
                populateFormSelects(editForm);
                fillFormWithMovement(editForm, currentMovement);
                editForm.classList.remove('was-validated');
                editModal?.show();
            });
        }

        if (editForm) {
            editForm.addEventListener('submit', (event) => {
                event.preventDefault();
                if (!currentMovement) {
                    return;
                }
                if (!editForm.checkValidity()) {
                    editForm.classList.add('was-validated');
                    return;
                }
                const fields = collectMovementFields(editForm);
                const itemsPayload = (currentMovement.items || []).map((item) => ({
                    product: {id: item.product.id},
                    quantity: item.quantity
                }));
                saveMovement(fields, itemsPayload, editModal, 'Операция обновлена');
            });
        }

        if (deleteButton) {
            deleteButton.addEventListener('click', () => {
                alertModal.show({
                    title: 'Удалить операцию?',
                    message: 'Действие нельзя отменить.',
                    actionText: 'Удалить',
                    closeText: 'Отмена',
                    onAction: deleteMovement
                });
            });
        }

        refreshButton?.addEventListener('click', loadMovement);

        addItemButton?.addEventListener('click', () => openItemModal('add'));

        if (itemForm) {
            itemForm.addEventListener('submit', (event) => {
                event.preventDefault();
                if (!currentMovement) {
                    return;
                }
                if (!itemForm.checkValidity()) {
                    itemForm.classList.add('was-validated');
                    return;
                }
                const productId = Number(productIdInput?.value || 0);
                if (!productId) {
                    itemForm.classList.add('was-validated');
                    productSearchInput?.setCustomValidity('Выберите товар из списка или создайте новый');
                    productSearchInput?.reportValidity();
                    return;
                } else if (productSearchInput) {
                    productSearchInput.setCustomValidity('');
                }
                const quantity = Number(itemForm.quantity.value);
                let nextItems = [...(currentMovement.items || [])];
                if (itemMode === 'add') {
                    const product = (referenceCache.data?.products || []).find((p) => p.id === productId);
                    if (!product) {
                        return;
                    }
                    nextItems.push({product, quantity});
                } else {
                    nextItems = nextItems.map((item) => item.id === editingItemId ? {...item, quantity} : item);
                }
                const itemsPayload = nextItems.map((item) => ({
                    product: {id: item.product.id},
                    quantity: item.quantity
                }));
                const fields = mapMovementToFields(currentMovement);
                const message = itemMode === 'add' ? 'Товар добавлен' : 'Количество обновлено';
                saveMovement(fields, itemsPayload, itemModal, message);
            });
        }

        async function loadMovement() {
            if (!movementId) {
                return;
            }
            toggleSectionLoader(itemsLoader, itemsWrapper, true);
            try {
                const movement = await apiRequest(`/movements/${movementId}`);
                currentMovement = movement;
                renderInfo();
                renderItems();
            } catch (error) {
                handleAuthError(error, alertModal) || alertModal.show({
                    title: 'Не удалось загрузить операцию',
                    message: error.message || 'Попробуйте обновить страницу.',
                    actionText: 'К списку',
                    onAction: () => {
                        window.location.href = returnPath;
                    }
                });
            } finally {
                toggleSectionLoader(itemsLoader, itemsWrapper, false);
            }
        }

        function renderInfo() {
            if (!currentMovement) {
                return;
            }
            const formattedDate = formatMovementDate(currentMovement.date);
            titleElement && (titleElement.textContent = `${config.detailTitle} ${formattedDate}`);
            infoDate && (infoDate.textContent = formattedDate);
            infoWarehouse && (infoWarehouse.textContent = currentMovement.warehouse?.name || '—');
            infoTargetWarehouse && (infoTargetWarehouse.textContent = currentMovement.targetWarehouse?.name || '—');
            infoCounterparty && (infoCounterparty.textContent = currentMovement.counterparty?.name || '—');
            infoEmployee && (infoEmployee.textContent = currentMovement.employee?.name || '—');
            infoTargetEmployee && (infoTargetEmployee.textContent = currentMovement.targetEmployee?.name || '—');
            infoDescription && (infoDescription.textContent = currentMovement.info || 'Информация отсутствует.');

            toggleDetailField(infoCounterparty?.closest('.movement-info-item'), !!config.detailLabels.counterparty);
            toggleDetailField(infoTargetWarehouse?.closest('.movement-info-item'), !!config.detailLabels.targetWarehouse);
            toggleDetailField(infoTargetEmployee?.closest('.movement-info-item'), !!config.detailLabels.targetEmployee);
            employeeLabel && (employeeLabel.textContent = config.detailLabels.employee || 'Сотрудник');
            if (targetEmployeeLabel) {
                targetEmployeeLabel.textContent = config.detailLabels.targetEmployee || 'Сотрудник приёмки';
            }
        }

        function renderItems() {
            if (!itemsBody) {
                return;
            }
            const items = currentMovement?.items || [];
            if (!items.length) {
                itemsBody.innerHTML = '';
                itemsWrapper?.classList.add('d-none');
                itemsEmpty?.classList.remove('d-none');
                return;
            }
            itemsWrapper?.classList.remove('d-none');
            itemsEmpty?.classList.add('d-none');

            const fragment = document.createDocumentFragment();
            items.forEach((item) => {
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${item.product?.id || '—'}</td>
                    <td>${escapeHtml(item.product?.name || '—')}</td>
                    <td class="text-end">${item.quantity}</td>
                    <td class="text-end">
                        <div class="table-actions">
                            <button type="button" class="btn-icon" data-item-action="edit" data-id="${item.id}" title="Редактировать">
                                <i class="bi bi-pencil"></i>
                            </button>
                            <button type="button" class="btn-icon" data-item-action="delete" data-id="${item.id}" title="Удалить">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                `;
                fragment.appendChild(row);
            });
            itemsBody.innerHTML = '';
            itemsBody.appendChild(fragment);

            itemsBody.querySelectorAll('[data-item-action="edit"]').forEach((button) => {
                button.addEventListener('click', (event) => openItemModal('edit', Number(event.currentTarget.getAttribute('data-id'))));
            });
            itemsBody.querySelectorAll('[data-item-action="delete"]').forEach((button) => {
                button.addEventListener('click', (event) => {
                    const id = Number(event.currentTarget.getAttribute('data-id'));
                    alertModal.show({
                        title: 'Удалить товар?',
                        message: 'Позиция будет удалена из операции.',
                        actionText: 'Удалить',
                        closeText: 'Отмена',
                        onAction: () => removeItem(id)
                    });
                });
            });
        }

        function openItemModal(mode, itemId) {
            if (!itemModal || !itemForm || !currentMovement) {
                return;
            }
            itemMode = mode;
            editingItemId = itemId || null;
            const quantityInput = itemForm.quantity;
            itemForm.classList.remove('was-validated');

            if (mode === 'add') {
                resetProductSelection();
                updateProductSuggestions();
                newProductToggle?.classList.remove('d-none');
                itemModalElement.querySelector('.modal-title').textContent = 'Добавить товар';
                if (quantityInput) {
                    quantityInput.value = '';
                }
            } else {
                const item = (currentMovement.items || []).find((row) => row.id === editingItemId);
                if (!item) {
                    return;
                }
                selectProduct(item.product);
                if (productSearchInput) {
                    productSearchInput.disabled = true;
                }
                newProductToggle?.classList.add('d-none');
                toggleNewProductForm(false);
                itemModalElement.querySelector('.modal-title').textContent = 'Редактировать товар';
                if (quantityInput) {
                    quantityInput.value = item.quantity;
                }
            }
            if (productSearchInput && itemMode === 'add') {
                productSearchInput.disabled = false;
                productSearchInput.focus();
            }
            itemModal.show();
        }

        function removeItem(itemId) {
            const nextItems = (currentMovement.items || []).filter((item) => item.id !== itemId);
            const payloadItems = nextItems.map((item) => ({
                product: {id: item.product.id},
                quantity: item.quantity
            }));
            const fields = mapMovementToFields(currentMovement);
            saveMovement(fields, payloadItems, null, 'Товар удалён');
        }

        async function saveMovement(fields, itemsPayload, modalInstance, successMessage) {
            try {
                const payload = createMovementPayload(fields, movementType, itemsPayload);
                const updated = await apiRequest(`/movements/${movementId}`, {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(payload)
                });
                currentMovement = updated;
                renderInfo();
                renderItems();
                modalInstance?.hide();
                if (successMessage) {
                    emitToast(successMessage);
                }
            } catch (error) {
                handleAuthError(error, alertModal) || alertModal.show({
                    title: 'Не удалось сохранить изменения',
                    message: error.message || 'Попробуйте повторить попытку позже.'
                });
            }
        }

        async function deleteMovement() {
            try {
                await apiRequest(`/movements/${movementId}`, {method: 'DELETE'});
                emitToast('Операция удалена');
                window.location.href = returnPath;
            } catch (error) {
                handleAuthError(error, alertModal) || alertModal.show({
                    title: 'Не удалось удалить операцию',
                    message: error.message || 'Попробуйте повторить попытку позже.'
                });
            }
        }

        function toggleDetailField(element, visible) {
            if (element) {
                element.classList.toggle('d-none', !visible);
            }
        }

        function mapMovementToFields(movement) {
            return {
                date: toInputDateValue(movement.date),
                warehouseId: movement.warehouse?.id,
                counterpartyId: movement.counterparty?.id || null,
                employeeId: movement.employee?.id,
                targetWarehouseId: movement.targetWarehouse?.id || null,
                targetEmployeeId: movement.targetEmployee?.id || null,
                info: movement.info || ''
            };
        }
    }

    function collectMovementFields(form) {
        if (!form) {
            return null;
        }
        return {
            date: form.date.value,
            warehouseId: Number(form.warehouseId.value),
            counterpartyId: form.counterpartyId ? Number(form.counterpartyId.value || 0) || null : null,
            employeeId: Number(form.employeeId.value),
            targetWarehouseId: form.targetWarehouseId ? Number(form.targetWarehouseId.value || 0) || null : null,
            targetEmployeeId: form.targetEmployeeId ? Number(form.targetEmployeeId.value || 0) || null : null,
            info: form.info.value.trim()
        };
    }

    function setupFormFields(form, visibility, labels) {
        if (!form || !visibility) {
            return;
        }
        toggleField(form, 'counterparty', visibility.requireCounterparty);
        toggleField(form, 'targetWarehouse', visibility.requireTargetWarehouse);
        toggleField(form, 'targetEmployee', visibility.requireTargetEmployee);
        setFormLabels(form, labels, visibility);
    }

    function toggleField(form, fieldName, visible) {
        form?.querySelectorAll(`[data-field="${fieldName}"]`).forEach((wrapper) => {
            wrapper.classList.toggle('d-none', !visible);
            const select = wrapper.querySelector('select');
            if (select) {
                select.required = !!visible;
                if (!visible) {
                    select.value = '';
                }
            }
        });
    }

    function setFormLabels(form, labels) {
        if (!form || !labels) {
            return;
        }
        form.querySelectorAll('[data-label-field]').forEach((labelElement) => {
            const target = labelElement.querySelector('[data-label-text]') || labelElement;
            const field = labelElement.getAttribute('data-label-field');
            if (!labelElement.dataset.defaultLabel) {
                labelElement.dataset.defaultLabel = target.textContent.trim();
            }
            const nextLabel = labels[field];
            target.textContent = nextLabel || labelElement.dataset.defaultLabel || target.textContent;
        });
    }

    function fillFormWithMovement(form, movement) {
        if (!form || !movement) {
            return;
        }
        if (form.date) {
            form.date.value = toInputDateValue(movement.date) || getCurrentDateInputValue();
        }
        form.warehouseId.value = movement.warehouse?.id || '';
        if (form.counterpartyId) {
            form.counterpartyId.value = movement.counterparty?.id || '';
        }
        form.employeeId.value = movement.employee?.id || '';
        if (form.targetWarehouseId) {
            form.targetWarehouseId.value = movement.targetWarehouse?.id || '';
        }
        if (form.targetEmployeeId) {
            form.targetEmployeeId.value = movement.targetEmployee?.id || '';
        }
        form.info.value = movement.info || '';
    }

    function createMovementPayload(fields, movementType, items) {
        return {
            date: normalizeDateForPayload(fields.date),
            type: movementType,
            info: fields.info || null,
            warehouse: {id: fields.warehouseId},
            employee: {id: fields.employeeId},
            counterparty: fields.counterpartyId ? {id: fields.counterpartyId} : null,
            targetWarehouse: fields.targetWarehouseId ? {id: fields.targetWarehouseId} : null,
            targetEmployee: fields.targetEmployeeId ? {id: fields.targetEmployeeId} : null,
            items
        };
    }

    function toggleLoading(button, spinner, state) {
        button && (button.disabled = state);
        spinner && spinner.classList.toggle('d-none', !state);
    }

    function toggleSectionLoader(loader, content, state) {
        loader?.classList.toggle('d-none', !state);
        content?.classList.toggle('d-none', state);
    }

    async function ensureReferences() {
        if (referenceCache.data) {
            return referenceCache.data;
        }
        if (referenceCache.promise) {
            return referenceCache.promise;
        }
        referenceCache.promise = Promise.all([
            apiRequest('/warehouses'),
            apiRequest('/employees'),
            apiRequest('/counterparties'),
            apiRequest('/products')
        ]).then(([warehouses, employees, counterparties, products]) => {
            referenceCache.data = {
                warehouses: warehouses || [],
                employees: employees || [],
                counterparties: counterparties || [],
                products: products || []
            };
            return referenceCache.data;
        }).finally(() => {
            referenceCache.promise = null;
        });
        return referenceCache.promise;
    }

    async function ensureCategories() {
        if (referenceCache.data?.categories) {
            return referenceCache.data.categories;
        }
        if (referenceCache.categoriesPromise) {
            return referenceCache.categoriesPromise;
        }
        referenceCache.categoriesPromise = apiRequest('/categories')
            .then((categories) => {
                referenceCache.data = referenceCache.data || {};
                referenceCache.data.categories = categories || [];
                return referenceCache.data.categories;
            })
            .finally(() => {
                referenceCache.categoriesPromise = null;
            });
        return referenceCache.categoriesPromise;
    }

    function populateSelect(select, items, placeholder) {
        if (!select) {
            return;
        }
        const options = [`<option value="" disabled selected>${placeholder || 'Выберите значение'}</option>`];
        items.forEach((item) => {
            options.push(`<option value="${item.id}">${escapeHtml(item.name || '')}</option>`);
        });
        select.innerHTML = options.join('');
    }

    function populateFormSelects(form) {
        if (!form || !referenceCache.data) {
            return;
        }
        populateSelect(form.warehouseId, referenceCache.data.warehouses, 'Выберите склад');
        if (form.targetWarehouseId) {
            populateSelect(form.targetWarehouseId, referenceCache.data.warehouses, 'Выберите склад');
        }
        if (form.counterpartyId) {
            populateSelect(form.counterpartyId, referenceCache.data.counterparties, 'Выберите контрагента');
        }
        populateSelect(form.employeeId, referenceCache.data.employees, 'Выберите сотрудника');
        if (form.targetEmployeeId) {
            populateSelect(form.targetEmployeeId, referenceCache.data.employees, 'Выберите сотрудника');
        }
        if (form.productId) {
            populateSelect(form.productId, referenceCache.data.products, 'Выберите товар');
        }
    }

    function formatMovementDate(value) {
        const date = parseDateValue(value);
        if (!date) {
            return value || '—';
        }
        return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function toInputDateValue(value) {
        const date = parseDateValue(value);
        return date ? formatDateForInput(date) : '';
    }

    function normalizeDateForPayload(value) {
        const date = parseDateValue(value);
        if (!date) {
            return null;
        }
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    }

    function getCurrentDateInputValue() {
        return formatDateForInput(new Date());
    }

    function formatDateForInput(date) {
        if (!date) {
            return '';
        }
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function parseDateValue(value) {
        if (!value) {
            return null;
        }
        const trimmed = value.trim();
        if (!trimmed) {
            return null;
        }
        const dotPattern = /^(\d{2})\.(\d{2})\.(\d{4})(?:\s+(\d{2}):(\d{2}))?$/;
        let match = trimmed.match(dotPattern);
        if (match) {
            const [, day, month, year, hour = '00', minute = '00'] = match;
            return new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), 0);
        }
        const isoPattern = /^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2}))?)?$/;
        match = trimmed.match(isoPattern);
        if (match) {
            const [, year, month, day, hour = '00', minute = '00', second = '00'] = match;
            return new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), Number(second));
        }
        return null;
    }

    function setupDateInput(input) {
        if (!input) {
            return;
        }
        const normalize = () => {
            const normalized = toInputDateValue(input.value);
            if (normalized) {
                input.value = normalized;
            }
        };
        input.addEventListener('change', normalize);
        input.addEventListener('blur', normalize);
        input.addEventListener('paste', (event) => {
            const text = (event.clipboardData || window.clipboardData)?.getData('text');
            if (!text) {
                return;
            }
            const normalized = toInputDateValue(text);
            if (normalized) {
                event.preventDefault();
                input.value = normalized;
            }
        });
    }

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function extractMovementId() {
        const match = window.location.pathname.match(/(\d+)(?:\/?$)/);
        return match ? Number(match[1]) : null;
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function createFeedbackModal(modalId) {
        const modalElement = document.getElementById(modalId);
        if (!modalElement || !bootstrapRef) {
            return {
                show: ({title, message}) => window.alert([title, message].filter(Boolean).join('\\n')),
                hide: () => undefined
            };
        }
        const modal = new bootstrapRef.Modal(modalElement);
        const titleElement = modalElement.querySelector('[data-modal=\"title\"]') || modalElement.querySelector('.modal-title');
        const bodyElement = modalElement.querySelector('[data-modal=\"body\"]') || modalElement.querySelector('.modal-body');
        const actionButton = modalElement.querySelector('[data-modal=\"action\"]');
        const closeButton = modalElement.querySelector('[data-modal=\"close\"]');
        const defaultCloseText = closeButton ? closeButton.textContent : 'Закрыть';

        return {
            show: ({title, message, actionText, onAction, closeText}) => {
                if (titleElement && title) {
                    titleElement.textContent = title;
                }
                if (bodyElement && message) {
                    bodyElement.textContent = message;
                }
                if (closeButton) {
                    closeButton.textContent = closeText || defaultCloseText;
                }
                if (actionButton) {
                    if (actionText) {
                        actionButton.textContent = actionText;
                        actionButton.classList.remove('d-none');
                        actionButton.onclick = () => {
                            modal.hide();
                            typeof onAction === 'function' && onAction();
                        };
                    } else {
                        actionButton.classList.add('d-none');
                        actionButton.onclick = null;
                    }
                }
                modal.show();
            },
            hide: () => modal.hide()
        };
    }

    function showReferenceError(error, modal) {
        if (!error) {
            return;
        }
        if (!handleAuthError(error, modal)) {
            modal?.show({
                title: 'Не удалось загрузить справочники',
                message: error.message || 'Попробуйте повторить попытку позже.'
            });
        }
    }

    function handleAuthError(error, modal) {
        if (!error) {
            return false;
        }
        if (error.code === 'UNAUTHORIZED' || error.status === 401 || error.status === 403) {
            modal && modal.show({
                title: 'Недостаточно прав',
                message: 'Проверьте авторизацию и повторите попытку.'
            });
            return true;
        }
        return false;
    }

    function applyDateColumnLayout(form, movementType) {
        if (!form) {
            return;
        }
        const container = form.querySelector('[data-field="date"]');
        if (!container) {
            return;
        }
        container.classList.add('col-12');
        const isTransfer = movementType === 'TRANSFER';
        if (isTransfer) {
            container.classList.add('col-sm-12');
            container.classList.remove('col-sm-6');
        } else {
            container.classList.add('col-sm-6');
            container.classList.remove('col-sm-12');
        }
    }

    async function apiRequest(url, options = {}) {
        const requestOptions = {
            method: options.method || 'GET',
            headers: Object.assign({}, options.headers || {}, {Accept: 'application/json'})
        };
        const token = localStorage.getItem('authToken');
        if (token) {
            requestOptions.headers.Authorization = `Bearer ${token}`;
        }
        if (options.body !== undefined) {
            requestOptions.body = options.body;
        }
        return fetch(url, requestOptions).then(async (response) => {
            if (!response.ok) {
                let message = '';
                try {
                    message = await response.text();
                } catch (ignored) {
                }
                console.warn('API request failed', {
                    url,
                    status: response.status,
                    statusText: response.statusText
                });
                const error = new Error(message || `Ошибка ${response.status}`);
                error.status = response.status;
                if (response.status === 401 || response.status === 403) {
                    error.code = 'UNAUTHORIZED';
                }
                throw error;
            }
            if (response.status === 204) {
                return null;
            }
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('application/json')) {
                return response.json();
            }
            return response.text();
        });
    }
})();
