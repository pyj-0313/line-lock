import { request } from './api.js';
import { saveToken, getToken, removeToken } from './auth.js';

// 1. 화면 요소 찾기
const loginForm = document.querySelector('#login-form');
const signupForm = document.querySelector('#signup-form');
const userSection = document.querySelector('#user-section');
const userName = document.querySelector('#user-name');
const logoutButton = document.querySelector('#logout-button');
const message = document.querySelector('#message');
const authTabs = document.querySelector('#auth-tabs');
const loginTab = document.querySelector('#login-tab');
const signupTab = document.querySelector('#signup-tab');

// 2. 화면을 바꾸는 함수들
// textContent는 글자를 그대로 넣어서, 서버 값에 HTML이 섞여도 실행되지 않음 (XSS 방지)
function showMessage(text) {
    message.textContent = text;
}

function clearFieldErrors() {
    document.querySelectorAll('.field-error').forEach((slot) => (slot.textContent = ''));
    document.querySelectorAll('[aria-invalid]').forEach((input) => input.removeAttribute('aria-invalid'));
}

// 서버가 알려준 필드 이름에 해당하는 칸 아래에 사유를 보여줌
function showFieldErrors(form, fieldErrors) {
    fieldErrors.forEach((fieldError) => {
        const slot = form.querySelector(`[data-error-for="${fieldError.field}"]`);
        // 화면에 해당 칸이 없는 오류는 놓치지 않도록 위쪽 안내 자리에 보여줌
        if (!slot) {
            showMessage(fieldError.message);
            return;
        }
        // 한 칸에 사유가 여러 개일 수 있어서 이어 붙임
        slot.textContent = `${slot.textContent} ${fieldError.message}`.trim();
        form.elements.namedItem(fieldError.field).setAttribute('aria-invalid', 'true');
    });
}

function showError(error, form) {
    // 만료 안내는 아래 auth:expired에서 보여주므로 덮어쓰지 않음
    if (error.code === 'UNAUTHORIZED') {
        return;
    }
    if (form && error.fieldErrors) {
        showFieldErrors(form, error.fieldErrors);
        return;
    }
    showMessage(error.message);
}

// 선택한 탭의 폼만 보여줌
function showTab(isSignup) {
    loginForm.hidden = isSignup;
    signupForm.hidden = !isSignup;
    loginTab.classList.toggle('active', !isSignup);
    signupTab.classList.toggle('active', isSignup);
    loginTab.setAttribute('aria-pressed', String(!isSignup));
    signupTab.setAttribute('aria-pressed', String(isSignup));
    showMessage('');
    clearFieldErrors();
}

function showLoggedOut() {
    authTabs.hidden = false;
    showTab(false);
    userSection.hidden = true;
    userName.textContent = '';
}

async function showLoggedIn() {
    const me = await request('/api/users/me');
    userName.textContent = me.name;
    authTabs.hidden = true;
    loginForm.hidden = true;
    signupForm.hidden = true;
    userSection.hidden = false;
}

// 3. 사용자의 행동에 반응하기
loginForm.addEventListener('submit', async (event) => {
    event.preventDefault(); // 폼의 기본 동작(주소에 값을 붙이며 새로고침)을 막음
    showMessage('');
    clearFieldErrors();
    const body = Object.fromEntries(new FormData(loginForm));

    try {
        const data = await request('/api/auth/login', { method: 'POST', body });
        saveToken(data.token);
        loginForm.reset();
        showMessage('');
        await showLoggedIn();
    } catch (error) {
        showError(error, loginForm);
    }
});

signupForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    showMessage('');
    clearFieldErrors();
    const body = Object.fromEntries(new FormData(signupForm));

    try {
        await request('/api/auth/signup', { method: 'POST', body });
        signupForm.reset();
        showTab(false);
        showMessage('회원가입이 완료됐습니다. 로그인해주세요.');
    } catch (error) {
        showError(error, signupForm);
    }
});

logoutButton.addEventListener('click', () => {
    removeToken();
    showLoggedOut();
    showMessage('로그아웃했습니다.');
});

loginTab.addEventListener('click', () => showTab(false));
signupTab.addEventListener('click', () => showTab(true));

window.addEventListener('auth:expired', () => {
    showLoggedOut();
    showMessage('로그인이 만료됐습니다. 다시 로그인해주세요.');
});

// 4. 처음 열었을 때: 토큰이 남아 있으면 로그인 상태를 복원
if (getToken()) {
    showLoggedIn().catch(showError);
}