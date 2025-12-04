(function () {
    const loginForm = document.getElementById('loginForm');
    if (!loginForm) {
        return;
    }

    const submitButton = document.getElementById('loginSubmit');
    const spinner = submitButton ? submitButton.querySelector('.spinner-border') : null;
    const errorModalElement = document.getElementById('errorModal');
    const errorModalBody = document.getElementById('errorModalBody');
    const errorModal = errorModalElement ? new bootstrap.Modal(errorModalElement) : null;
    const successModalElement = document.getElementById('successModal');
    const successModalBody = document.getElementById('successModalBody');
    const successModalAction = document.getElementById('successModalAction');
    const successModal = successModalElement ? new bootstrap.Modal(successModalElement) : null;
    const DASHBOARD_URL = '/dashboard.html';
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

    const showErrorModal = (message) => {
        if (errorModalBody) {
            errorModalBody.textContent = message;
        }
        if (errorModal) {
            errorModal.show();
        }
    };

    const redirectToDashboard = () => {
        window.location.href = DASHBOARD_URL;
    };

    if (successModalAction) {
        successModalAction.addEventListener('click', () => {
            if (successRedirectTimer) {
                clearTimeout(successRedirectTimer);
            }
            redirectToDashboard();
        });
    }

    const showSuccessModal = (message) => {
        if (successModalBody && message) {
            successModalBody.textContent = message;
        }
        if (successModal) {
            successModal.show();
            if (successRedirectTimer) {
                clearTimeout(successRedirectTimer);
            }
            successRedirectTimer = setTimeout(redirectToDashboard, 3000);
        } else {
            redirectToDashboard();
        }
    };

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
                                message = errorData[firstKey];
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
})();
