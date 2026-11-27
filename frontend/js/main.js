import { request } from './api.js';
import { saveToken, getToken, removeToken } from './auth.js';

// 1. 화면 요소 찾기
const loginForm = document.querySelector('#login-form');
const signupForm = document.querySelector('#signup-form');
const userSection = document.querySelector('#user-section');
const userName = document.querySelector('#user-name');
const logoutButton = document.querySelector('#logout-button');
const message = document.querySelector('#message');

// 2. 화면을 바꾸는 함수들
// textContent는 글자를 그대로 넣어서, 서버 값에 HTML이 섞여도 실행되지 않음 (XSS 방지)
function showMessage(text) {
    message.textContent = text;
}

function showError(error) {
    // 만료 안내는 아래 auth:expired에서 보여주므로 덮어쓰지 않음
    if (error.code === 'UNAUTHORIZED') {
        return;
    }
    // 입력값 검증 실패면 필드별 사유를, 아니면 공통 메시지를 보여줌
    const details = error.fieldErrors?.map((fieldError) => fieldError.message).join(' ');
    showMessage(details || error.message);
}

function showLoggedOut() {
    loginForm.hidden = false;
    signupForm.hidden = false;
    userSection.hidden = true;
    userName.textContent = '';
}

async function showLoggedIn() {
    const me = await request('/api/users/me');
    userName.textContent = me.name;
    loginForm.hidden = true;
    signupForm.hidden = true;
    userSection.hidden = false;
}

// 3. 사용자의 행동에 반응하기
loginForm.addEventListener('submit', async (event) => {
    event.preventDefault(); // 폼의 기본 동작(주소에 값을 붙이며 새로고침)을 막음
    const body = Object.fromEntries(new FormData(loginForm));

    try {
        const data = await request('/api/auth/login', { method: 'POST', body });
        saveToken(data.token);
        loginForm.reset();
        showMessage('');
        await showLoggedIn();
    } catch (error) {
        showError(error);
    }
});

signupForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(signupForm));

    try {
        await request('/api/auth/signup', { method: 'POST', body });
        signupForm.reset();
        showMessage('회원가입이 완료됐습니다. 로그인해주세요.');
    } catch (error) {
        showError(error);
    }
});

logoutButton.addEventListener('click', () => {
    removeToken();
    showLoggedOut();
    showMessage('로그아웃했습니다.');
});

window.addEventListener('auth:expired', () => {
    showLoggedOut();
    showMessage('로그인이 만료됐습니다. 다시 로그인해주세요.');
});

// 4. 처음 열었을 때: 토큰이 남아 있으면 로그인 상태를 복원
if (getToken()) {
    showLoggedIn().catch(showError);
}