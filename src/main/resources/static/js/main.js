(function () {
    const bootstrapRef = window.bootstrap;
    let toastContainerRef = null;

    const ensureToastContainer = () => {
        if (toastContainerRef) {
            return toastContainerRef;
        }
        toastContainerRef = document.getElementById('appToastContainer');
        if (!toastContainerRef) {
            toastContainerRef = document.createElement('div');
            toastContainerRef.id = 'appToastContainer';
            toastContainerRef.className = 'app-toast-container';
            document.body.appendChild(toastContainerRef);
        }
        return toastContainerRef;
    };

    const emitToast = (message, type = 'success') => {
        if (!message) {
            return;
        }
        const container = ensureToastContainer();
        const toast = document.createElement('div');
        toast.className = `app-toast app-toast--${type}`;
        toast.textContent = message;
        container.appendChild(toast);
        setTimeout(() => {
            toast.classList.add('app-toast--hide');
        }, 3000);
        setTimeout(() => {
            toast.remove();
        }, 3600);
    };

    window.showAppToast = emitToast;

    const bootstrapApp = () => {
        initLoginPage();
        initWarehouseListPage();
        initWarehouseDetailsPage();
        initCategoriesPage();
        initProductsPage();
        initCounterpartiesPage();
        initEmployeesPage();
        initReportsPage();
        initPhoneMasks();
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bootstrapApp);
    } else {
        bootstrapApp();
    }

    function initLoginPage() {
        const loginForm = document.getElementById('loginForm');
        if (!loginForm) {
            return;
        }

        const submitButton = document.getElementById('loginSubmit');
        const spinner = submitButton ? submitButton.querySelector('.spinner-border') : null;
        const errorModalElement = document.getElementById('errorModal');
        const errorModalBody = document.getElementById('errorModalBody');
        const errorModal = errorModalElement && bootstrapRef ? new bootstrapRef.Modal(errorModalElement) : null;
        const successModalElement = document.getElementById('successModal');
        const successModalBody = document.getElementById('successModalBody');
        const successModalAction = document.getElementById('successModalAction');
        const successModal = successModalElement && bootstrapRef ? new bootstrapRef.Modal(successModalElement) : null;
        const DASHBOARD_URL = '/dashboard';
        let successRedirectTimer = null;

        const toggleLoading = (state) => {
            if (!submitButton) {
                return;
            }
            submitButton.disabled = state;
            submitButton.classList.toggle('loading', state);
            if (spinner) {
                spinner.classList.toggle('d-none', !state);
            }
        };

        const redirectToDashboard = () => {
            window.location.href = DASHBOARD_URL;
        };

        const showErrorModal = (message) => {
            if (errorModalBody) {
                errorModalBody.textContent = message;
            }
            if (errorModal) {
                errorModal.show();
            }
        };

        const showSuccessModal = (message) => {
            if (successModalBody && message) {
                successModalBody.textContent = message;
            }
            if (successModal) {
                successModal.show();
                if (successRedirectTimer) {
                    clearTimeout(successRedirectTimer);
                }
                successRedirectTimer = window.setTimeout(redirectToDashboard, 3000);
            } else {
                redirectToDashboard();
            }
        };

        if (successModalAction) {
            successModalAction.addEventListener('click', () => {
                if (successRedirectTimer) {
                    clearTimeout(successRedirectTimer);
                }
                redirectToDashboard();
            });
        }

        loginForm.addEventListener('submit', async (event) => {
            event.preventDefault();

            if (!loginForm.checkValidity()) {
                loginForm.classList.add('was-validated');
                return;
            }

            const payload = {
                username: loginForm.username.value.trim(),
                password: loginForm.password.value
            };

            toggleLoading(true);

            try {
                const response = await fetch(loginForm.action, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(payload)
                });

                const contentType = response.headers.get('content-type') || '';

                if (!response.ok) {
                    const isAuthError = response.status === 401 || response.status === 403;
                    let message = isAuthError ? 'Неверный логин или пароль' : `Ошибка ${response.status}`;

                    if (!isAuthError && contentType.includes('application/json')) {
                        try {
                            const errorData = await response.json();
                            if (typeof errorData === 'string') {
                                message = errorData;
                            } else if (errorData.error) {
                                message = errorData.error;
                            } else {
                                const firstKey = Object.keys(errorData)[0];
                                if (firstKey) {
                                    const value = errorData[firstKey];
                                    message = Array.isArray(value) ? value[0] : value;
                                }
                            }
                        } catch (jsonError) {
                            console.warn('Не удалось разобрать тело ошибки', jsonError);
                        }
                    } else if (!isAuthError) {
                        const text = await response.text();
                        if (text) {
                            message = text;
                        }
                    }

                    throw new Error(message || 'Не удалось выполнить вход');
                }

                const data = contentType.includes('application/json') ? await response.json() : {};
                if (data && data.token) {
                    localStorage.setItem('authToken', data.token);
                }

                showSuccessModal('Авторизация выполнена успешно. Перенаправляем в панель управления.');
            } catch (error) {
                console.error('Ошибка авторизации', error);
                showErrorModal(error.message || 'Не удалось выполнить вход.');
            } finally {
                toggleLoading(false);
            }
        });
    }

    function initWarehouseListPage() {
        const pageRoot = document.getElementById('warehousesPage');
        if (!pageRoot) {
            return;
        }

        const grid = document.getElementById('warehousesGrid');
        const loader = document.getElementById('warehousesLoader');
        const emptyState = document.getElementById('warehousesEmptyState');
        const addForm = document.getElementById('addWarehouseForm');
        const addSubmitButton = document.getElementById('addWarehouseSubmit');
        const addSubmitSpinner = addSubmitButton ? addSubmitButton.querySelector('.spinner-border') : null;
        const addModalElement = document.getElementById('addWarehouseModal');
        const addModal = addModalElement && bootstrapRef ? new bootstrapRef.Modal(addModalElement) : null;
        const feedbackModal = createFeedbackModal('warehouseAlertModal');
        const backButton = document.getElementById('warehousesBackButton');

        if (backButton) {
            backButton.addEventListener('click', () => {
                window.location.href = '/dashboard';
            });
        }

        const toggleLoader = (state) => {
            if (loader) {
                loader.classList.toggle('d-none', !state);
            }
            if (grid) {
                grid.setAttribute('aria-busy', state ? 'true' : 'false');
            }
            if (state && emptyState) {
                emptyState.classList.add('d-none');
            }
        };

        const renderWarehouses = (items) => {
            if (!grid) {
                return;
            }

            grid.innerHTML = '';

            if (!items.length) {
                if (emptyState) {
                    emptyState.classList.remove('d-none');
                }
                return;
            }

            const fragment = document.createDocumentFragment();
            items.forEach((warehouse) => {
                const info = typeof warehouse.info === 'string' ? warehouse.info.trim() : '';
                const detailsUrl = `/warehouse/${warehouse.id}`;
                const col = document.createElement('div');
                col.className = 'col-12 col-lg-6';
                col.innerHTML = `
                    <a class="warehouse-card d-flex gap-3 align-items-start h-100 text-decoration-none text-dark"
                       href="${detailsUrl}">
                        <div class="warehouse-icon flex-shrink-0">
                            <i class="bi bi-buildings-fill"></i>
                        </div>
                        <div class="d-flex flex-column gap-3 flex-grow-1">
                            <div>
                                <h3 class="h5 mb-2">${escapeHtml(warehouse.name || 'Без названия')}</h3>
                                ${info ? `<p class="mb-0">${escapeHtml(info)}</p>` : ''}
                            </div>
                            <div>
                                <span class="btn btn-dark btn-sm d-inline-flex align-items-center gap-2">
                                    <span>Подробнее</span>
                                    <i class="bi bi-arrow-right"></i>
                                </span>
                            </div>
                        </div>
                    </a>
                `;
                fragment.appendChild(col);
            });

            grid.appendChild(fragment);
        };

        const loadWarehouses = async () => {
            toggleLoader(true);
            try {
                const data = await apiRequest('/warehouses');
                renderWarehouses(Array.isArray(data) ? data : []);
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось загрузить склады',
                        message: error.message || 'Попробуйте обновить страницу позже.'
                    });
                }
            } finally {
                toggleLoader(false);
            }
        };

        const toggleAddLoading = (state) => {
            if (addSubmitButton) {
                addSubmitButton.disabled = state;
            }
            if (addSubmitSpinner) {
                addSubmitSpinner.classList.toggle('d-none', !state);
            }
        };

        const resetAddForm = () => {
            if (!addForm) {
                return;
            }
            addForm.reset();
            addForm.classList.remove('was-validated');
        };

        if (addForm) {
            addForm.addEventListener('submit', async (event) => {
                event.preventDefault();

                if (!addForm.checkValidity()) {
                    addForm.classList.add('was-validated');
                    return;
                }

                const trimmedName = addForm.name.value.trim();
                if (!trimmedName) {
                    addForm.name.value = '';
                    addForm.classList.add('was-validated');
                    return;
                }
                const payload = {
                    name: trimmedName,
                    info: addForm.info.value.trim() || null
                };

                toggleAddLoading(true);

                try {
                    await apiRequest('/warehouses', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(payload)
                    });
                    if (addModal) {
                        addModal.hide();
                    }
                    resetAddForm();
                    emitToast('Склад добавлен');
                    loadWarehouses();
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось добавить склад',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleAddLoading(false);
                }
            });
        }

        loadWarehouses();
    }

    function initWarehouseDetailsPage() {
        const root = document.getElementById('warehouseDetailsRoot');
        if (!root) {
            return;
        }

        const summaryLoader = document.getElementById('warehouseDetailsLoader');
        const summaryContent = document.getElementById('warehouseSummaryContent');
        const titleElement = document.getElementById('warehouseTitle');
        const descriptionElement = document.getElementById('warehouseDescription');
        const idElement = document.getElementById('warehouseIdValue');
        const refreshButton = document.getElementById('warehouseRefreshButton');
        const editButton = document.getElementById('warehouseEditButton');
        const deleteButton = document.getElementById('warehouseDeleteButton');
        const productsLoader = document.getElementById('warehouseProductsLoader');
        const productsEmptyState = document.getElementById('warehouseProductsEmpty');
        const productsTableWrapper = document.getElementById('warehouseProductsTableWrapper');
        const productsTableBody = document.getElementById('warehouseProductsTableBody');
        const editModalElement = document.getElementById('editWarehouseModal');
        const editForm = document.getElementById('editWarehouseForm');
        const editModal = editModalElement && bootstrapRef ? new bootstrapRef.Modal(editModalElement) : null;
        const editSubmitButton = document.getElementById('editWarehouseSubmit');
        const editSubmitSpinner = editSubmitButton ? editSubmitButton.querySelector('.spinner-border') : null;
        const feedbackModal = createFeedbackModal('warehouseAlertModal');
        const warehouseId = extractWarehouseIdFromLocation();
        let currentWarehouse = null;

        if (!warehouseId) {
            if (summaryLoader) {
                summaryLoader.classList.add('d-none');
            }
            if (descriptionElement) {
                descriptionElement.textContent = 'Некорректный идентификатор склада.';
                descriptionElement.classList.remove('d-none');
            }
            feedbackModal.show({
                title: 'Склад не найден',
                message: 'Проверьте адрес страницы или выберите склад из списка.',
                actionText: 'К списку складов',
                onAction: () => {
                    window.location.href = '/warehouses/page';
                }
            });
            return;
        }

        const toggleSummaryLoading = (state) => {
            if (summaryLoader) {
                summaryLoader.classList.toggle('d-none', !state);
            }
            if (summaryContent) {
                summaryContent.classList.toggle('d-none', state);
            }
            if (refreshButton) {
                refreshButton.disabled = state;
            }
            if (editButton) {
                editButton.disabled = state;
            }
            if (deleteButton) {
                deleteButton.disabled = state;
            }
        };

        const beginProductsLoading = () => {
            if (productsLoader) {
                productsLoader.classList.remove('d-none');
            }
            if (productsTableWrapper) {
                productsTableWrapper.classList.add('d-none');
            }
            if (productsEmptyState) {
                productsEmptyState.classList.add('d-none');
            }
        };

        const endProductsLoading = () => {
            if (productsLoader) {
                productsLoader.classList.add('d-none');
            }
        };

        const updateSummary = (warehouse) => {
            currentWarehouse = warehouse || null;
            const safeName = warehouse?.name ? warehouse.name : `Склад #${warehouseId}`;
            const info = typeof warehouse?.info === 'string' ? warehouse.info.trim() : '';

            if (titleElement) {
                titleElement.textContent = safeName;
            }
            if (idElement) {
                idElement.textContent = warehouse?.id ?? warehouseId;
            }
            if (descriptionElement) {
                if (info) {
                    descriptionElement.textContent = info;
                    descriptionElement.classList.remove('d-none');
                } else {
                    descriptionElement.textContent = '';
                    descriptionElement.classList.add('d-none');
                }
            }
        };

        const populateEditForm = () => {
            if (!editForm || !currentWarehouse) {
                return;
            }
            editForm.name.value = currentWarehouse.name || '';
            editForm.info.value = currentWarehouse.info || '';
            editForm.classList.remove('was-validated');
        };

        const toggleEditLoading = (state) => {
            if (editSubmitButton) {
                editSubmitButton.disabled = state;
            }
            if (editSubmitSpinner) {
                editSubmitSpinner.classList.toggle('d-none', !state);
            }
        };

        const renderProducts = (items) => {
            if (!productsTableBody || !productsTableWrapper || !productsEmptyState) {
                return;
            }
            productsTableBody.innerHTML = '';
            if (!items.length) {
                productsEmptyState.classList.remove('d-none');
                productsTableWrapper.classList.add('d-none');
                return;
            }
            productsEmptyState.classList.add('d-none');
            productsTableWrapper.classList.remove('d-none');
            const fragment = document.createDocumentFragment();
            items.forEach((item) => {
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${item.productId ?? '—'}</td>
                    <td>${escapeHtml(item.name || 'Без названия')}</td>
                    <td class="text-end">${item.quantity ?? 0}</td>
                `;
                fragment.appendChild(row);
            });
            productsTableBody.appendChild(fragment);
        };

        const loadProducts = async () => {
            beginProductsLoading();
            try {
                const data = await apiRequest(`/warehouses/${warehouseId}/products`);
                renderProducts(Array.isArray(data) ? data : []);
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось загрузить товары',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            } finally {
                endProductsLoading();
            }
        };

        const handleDeleteWarehouse = async () => {
            toggleSummaryLoading(true);
            try {
                await apiRequest(`/warehouses/${warehouseId}`, {
                    method: 'DELETE'
                });
                emitToast('Склад удалён');
                window.location.href = '/warehouses/page';
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось удалить склад',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            } finally {
                toggleSummaryLoading(false);
            }
        };

        const fetchWarehouse = async () => {
            toggleSummaryLoading(true);
            try {
                const data = await apiRequest(`/warehouses/${warehouseId}`);
                updateSummary(data || {});
                return true;
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    if (error.status === 404) {
                        if (descriptionElement) {
                            descriptionElement.textContent = 'Склад не найден или был удалён.';
                            descriptionElement.classList.remove('d-none');
                        }
                        feedbackModal.show({
                            title: 'Склад не найден',
                            message: 'Похоже, склад был удалён. Вернитесь к списку и выберите другой склад.',
                            actionText: 'К списку складов',
                            onAction: () => {
                                window.location.href = '/warehouses/page';
                            }
                        });
                    } else {
                        feedbackModal.show({
                            title: 'Не удалось загрузить склад',
                            message: error.message || 'Попробуйте повторить попытку позже.',
                            actionText: 'Повторить попытку',
                            onAction: () => {
                                fetchWarehouse();
                            }
                        });
                    }
                }
                return false;
            } finally {
                toggleSummaryLoading(false);
            }
        };

        if (refreshButton) {
            refreshButton.addEventListener('click', () => {
                fetchWarehouse().then((loaded) => {
                    if (loaded) {
                        loadProducts();
                    }
                });
            });
        }

        if (editButton && editModal && editForm) {
            editButton.addEventListener('click', () => {
                if (!currentWarehouse) {
                    return;
                }
                populateEditForm();
                editModal.show();
            });
        }

        if (deleteButton) {
            deleteButton.addEventListener('click', () => {
                if (!currentWarehouse) {
                    return;
                }
                feedbackModal.show({
                    title: 'Удалить склад?',
                    message: `Склад "${currentWarehouse.name || `#${warehouseId}`}" будет удалён безвозвратно.`,
                    actionText: 'Удалить',
                    closeText: 'Отмена',
                    onAction: () => {
                        handleDeleteWarehouse();
                    }
                });
            });
        }

        if (editForm) {
            editForm.addEventListener('submit', async (event) => {
                event.preventDefault();

                if (!editForm.checkValidity()) {
                    editForm.classList.add('was-validated');
                    return;
                }

                if (!currentWarehouse) {
                    return;
                }

                const trimmedName = editForm.name.value.trim();
                if (!trimmedName) {
                    editForm.name.value = '';
                    editForm.classList.add('was-validated');
                    return;
                }

                const payload = {
                    name: trimmedName,
                    info: editForm.info.value.trim() || null
                };

                toggleEditLoading(true);
                try {
                    const updated = await apiRequest(`/warehouses/${warehouseId}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(payload)
                    });
                    updateSummary(updated || payload);
                    if (editModal) {
                        editModal.hide();
                    }
                    emitToast('Информация о складе обновлена');
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось обновить склад',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleEditLoading(false);
                }
            });
        }

        fetchWarehouse().then((loaded) => {
            if (loaded) {
                loadProducts();
            }
        });
    }

    function initCategoriesPage() {
        const root = document.getElementById('categoriesPage');
        if (!root) {
            return;
        }

        const loader = document.getElementById('categoriesLoader');
        const tableWrapper = document.getElementById('categoriesTableWrapper');
        const tableBody = document.getElementById('categoriesTableBody');
        const emptyState = document.getElementById('categoriesEmptyState');
        const addForm = document.getElementById('addCategoryForm');
        const editForm = document.getElementById('editCategoryForm');
        const addModalElement = document.getElementById('addCategoryModal');
        const editModalElement = document.getElementById('editCategoryModal');
        const addModal = addModalElement && bootstrapRef ? new bootstrapRef.Modal(addModalElement) : null;
        const editModal = editModalElement && bootstrapRef ? new bootstrapRef.Modal(editModalElement) : null;
        const addSubmitButton = document.getElementById('addCategorySubmit');
        const addSubmitSpinner = addSubmitButton ? addSubmitButton.querySelector('.spinner-border') : null;
        const editSubmitButton = document.getElementById('editCategorySubmit');
        const editSubmitSpinner = editSubmitButton ? editSubmitButton.querySelector('.spinner-border') : null;
        const feedbackModal = createFeedbackModal('managementAlertModal');
        let categories = [];
        let editingId = null;

        const toggleLoader = (state) => {
            if (loader) {
                loader.classList.toggle('d-none', !state);
            }
            if (tableWrapper) {
                tableWrapper.classList.toggle('d-none', state);
            }
            if (!state && !categories.length && emptyState) {
                emptyState.classList.remove('d-none');
            } else if (emptyState) {
                emptyState.classList.add('d-none');
            }
        };

        const renderCategories = () => {
            if (!tableBody) {
                return;
            }
            if (!categories.length) {
                tableBody.innerHTML = '';
                if (tableWrapper) {
                    tableWrapper.classList.add('d-none');
                }
                if (emptyState) {
                    emptyState.classList.remove('d-none');
                }
                return;
            }

            if (tableWrapper) {
                tableWrapper.classList.remove('d-none');
            }
            if (emptyState) {
                emptyState.classList.add('d-none');
            }

            const fragment = document.createDocumentFragment();
            categories.forEach((category) => {
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${category.id}</td>
                    <td>${escapeHtml(category.name || '')}</td>
                    <td class="text-end">
                        <div class="table-actions">
                            <button type="button" class="btn-icon" data-action="edit" data-id="${category.id}" title="Редактировать">
                                <i class="bi bi-pencil"></i>
                            </button>
                            <button type="button" class="btn-icon" data-action="delete" data-id="${category.id}" title="Удалить">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                `;
                fragment.appendChild(row);
            });
            tableBody.innerHTML = '';
            tableBody.appendChild(fragment);

            tableBody.querySelectorAll('[data-action="edit"]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = Number(button.getAttribute('data-id'));
                    startEditCategory(id);
                });
            });
            tableBody.querySelectorAll('[data-action="delete"]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = Number(button.getAttribute('data-id'));
                    const category = categories.find((item) => item.id === id);
                    if (!category) {
                        return;
                    }
                    feedbackModal.show({
                        title: 'Удалить категорию?',
                        message: `Категория "${category.name}" будет удалена.`,
                        actionText: 'Удалить',
                        closeText: 'Отмена',
                        onAction: () => deleteCategory(id)
                    });
                });
            });
        };

        const loadCategories = async () => {
            toggleLoader(true);
            try {
                const data = await apiRequest('/categories');
                categories = Array.isArray(data) ? data : [];
                renderCategories();
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось загрузить категории',
                        message: error.message || 'Попробуйте обновить страницу позже.'
                    });
                }
            } finally {
                toggleLoader(false);
            }
        };

        const toggleFormLoading = (button, spinner, state) => {
            if (button) {
                button.disabled = state;
            }
            if (spinner) {
                spinner.classList.toggle('d-none', !state);
            }
        };

        const resetAddForm = () => {
            if (addForm) {
                addForm.reset();
                addForm.classList.remove('was-validated');
            }
        };

        const startEditCategory = (id) => {
            const category = categories.find((item) => item.id === id);
            if (!category || !editForm) {
                return;
            }
            editingId = id;
            editForm.name.value = category.name || '';
            editForm.classList.remove('was-validated');
            if (editModal) {
                editModal.show();
            }
        };

        const deleteCategory = async (id) => {
            try {
                await apiRequest(`/categories/${id}`, {method: 'DELETE'});
                categories = categories.filter((category) => category.id !== id);
                renderCategories();
                emitToast('Категория удалена');
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось удалить категорию',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            }
        };

        if (addForm) {
            addForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!addForm.checkValidity()) {
                    addForm.classList.add('was-validated');
                    return;
                }
                const name = addForm.name.value.trim();
                if (!name) {
                    addForm.name.value = '';
                    addForm.classList.add('was-validated');
                    return;
                }
                toggleFormLoading(addSubmitButton, addSubmitSpinner, true);
                try {
                    const created = await apiRequest('/categories', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({name})
                    });
                    categories.push(created);
                    renderCategories();
                    if (addModal) {
                        addModal.hide();
                    }
                    resetAddForm();
                    emitToast('Категория добавлена');
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось сохранить категорию',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleFormLoading(addSubmitButton, addSubmitSpinner, false);
                }
            });
        }

        if (editForm) {
            editForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!editForm.checkValidity()) {
                    editForm.classList.add('was-validated');
                    return;
                }
                if (!editingId) {
                    return;
                }
                const name = editForm.name.value.trim();
                if (!name) {
                    editForm.name.value = '';
                    editForm.classList.add('was-validated');
                    return;
                }
                toggleFormLoading(editSubmitButton, editSubmitSpinner, true);
                try {
                    const updated = await apiRequest(`/categories/${editingId}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({name})
                    });
                    categories = categories.map((category) => category.id === editingId ? updated : category);
                    renderCategories();
                    if (editModal) {
                        editModal.hide();
                    }
                    editingId = null;
                    emitToast('Категория обновлена');
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось обновить категорию',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleFormLoading(editSubmitButton, editSubmitSpinner, false);
                }
            });
        }

        loadCategories();
    }

    function initProductsPage() {
        const root = document.getElementById('productsPage');
        if (!root) {
            return;
        }

        const loader = document.getElementById('productsLoader');
        const tableWrapper = document.getElementById('productsTableWrapper');
        const tableBody = document.getElementById('productsTableBody');
        const emptyState = document.getElementById('productsEmptyState');
        const addForm = document.getElementById('addProductForm');
        const editForm = document.getElementById('editProductForm');
        const addModalElement = document.getElementById('addProductModal');
        const editModalElement = document.getElementById('editProductModal');
        const addModal = addModalElement && bootstrapRef ? new bootstrapRef.Modal(addModalElement) : null;
        const editModal = editModalElement && bootstrapRef ? new bootstrapRef.Modal(editModalElement) : null;
        const addSubmitButton = document.getElementById('addProductSubmit');
        const addSubmitSpinner = addSubmitButton ? addSubmitButton.querySelector('.spinner-border') : null;
        const editSubmitButton = document.getElementById('editProductSubmit');
        const editSubmitSpinner = editSubmitButton ? editSubmitButton.querySelector('.spinner-border') : null;
        const addCategorySelect = document.getElementById('addProductCategory');
        const editCategorySelect = document.getElementById('editProductCategory');
        const feedbackModal = createFeedbackModal('productsAlertModal');
        let products = [];
        let categories = [];
        let editingId = null;

        const toggleLoader = (state) => {
            if (loader) {
                loader.classList.toggle('d-none', !state);
            }
            if (tableWrapper) {
                tableWrapper.classList.toggle('d-none', state);
            }
            if (!state && !products.length && emptyState) {
                emptyState.classList.remove('d-none');
            } else if (emptyState) {
                emptyState.classList.add('d-none');
            }
        };

        const populateCategorySelects = () => {
            const optionsHtml = ['<option value="" disabled selected>Выберите категорию</option>'];
            categories.forEach((category) => {
                optionsHtml.push(`<option value="${category.id}">${escapeHtml(category.name || '')}</option>`);
            });
            if (addCategorySelect) {
                addCategorySelect.innerHTML = optionsHtml.join('');
            }
            if (editCategorySelect) {
                editCategorySelect.innerHTML = ['<option value="" disabled>Выберите категорию</option>', ...optionsHtml.slice(1)].join('');
            }
        };

        const loadCategoriesOptions = async () => {
            try {
                const data = await apiRequest('/categories');
                categories = Array.isArray(data) ? data : [];
                populateCategorySelects();
            } catch (error) {
                console.warn('Не удалось загрузить категории для товаров', error);
            }
        };

        const renderProducts = () => {
            if (!tableBody) {
                return;
            }
            if (!products.length) {
                tableBody.innerHTML = '';
                if (tableWrapper) {
                    tableWrapper.classList.add('d-none');
                }
                if (emptyState) {
                    emptyState.classList.remove('d-none');
                }
                return;
            }

            if (tableWrapper) {
                tableWrapper.classList.remove('d-none');
            }
            if (emptyState) {
                emptyState.classList.add('d-none');
            }

            const fragment = document.createDocumentFragment();
            products.forEach((product) => {
                const categoryName = product.category && product.category.name ? product.category.name : '—';
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${product.id}</td>
                    <td>${escapeHtml(product.name || '')}</td>
                    <td>${escapeHtml(categoryName)}</td>
                    <td class="text-end">
                        <div class="table-actions">
                            <button type="button" class="btn-icon" data-action="edit" data-id="${product.id}" title="Редактировать">
                                <i class="bi bi-pencil"></i>
                            </button>
                            <button type="button" class="btn-icon" data-action="delete" data-id="${product.id}" title="Удалить">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                `;
                fragment.appendChild(row);
            });
            tableBody.innerHTML = '';
            tableBody.appendChild(fragment);

            tableBody.querySelectorAll('[data-action="edit"]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = Number(button.getAttribute('data-id'));
                    startEditProduct(id);
                });
            });
            tableBody.querySelectorAll('[data-action="delete"]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = Number(button.getAttribute('data-id'));
                    const product = products.find((item) => item.id === id);
                    if (!product) {
                        return;
                    }
                    feedbackModal.show({
                        title: 'Удалить товар?',
                        message: `Товар "${product.name}" будет удалён.`,
                        actionText: 'Удалить',
                        closeText: 'Отмена',
                        onAction: () => deleteProduct(id)
                    });
                });
            });
        };

        const loadProducts = async () => {
            toggleLoader(true);
            try {
                const data = await apiRequest('/products');
                products = Array.isArray(data) ? data : [];
                renderProducts();
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось загрузить товары',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            } finally {
                toggleLoader(false);
            }
        };

        const toggleFormLoading = (button, spinner, state) => {
            if (button) {
                button.disabled = state;
            }
            if (spinner) {
                spinner.classList.toggle('d-none', !state);
            }
        };

        const resetAddForm = () => {
            if (addForm) {
                addForm.reset();
                addForm.classList.remove('was-validated');
            }
        };

        const startEditProduct = (id) => {
            const product = products.find((item) => item.id === id);
            if (!product || !editForm) {
                return;
            }
            editingId = id;
            editForm.name.value = product.name || '';
            if (editCategorySelect) {
                const categoryId = product.category && product.category.id ? product.category.id : '';
                editCategorySelect.value = categoryId;
            }
            editForm.classList.remove('was-validated');
            if (editModal) {
                editModal.show();
            }
        };

        const deleteProduct = async (id) => {
            try {
                await apiRequest(`/products/${id}`, {method: 'DELETE'});
                products = products.filter((product) => product.id !== id);
                renderProducts();
                emitToast('Товар удалён');
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось удалить товар',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            }
        };

        if (addForm) {
            addForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!addForm.checkValidity()) {
                    addForm.classList.add('was-validated');
                    return;
                }
                const name = addForm.name.value.trim();
                const categoryId = addForm.categoryId.value;
                if (!name || !categoryId) {
                    addForm.classList.add('was-validated');
                    return;
                }
                toggleFormLoading(addSubmitButton, addSubmitSpinner, true);
                try {
                    const payload = {
                        name,
                        info: null,
                        category: {id: Number(categoryId)}
                    };
                    const created = await apiRequest('/products', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(payload)
                    });
                    products.push(created);
                    renderProducts();
                    if (addModal) {
                        addModal.hide();
                    }
                    resetAddForm();
                    emitToast('Товар добавлен');
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось сохранить товар',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleFormLoading(addSubmitButton, addSubmitSpinner, false);
                }
            });
        }

        if (editForm) {
            editForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!editForm.checkValidity()) {
                    editForm.classList.add('was-validated');
                    return;
                }
                if (!editingId) {
                    return;
                }
                const name = editForm.name.value.trim();
                const categoryId = editForm.categoryId.value;
                if (!name || !categoryId) {
                    editForm.classList.add('was-validated');
                    return;
                }
                toggleFormLoading(editSubmitButton, editSubmitSpinner, true);
                try {
                    const payload = {
                        name,
                        info: null,
                        category: {id: Number(categoryId)}
                    };
                    const updated = await apiRequest(`/products/${editingId}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(payload)
                    });
                    products = products.map((product) => product.id === editingId ? updated : product);
                    renderProducts();
                    if (editModal) {
                        editModal.hide();
                    }
                    editingId = null;
                    emitToast('Товар обновлён');
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось обновить товар',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleFormLoading(editSubmitButton, editSubmitSpinner, false);
                }
            });
        }

        loadCategoriesOptions().then(loadProducts);
    }

    function initReportsPage() {
        const root = document.getElementById('reportsPage');
        if (!root) {
            return;
        }

        const warehouseSelect = document.getElementById('reportWarehouseSelect');
        const categorySelect = document.getElementById('reportCategorySelect');
        const form = document.getElementById('reportFilterForm');
        const downloadButton = document.getElementById('reportDownloadBtn');
        const downloadSpinner = downloadButton ? downloadButton.querySelector('.spinner-border') : null;
        const resetButton = document.getElementById('reportResetBtn');
        const reportDateInput = document.getElementById('reportDate');
        const feedbackModal = createFeedbackModal('reportsAlertModal');
        const toast = window.showAppToast || (() => undefined);

        const toggleLoading = (state) => {
            if (!downloadButton) {
                return;
            }
            downloadButton.disabled = state;
            if (downloadSpinner) {
                downloadSpinner.classList.toggle('d-none', !state);
            }
        };

        const loadReferences = async () => {
            try {
                const [warehouses, categories] = await Promise.all([
                    apiRequest('/warehouses'),
                    apiRequest('/categories')
                ]);
                populateMultiSelect(warehouseSelect, warehouses);
                populateMultiSelect(categorySelect, categories);
            } catch (error) {
                showReferenceError(error, feedbackModal);
            }
        };

        const populateMultiSelect = (select, items) => {
            if (!select) {
                return;
            }
            const options = items && items.length
                ? items
                    .slice()
                    .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
                    .map((item) => `<option value="${item.id}">${escapeHtml(item.name || '')}</option>`)
                : ['<option disabled>Нет данных</option>'];
            select.innerHTML = options.join('');
        };

        const collectSelected = (select) => {
            if (!select) {
                return [];
            }
            return Array.from(select.selectedOptions || [])
                .map((option) => Number(option.value))
                .filter((value) => !Number.isNaN(value));
        };

        form?.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!localStorage.getItem('authToken')) {
                window.location.href = '/login';
                return;
            }
            toggleLoading(true);
            const payload = {
                reportDate: reportDateInput?.value || null,
                warehouseIds: collectSelected(warehouseSelect),
                categoryIds: collectSelected(categorySelect)
            };
            try {
                const response = await fetch('/reports/stock', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/pdf',
                        'Authorization': `Bearer ${localStorage.getItem('authToken') || ''}`
                    },
                    body: JSON.stringify(payload)
                });
                if (!response.ok) {
                    const message = await response.text();
                    const error = new Error(message || `Ошибка ${response.status}`);
                    error.status = response.status;
                    throw error;
                }
                const blob = await response.blob();
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                const timestamp = new Date().toISOString().replace(/[:T]/g, '').slice(0, 13);
                link.href = url;
                link.download = `stock-report-${timestamp}.pdf`;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                URL.revokeObjectURL(url);
                toast('Отчёт сформирован');
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось сформировать отчёт',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            } finally {
                toggleLoading(false);
            }
        });

        resetButton?.addEventListener('click', () => {
            reportDateInput && (reportDateInput.value = '');
            [warehouseSelect, categorySelect].forEach((select) => {
                if (select) {
                    Array.from(select.options).forEach((option) => {
                        option.selected = false;
                    });
                }
            });
        });

        loadReferences();
    }

    function initCounterpartiesPage() {
        const root = document.getElementById('counterpartiesPage');
        if (!root) {
            return;
        }

        const loader = document.getElementById('counterpartiesLoader');
        const tableWrapper = document.getElementById('counterpartiesTableWrapper');
        const tableBody = document.getElementById('counterpartiesTableBody');
        const emptyState = document.getElementById('counterpartiesEmptyState');
        const addForm = document.getElementById('addCounterpartyForm');
        const editForm = document.getElementById('editCounterpartyForm');
        const addModalElement = document.getElementById('addCounterpartyModal');
        const editModalElement = document.getElementById('editCounterpartyModal');
        const viewModalElement = document.getElementById('viewCounterpartyModal');
        const addModal = addModalElement && bootstrapRef ? new bootstrapRef.Modal(addModalElement) : null;
        const editModal = editModalElement && bootstrapRef ? new bootstrapRef.Modal(editModalElement) : null;
        const viewModal = viewModalElement && bootstrapRef ? new bootstrapRef.Modal(viewModalElement) : null;
        const addSubmitButton = document.getElementById('addCounterpartySubmit');
        const addSubmitSpinner = addSubmitButton ? addSubmitButton.querySelector('.spinner-border') : null;
        const editSubmitButton = document.getElementById('editCounterpartySubmit');
        const editSubmitSpinner = editSubmitButton ? editSubmitButton.querySelector('.spinner-border') : null;
        const viewName = document.getElementById('viewCounterpartyName');
        const viewPhone = document.getElementById('viewCounterpartyPhone');
        const viewInfo = document.getElementById('viewCounterpartyInfo');
        const viewEditButton = document.getElementById('viewCounterpartyEditButton');
        const viewDeleteButton = document.getElementById('viewCounterpartyDeleteButton');
        const feedbackModal = createFeedbackModal('counterpartyAlertModal');
        let counterparties = [];
        let editingId = null;
        let viewingId = null;

        const toggleLoader = (state) => {
            if (loader) {
                loader.classList.toggle('d-none', !state);
            }
            if (tableWrapper) {
                tableWrapper.classList.toggle('d-none', state);
            }
            if (!state && !counterparties.length && emptyState) {
                emptyState.classList.remove('d-none');
            } else if (emptyState) {
                emptyState.classList.add('d-none');
            }
        };

        const renderCounterparties = () => {
            if (!tableBody) {
                return;
            }
            if (!counterparties.length) {
                tableBody.innerHTML = '';
                if (tableWrapper) {
                    tableWrapper.classList.add('d-none');
                }
                if (emptyState) {
                    emptyState.classList.remove('d-none');
                }
                return;
            }

            if (tableWrapper) {
                tableWrapper.classList.remove('d-none');
            }
            if (emptyState) {
                emptyState.classList.add('d-none');
            }

            const fragment = document.createDocumentFragment();
            counterparties.forEach((counterparty) => {
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${counterparty.id}</td>
                    <td>${escapeHtml(counterparty.name || '')}</td>
                    <td>${escapeHtml(formatPhoneNumber(counterparty.phone))}</td>
                    <td class="text-end">
                        <div class="table-actions">
                            <button type="button" class="btn-icon" data-action="view" data-id="${counterparty.id}" title="Просмотр">
                                <i class="bi bi-eye"></i>
                            </button>
                            <button type="button" class="btn-icon" data-action="edit" data-id="${counterparty.id}" title="Редактировать">
                                <i class="bi bi-pencil"></i>
                            </button>
                            <button type="button" class="btn-icon" data-action="delete" data-id="${counterparty.id}" title="Удалить">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                `;
                fragment.appendChild(row);
            });
            tableBody.innerHTML = '';
            tableBody.appendChild(fragment);

            tableBody.querySelectorAll('[data-action="view"]').forEach((button) => {
                button.addEventListener('click', () => {
                    openCounterpartyView(Number(button.getAttribute('data-id')));
                });
            });
            tableBody.querySelectorAll('[data-action="edit"]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = Number(button.getAttribute('data-id'));
                    startEditCounterparty(id);
                });
            });
            tableBody.querySelectorAll('[data-action="delete"]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = Number(button.getAttribute('data-id'));
                    const counterparty = counterparties.find((item) => item.id === id);
                    if (!counterparty) {
                        return;
                    }
                    feedbackModal.show({
                        title: 'Удалить контрагента?',
                        message: `Контрагент "${counterparty.name}" будет удалён.`,
                        actionText: 'Удалить',
                        closeText: 'Отмена',
                        onAction: () => deleteCounterparty(id)
                    });
                });
            });
        };

        const loadCounterparties = async () => {
            toggleLoader(true);
            try {
                const data = await apiRequest('/counterparties');
                counterparties = Array.isArray(data) ? data : [];
                renderCounterparties();
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось загрузить контрагентов',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            } finally {
                toggleLoader(false);
            }
        };

        const toggleFormLoading = (button, spinner, state) => {
            if (button) {
                button.disabled = state;
            }
            if (spinner) {
                spinner.classList.toggle('d-none', !state);
            }
        };

        const resetAddForm = () => {
            if (addForm) {
                addForm.reset();
                addForm.classList.remove('was-validated');
            }
        };

        const startEditCounterparty = (id) => {
            const counterparty = counterparties.find((item) => item.id === id);
            if (!counterparty || !editForm) {
                return;
            }
            editingId = id;
            editForm.name.value = counterparty.name || '';
            editForm.phone.value = counterparty.phone || '';
            editForm.info.value = counterparty.info || '';
            editForm.classList.remove('was-validated');
            if (editModal) {
                editModal.show();
            }
        };

        const openCounterpartyView = (id) => {
            const counterparty = counterparties.find((item) => item.id === id);
            if (!counterparty || !viewName || !viewPhone || !viewInfo || !viewModal) {
                return;
            }
            viewingId = id;
            viewName.textContent = counterparty.name || '—';
            viewPhone.textContent = formatPhoneNumber(counterparty.phone);
            viewInfo.textContent = counterparty.info || '—';
            viewModal.show();
        };

        const deleteCounterparty = async (id) => {
            try {
                await apiRequest(`/counterparties/${id}`, {method: 'DELETE'});
                counterparties = counterparties.filter((counterparty) => counterparty.id !== id);
                renderCounterparties();
                emitToast('Контрагент удалён');
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось удалить контрагента',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            }
        };

        if (addForm) {
            addForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!addForm.checkValidity()) {
                    addForm.classList.add('was-validated');
                    return;
                }
                const name = addForm.name.value.trim();
                const phone = addForm.phone.value.trim();
                const info = addForm.info.value.trim() || null;
                if (!name || !phone) {
                    addForm.classList.add('was-validated');
                    return;
                }
                toggleFormLoading(addSubmitButton, addSubmitSpinner, true);
                try {
                    const payload = {name, phone, info};
                    const created = await apiRequest('/counterparties', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(payload)
                    });
                    counterparties.push(created);
                    renderCounterparties();
                    if (addModal) {
                        addModal.hide();
                    }
                    resetAddForm();
                    emitToast('Контрагент добавлен');
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось сохранить контрагента',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleFormLoading(addSubmitButton, addSubmitSpinner, false);
                }
            });
        }

        if (editForm) {
            editForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!editForm.checkValidity()) {
                    editForm.classList.add('was-validated');
                    return;
                }
                if (!editingId) {
                    return;
                }
                const name = editForm.name.value.trim();
                const phone = editForm.phone.value.trim();
                const info = editForm.info.value.trim() || null;
                if (!name || !phone) {
                    editForm.classList.add('was-validated');
                    return;
                }
                toggleFormLoading(editSubmitButton, editSubmitSpinner, true);
                try {
                    const payload = {name, phone, info};
                    const updated = await apiRequest(`/counterparties/${editingId}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(payload)
                    });
                    counterparties = counterparties.map((counterparty) => counterparty.id === editingId ? updated : counterparty);
                    renderCounterparties();
                    if (editModal) {
                        editModal.hide();
                    }
                    editingId = null;
                    emitToast('Контрагент обновлён');
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось обновить контрагента',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleFormLoading(editSubmitButton, editSubmitSpinner, false);
                }
            });
        }

        if (viewEditButton) {
            viewEditButton.addEventListener('click', () => {
                if (viewingId) {
                    startEditCounterparty(viewingId);
                }
            });
        }

        if (viewDeleteButton) {
            viewDeleteButton.addEventListener('click', () => {
                if (!viewingId) {
                    return;
                }
                const counterparty = counterparties.find((item) => item.id === viewingId);
                if (!counterparty) {
                    return;
                }
                feedbackModal.show({
                    title: 'Удалить контрагента?',
                    message: `Контрагент "${counterparty.name}" будет удалён.`,
                    actionText: 'Удалить',
                    closeText: 'Отмена',
                    onAction: () => deleteCounterparty(viewingId)
                });
            });
        }

        loadCounterparties();
    }

    function initEmployeesPage() {
        const root = document.getElementById('employeesPage');
        if (!root) {
            return;
        }

        const loader = document.getElementById('employeesLoader');
        const tableWrapper = document.getElementById('employeesTableWrapper');
        const tableBody = document.getElementById('employeesTableBody');
        const emptyState = document.getElementById('employeesEmptyState');
        const addForm = document.getElementById('addEmployeeForm');
        const editForm = document.getElementById('editEmployeeForm');
        const addModalElement = document.getElementById('addEmployeeModal');
        const editModalElement = document.getElementById('editEmployeeModal');
        const viewModalElement = document.getElementById('viewEmployeeModal');
        const addModal = addModalElement && bootstrapRef ? new bootstrapRef.Modal(addModalElement) : null;
        const editModal = editModalElement && bootstrapRef ? new bootstrapRef.Modal(editModalElement) : null;
        const viewModal = viewModalElement && bootstrapRef ? new bootstrapRef.Modal(viewModalElement) : null;
        const addSubmitButton = document.getElementById('addEmployeeSubmit');
        const addSubmitSpinner = addSubmitButton ? addSubmitButton.querySelector('.spinner-border') : null;
        const editSubmitButton = document.getElementById('editEmployeeSubmit');
        const editSubmitSpinner = editSubmitButton ? editSubmitButton.querySelector('.spinner-border') : null;
        const viewName = document.getElementById('viewEmployeeName');
        const viewPhone = document.getElementById('viewEmployeePhone');
        const viewInfo = document.getElementById('viewEmployeeInfo');
        const viewEditButton = document.getElementById('viewEmployeeEditButton');
        const viewDeleteButton = document.getElementById('viewEmployeeDeleteButton');
        const feedbackModal = createFeedbackModal('employeeAlertModal');
        let employees = [];
        let editingId = null;
        let viewingId = null;

        const toggleLoader = (state) => {
            if (loader) {
                loader.classList.toggle('d-none', !state);
            }
            if (tableWrapper) {
                tableWrapper.classList.toggle('d-none', state);
            }
            if (!state && !employees.length && emptyState) {
                emptyState.classList.remove('d-none');
            } else if (emptyState) {
                emptyState.classList.add('d-none');
            }
        };

        const renderEmployees = () => {
            if (!tableBody) {
                return;
            }
            if (!employees.length) {
                tableBody.innerHTML = '';
                if (tableWrapper) {
                    tableWrapper.classList.add('d-none');
                }
                if (emptyState) {
                    emptyState.classList.remove('d-none');
                }
                return;
            }

            if (tableWrapper) {
                tableWrapper.classList.remove('d-none');
            }
            if (emptyState) {
                emptyState.classList.add('d-none');
            }

            const fragment = document.createDocumentFragment();
            employees.forEach((employee) => {
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${employee.id}</td>
                    <td>${escapeHtml(employee.name || '')}</td>
                    <td>${escapeHtml(formatPhoneNumber(employee.phone))}</td>
                    <td>${escapeHtml(employee.info || '—')}</td>
                    <td class="text-end">
                        <div class="table-actions">
                            <button type="button" class="btn-icon" data-action="view" data-id="${employee.id}" title="Просмотр">
                                <i class="bi bi-eye"></i>
                            </button>
                            <button type="button" class="btn-icon" data-action="edit" data-id="${employee.id}" title="Редактировать">
                                <i class="bi bi-pencil"></i>
                            </button>
                            <button type="button" class="btn-icon" data-action="delete" data-id="${employee.id}" title="Удалить">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                `;
                fragment.appendChild(row);
            });
            tableBody.innerHTML = '';
            tableBody.appendChild(fragment);

            tableBody.querySelectorAll('[data-action="view"]').forEach((button) => {
                button.addEventListener('click', () => {
                    openEmployeeView(Number(button.getAttribute('data-id')));
                });
            });
            tableBody.querySelectorAll('[data-action="edit"]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = Number(button.getAttribute('data-id'));
                    startEditEmployee(id);
                });
            });
            tableBody.querySelectorAll('[data-action="delete"]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = Number(button.getAttribute('data-id'));
                    const employee = employees.find((item) => item.id === id);
                    if (!employee) {
                        return;
                    }
                    feedbackModal.show({
                        title: 'Удалить сотрудника?',
                        message: `Сотрудник "${employee.name}" будет удалён.`,
                        actionText: 'Удалить',
                        closeText: 'Отмена',
                        onAction: () => deleteEmployee(id)
                    });
                });
            });
        };

        const loadEmployees = async () => {
            toggleLoader(true);
            try {
                const data = await apiRequest('/employees');
                employees = Array.isArray(data) ? data : [];
                renderEmployees();
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось загрузить сотрудников',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            } finally {
                toggleLoader(false);
            }
        };

        const toggleFormLoading = (button, spinner, state) => {
            if (button) {
                button.disabled = state;
            }
            if (spinner) {
                spinner.classList.toggle('d-none', !state);
            }
        };

        const resetAddForm = () => {
            if (addForm) {
                addForm.reset();
                addForm.classList.remove('was-validated');
            }
        };

        const startEditEmployee = (id) => {
            const employee = employees.find((item) => item.id === id);
            if (!employee || !editForm) {
                return;
            }
            editingId = id;
            editForm.name.value = employee.name || '';
            editForm.phone.value = employee.phone || '';
            editForm.info.value = employee.info || '';
            editForm.classList.remove('was-validated');
            if (editModal) {
                editModal.show();
            }
        };

        const openEmployeeView = (id) => {
            const employee = employees.find((item) => item.id === id);
            if (!employee || !viewModal || !viewName || !viewPhone || !viewInfo) {
                return;
            }
            viewingId = id;
            viewName.textContent = employee.name || '—';
            viewPhone.textContent = formatPhoneNumber(employee.phone);
            viewInfo.textContent = employee.info || '—';
            viewModal.show();
        };

        const deleteEmployee = async (id) => {
            try {
                await apiRequest(`/employees/${id}`, {method: 'DELETE'});
                employees = employees.filter((employee) => employee.id !== id);
                renderEmployees();
                emitToast('Сотрудник удалён');
            } catch (error) {
                if (!handleAuthError(error, feedbackModal)) {
                    feedbackModal.show({
                        title: 'Не удалось удалить сотрудника',
                        message: error.message || 'Попробуйте повторить попытку позже.'
                    });
                }
            }
        };

        if (addForm) {
            addForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!addForm.checkValidity()) {
                    addForm.classList.add('was-validated');
                    return;
                }
                const name = addForm.name.value.trim();
                const phone = addForm.phone.value.trim();
                const info = addForm.info.value.trim() || null;
                if (!name || !phone) {
                    addForm.classList.add('was-validated');
                    return;
                }
                toggleFormLoading(addSubmitButton, addSubmitSpinner, true);
                try {
                    const payload = {name, phone, info};
                    const created = await apiRequest('/employees', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(payload)
                    });
                    employees.push(created);
                    renderEmployees();
                    if (addModal) {
                        addModal.hide();
                    }
                    resetAddForm();
                    emitToast('Сотрудник добавлен');
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось сохранить сотрудника',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleFormLoading(addSubmitButton, addSubmitSpinner, false);
                }
            });
        }

        if (editForm) {
            editForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!editForm.checkValidity()) {
                    editForm.classList.add('was-validated');
                    return;
                }
                if (!editingId) {
                    return;
                }
                const name = editForm.name.value.trim();
                const phone = editForm.phone.value.trim();
                const info = editForm.info.value.trim() || null;
                if (!name || !phone) {
                    editForm.classList.add('was-validated');
                    return;
                }
                toggleFormLoading(editSubmitButton, editSubmitSpinner, true);
                try {
                    const payload = {name, phone, info};
                    const updated = await apiRequest(`/employees/${editingId}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(payload)
                    });
                    employees = employees.map((employee) => employee.id === editingId ? updated : employee);
                    renderEmployees();
                    if (editModal) {
                        editModal.hide();
                    }
                    editingId = null;
                    emitToast('Сотрудник обновлён');
                } catch (error) {
                    if (!handleAuthError(error, feedbackModal)) {
                        feedbackModal.show({
                            title: 'Не удалось обновить сотрудника',
                            message: error.message || 'Попробуйте повторить попытку позже.'
                        });
                    }
                } finally {
                    toggleFormLoading(editSubmitButton, editSubmitSpinner, false);
                }
            });
        }

        if (viewEditButton) {
            viewEditButton.addEventListener('click', () => {
                if (viewingId) {
                    startEditEmployee(viewingId);
                }
            });
        }

    if (viewDeleteButton) {
            viewDeleteButton.addEventListener('click', () => {
                if (!viewingId) {
                    return;
                }
                const employee = employees.find((item) => item.id === viewingId);
                if (!employee) {
                    return;
                }
                feedbackModal.show({
                    title: 'Удалить сотрудника?',
                    message: `Сотрудник "${employee.name}" будет удалён.`,
                    actionText: 'Удалить',
                    closeText: 'Отмена',
                    onAction: () => deleteEmployee(viewingId)
                });
            });
        }

        loadEmployees();
    }

    function createFeedbackModal(modalId) {
        const modalElement = document.getElementById(modalId);
        if (!modalElement || !bootstrapRef) {
            return {
                show: ({ title, message }) => {
                    const text = [title, message].filter(Boolean).join('\n');
                    if (text) {
                        window.alert(text);
                    }
                },
                hide: () => undefined
            };
        }
        const modal = new bootstrapRef.Modal(modalElement);
        const titleElement = modalElement.querySelector('[data-modal="title"]') || modalElement.querySelector('.modal-title');
        const bodyElement = modalElement.querySelector('[data-modal="body"]') || modalElement.querySelector('.modal-body');
        const actionButton = modalElement.querySelector('[data-modal="action"]');
        const closeButton = modalElement.querySelector('[data-modal="close"]');
        const defaultCloseText = closeButton ? closeButton.textContent : 'Закрыть';

        return {
            show: ({ title, message, actionText, onAction, closeText }) => {
                if (titleElement && title) {
                    titleElement.textContent = title;
                }
                if (bodyElement && message) {
                    bodyElement.textContent = message;
                }
                if (closeButton) {
                    if (typeof closeText === 'string') {
                        closeButton.textContent = closeText;
                    } else {
                        closeButton.textContent = defaultCloseText;
                    }
                }
                if (actionButton) {
                    if (actionText) {
                        actionButton.textContent = actionText;
                        actionButton.classList.remove('d-none');
                        actionButton.onclick = () => {
                            modal.hide();
                            if (typeof onAction === 'function') {
                                onAction();
                            }
                        };
                    } else {
                        actionButton.classList.add('d-none');
                        actionButton.onclick = null;
                    }
                }
                modal.show();
            },
            hide: () => {
                modal.hide();
            }
        };
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

    function initPhoneMasks() {
        document.querySelectorAll('[data-phone-input]').forEach((input) => {
            const applyMask = () => {
                input.value = formatPhoneInputValue(input.value);
            };
            input.addEventListener('input', applyMask);
            input.addEventListener('focus', applyMask);
            applyMask();
        });
    }

    function formatPhoneInputValue(value) {
        if (!value) {
            return '+7 (';
        }
        let digits = value.replace(/\D/g, '');
        if (digits.startsWith('7')) {
            digits = digits.slice(1);
        } else if (digits.startsWith('8')) {
            digits = digits.slice(1);
        }
        digits = digits.slice(0, 10);
        let formatted = '+7';
        if (digits.length > 0) {
            formatted += ' (' + digits.slice(0, 3);
        }
        if (digits.length >= 3) {
            formatted += ') ' + digits.slice(3, 6);
        } else if (digits.length > 0) {
            formatted += ') ' + digits.slice(3);
        }
        if (digits.length >= 6) {
            formatted += '-' + digits.slice(6, 8);
        }
        if (digits.length >= 8) {
            formatted += '-' + digits.slice(8, 10);
        }
        return formatted;
    }

    function formatPhoneNumber(value) {
        if (!value) {
            return '—';
        }
        return formatPhoneInputValue(value);
    }

    function getToken() {
        return localStorage.getItem('authToken');
    }

    async function apiRequest(url, options = {}) {
        const requestOptions = {
            method: options.method || 'GET',
            headers: Object.assign({}, options.headers || {})
        };

        if (!requestOptions.headers.Accept) {
            requestOptions.headers.Accept = 'application/json';
        }

        const token = getToken();
        if (token) {
            requestOptions.headers.Authorization = `Bearer ${token}`;
        }

        if (options.body !== undefined) {
            requestOptions.body = options.body;
        }

        try {
            const response = await fetch(url, requestOptions);
            const contentType = response.headers.get('content-type') || '';

            if (!response.ok) {
                const message = await extractErrorMessage(response, contentType);
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

            if (contentType.includes('application/json')) {
                return await response.json();
            }

            if (requestOptions.method === 'GET') {
                return await response.text();
            }

            return null;
        } catch (error) {
            if (error.name === 'TypeError') {
                const networkError = new Error('Не удалось связаться с сервером. Проверьте подключение к сети.');
                networkError.code = 'NETWORK';
                throw networkError;
            }
            throw error;
        }
    }

    async function extractErrorMessage(response, contentType) {
        if (contentType.includes('application/json')) {
            try {
                const data = await response.json();
                if (!data) {
                    return `Ошибка ${response.status}`;
                }
                if (typeof data === 'string') {
                    return data;
                }
                if (data.error) {
                    return data.error;
                }
                const firstKey = Object.keys(data)[0];
                if (firstKey) {
                    const value = data[firstKey];
                    if (Array.isArray(value)) {
                        return value[0];
                    }
                    if (typeof value === 'string') {
                        return value;
                    }
                }
            } catch (error) {
                return `Ошибка ${response.status}`;
            }
        } else {
            try {
                const text = await response.text();
                if (text) {
                    return text;
                }
            } catch (error) {
                return `Ошибка ${response.status}`;
            }
        }
        return `Ошибка ${response.status}`;
    }

    function handleAuthError(error, modal) {
        if (!error) {
            return false;
        }
        if (error.code === 'UNAUTHORIZED' || error.status === 401 || error.status === 403) {
            if (modal) {
                modal.show({
                    title: 'Ошибка запроса',
                    message: 'Не удалось выполнить действие. Попробуйте повторно.'
                });
            }
            return true;
        }
        return false;
    }

    function extractWarehouseIdFromLocation() {
        const pathMatch = window.location.pathname.match(/\/warehouse\/(\d+)(?:\/|$)/);
        if (pathMatch && pathMatch[1]) {
            return pathMatch[1];
        }
        const params = new URLSearchParams(window.location.search);
        if (params.has('id')) {
            return params.get('id');
        }
        return null;
    }
})();
