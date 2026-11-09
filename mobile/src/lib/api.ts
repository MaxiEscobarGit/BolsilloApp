// Thin fetch client for the Spring Boot API (api/). Access token lives in memory,
// refresh token in the secure store; a 401 triggers one refresh + retry.
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

// On a physical phone, localhost is the phone itself: set EXPO_PUBLIC_API_URL=http://<LAN-IP>:8080.
export const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080';

const REFRESH_KEY = 'bolsillo.refresh';

export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  userId: string;
  email: string;
  displayName: string;
};

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

// shortcut: expo-secure-store has no web support, so web keeps the refresh token in memory
// (lost on reload). Fine for previewing in a browser; the product target is native.
let webRefresh: string | null = null;
export const refreshStore = {
  get: () => (Platform.OS === 'web' ? Promise.resolve(webRefresh) : SecureStore.getItemAsync(REFRESH_KEY)),
  set: (v: string) => (Platform.OS === 'web' ? ((webRefresh = v), Promise.resolve()) : SecureStore.setItemAsync(REFRESH_KEY, v)),
  clear: () => (Platform.OS === 'web' ? ((webRefresh = null), Promise.resolve()) : SecureStore.deleteItemAsync(REFRESH_KEY)),
};

let accessToken: string | null = null;
let onSessionLost: () => void = () => {};

export function setSession(auth: AuthResponse | null) {
  accessToken = auth?.accessToken ?? null;
  return auth ? refreshStore.set(auth.refreshToken) : refreshStore.clear();
}

export function onUnauthorized(handler: () => void) {
  onSessionLost = handler;
}

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const res = await fetch(API_URL + path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...init.headers,
    },
  });

  if (res.status === 401 && retry && !path.startsWith('/api/auth/')) {
    if (await refresh()) return request<T>(path, init, false);
    onSessionLost();
  }
  if (!res.ok) {
    // The API always answers { error, code, status } (GlobalExceptionHandler).
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body.code ?? 'UNKNOWN', body.error ?? `HTTP ${res.status}`);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

// Single in-flight refresh: refresh tokens rotate, so two parallel refreshes would trip
// the backend's reuse detection and revoke every session.
let refreshing: Promise<AuthResponse | null> | null = null;
export function refresh(): Promise<AuthResponse | null> {
  refreshing ??= (async () => {
    const token = await refreshStore.get();
    if (!token) return null;
    try {
      const auth = await request<AuthResponse>('/api/auth/refresh', { method: 'POST', headers: { 'Refresh-Token': token } }, false);
      await setSession(auth);
      return auth;
    } catch {
      await setSession(null);
      return null;
    }
  })().finally(() => (refreshing = null));
  return refreshing;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body), headers }),
};
