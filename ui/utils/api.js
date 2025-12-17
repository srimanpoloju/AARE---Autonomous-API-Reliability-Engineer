// ui/utils/api.js
import Cookies from 'js-cookie';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8088/api';

export async function fetchApi(path, options = {}) {
  const token = Cookies.get('jwt_token'); // Get token from cookie

  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      // Handle unauthorized access, e.g., redirect to login
      console.error('Unauthorized access. Redirecting to login...');
      // window.location.href = '/login'; // Uncomment for actual redirect
      Cookies.remove('jwt_token'); // Clear invalid token
    }
    const errorData = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(errorData.message || 'API request failed');
  }

  return response.json();
}

export async function login(username, password) {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(errorData.message || 'Login failed');
  }

  const data = await response.json();
  Cookies.set('jwt_token', data.jwt, { expires: 1 }); // Store token in cookie for 1 day
  return data;
}

export function logout() {
  Cookies.remove('jwt_token');
  // window.location.href = '/login'; // Uncomment for actual redirect
}

export function getToken() {
  return Cookies.get('jwt_token');
}