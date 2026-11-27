import { getToken, removeToken } from './auth.js';

// 백엔드 주소. 주소가 바뀌면 여기만 고침
const API_BASE_URL = 'http://localhost:8080';

export async function request(path, { method = 'GET', body } = {}) {
    // 1. 보낼 헤더 준비
    const token = getToken();
    const headers = {};

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }
    if (body) {
        headers['Content-Type'] = 'application/json';
    }

    // 2. 요청 보내기
    let response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            method,
            headers,
            body: body ? JSON.stringify(body) : undefined,
        });
    } catch (error) {
        throw { message: '서버에 연결할 수 없습니다.' };
    }

    // 3. 응답 읽기 (회원가입처럼 본문이 없는 응답이 있어서, 내용이 있을 때만 JSON으로 바꿈)
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;

    // 4. 실패 처리
    if (!response.ok) {
        // 토큰이 만료됐거나 위조된 경우: 토큰을 지우고 화면에 알림
        if (token && data?.code === 'UNAUTHORIZED') {
            removeToken();
            window.dispatchEvent(new Event('auth:expired'));
        }
        throw data ?? { message: '요청에 실패했습니다.' };
    }

    return data;
}