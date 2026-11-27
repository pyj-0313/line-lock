// 토큰을 저장할 때 쓰는 이름. 한 곳에만 적어서 오타를 막음
const TOKEN_KEY = 'linelock.token';

export function saveToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}

export function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

export function removeToken() {
    localStorage.removeItem(TOKEN_KEY);
}