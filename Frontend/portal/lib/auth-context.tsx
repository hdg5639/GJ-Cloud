"use client";

import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { setTokenRefresher } from "./api-client";

interface User {
  email: string;
}

interface AuthContextType {
  accessToken: string | null;
  user: User | null;
  isLoading: boolean;
  login: (token: string, user: User) => void;
  logout: () => void;
  refresh: () => Promise<string | null>;
}

interface AccessTokenPayload {
  email: string;
  exp: number;
}

type AuthSyncMessage =
  | { type: "TOKEN"; token: string }
  | { type: "LOGOUT" };

const AuthContext = createContext<AuthContextType | null>(null);
const AUTH_PATHS = ["/login", "/register", "/verify"];
const AUTH_CHANNEL = "gamjabox-auth-session";
const AUTH_REFRESH_LOCK = "gamjabox-auth-refresh";
const REFRESH_EARLY_MS = 90_000;
const REFRESH_RETRY_DELAYS_MS = [5_000, 15_000, 30_000, 60_000];

function isAuthPage(): boolean {
  return typeof window !== "undefined" && AUTH_PATHS.some((path) => window.location.pathname.startsWith(path));
}

function parseAccessToken(token: string): AccessTokenPayload {
  const encoded = token.split(".")[1];
  if (!encoded) throw new Error("Access token payload is missing");
  const normalized = encoded.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  const bytes = Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
  const payload = JSON.parse(new TextDecoder().decode(bytes)) as Partial<AccessTokenPayload>;
  if (typeof payload.email !== "string" || typeof payload.exp !== "number") {
    throw new Error("Access token payload is invalid");
  }
  return payload as AccessTokenPayload;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const accessTokenRef = useRef<string | null>(null);
  const tokenExpiresAtRef = useRef(0);
  const lastTokenUpdateRef = useRef(0);
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const refreshRetryCountRef = useRef(0);
  const pendingRefreshRef = useRef<Promise<string | null> | null>(null);
  const authChannelRef = useRef<BroadcastChannel | null>(null);

  // 여러 탭이 같은 회전형 refresh token을 동시에 소비하지 않게 새 토큰을 공유한다.
  // Web Locks를 지원하지 않는 브라우저에서도 먼저 갱신된 탭의 BroadcastChannel 메시지를
  // 받은 뒤라면 뒤늦게 도착한 실패 응답이 정상 세션을 지우지 않는다.
  useEffect(() => {
    if (typeof BroadcastChannel === "undefined") return;
    const channel = new BroadcastChannel(AUTH_CHANNEL);
    authChannelRef.current = channel;
    channel.onmessage = (event: MessageEvent<AuthSyncMessage>) => {
      if (event.data?.type === "TOKEN") {
        try {
          applyAccessToken(event.data.token, false);
        } catch {
          // 다른 탭에서 손상된 메시지가 오더라도 현재 탭 세션은 건드리지 않는다.
        }
      } else if (event.data?.type === "LOGOUT") {
        clearLocalSession(false);
        redirectToLogin();
      }
    };
    return () => {
      channel.close();
      authChannelRef.current = null;
    };
    // 세션 동기화 핸들러는 ref 기반 함수만 사용하며 Provider 수명 동안 한 번만 등록한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (isAuthPage()) {
      // 인증 전용 화면에서는 서버 세션 복구를 기다릴 필요가 없다.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setIsLoading(false);
    } else {
      void refreshAccessToken();
    }
    return () => clearRefreshTimer();
    // 초기 세션 복구는 Provider 마운트 시 한 번만 수행한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // api-client가 401을 받았을 때도 아래의 단일 갱신 파이프라인을 사용한다.
  useEffect(() => {
    setTokenRefresher(refreshAccessToken);
    // api-client에는 ref 기반의 안정적인 세션 갱신 진입점을 한 번만 등록한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 백그라운드 탭의 타이머는 브라우저가 오래 지연시킬 수 있다. 절전 복귀·탭 재활성화·
  // 네트워크 복구 시 만료가 가까운 토큰만 즉시 갱신해 사용 도중 갑자기 튕기는 일을 막는다.
  useEffect(() => {
    function recoverSession() {
      if (document.visibilityState === "hidden") return;
      const shouldRefresh = !accessTokenRef.current || tokenExpiresAtRef.current - Date.now() <= REFRESH_EARLY_MS;
      if (shouldRefresh && !isAuthPage()) void refreshAccessToken();
    }

    document.addEventListener("visibilitychange", recoverSession);
    window.addEventListener("focus", recoverSession);
    window.addEventListener("online", recoverSession);
    return () => {
      document.removeEventListener("visibilitychange", recoverSession);
      window.removeEventListener("focus", recoverSession);
      window.removeEventListener("online", recoverSession);
    };
    // 브라우저 복귀 이벤트는 Provider 수명 동안 한 번만 구독한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function clearRefreshTimer() {
    if (!refreshTimerRef.current) return;
    clearTimeout(refreshTimerRef.current);
    refreshTimerRef.current = null;
  }

  function redirectToLogin() {
    if (typeof window !== "undefined" && !isAuthPage()) {
      window.location.replace("/login");
    }
  }

  function applyAccessToken(token: string, broadcast = true) {
    const payload = parseAccessToken(token);
    if (payload.exp * 1000 <= Date.now()) throw new Error("Access token is expired");

    accessTokenRef.current = token;
    tokenExpiresAtRef.current = payload.exp * 1000;
    lastTokenUpdateRef.current = Date.now();
    refreshRetryCountRef.current = 0;
    setAccessToken(token);
    setUser({ email: payload.email });
    setIsLoading(false);
    scheduleRefresh(payload.exp);

    if (broadcast) authChannelRef.current?.postMessage({ type: "TOKEN", token } satisfies AuthSyncMessage);
  }

  function clearLocalSession(broadcast = true) {
    clearRefreshTimer();
    accessTokenRef.current = null;
    tokenExpiresAtRef.current = 0;
    refreshRetryCountRef.current = 0;
    setAccessToken(null);
    setUser(null);
    setIsLoading(false);
    if (broadcast) authChannelRef.current?.postMessage({ type: "LOGOUT" } satisfies AuthSyncMessage);
  }

  function scheduleRefresh(exp: number) {
    clearRefreshTimer();
    const delay = Math.max(exp * 1000 - Date.now() - REFRESH_EARLY_MS, 0);
    refreshTimerRef.current = setTimeout(() => {
      void refreshAccessToken();
    }, delay);
  }

  function scheduleRefreshRetry() {
    clearRefreshTimer();
    const attempt = refreshRetryCountRef.current;
    const delay = REFRESH_RETRY_DELAYS_MS[Math.min(attempt, REFRESH_RETRY_DELAYS_MS.length - 1)];
    refreshRetryCountRef.current = attempt + 1;
    refreshTimerRef.current = setTimeout(() => {
      void refreshAccessToken();
    }, delay);
  }

  async function requestRefresh(requestStartedAt: number): Promise<string | null> {
    // 다른 탭이 lock 대기 중 먼저 갱신했다면 이미 전달받은 토큰을 그대로 사용한다.
    if (lastTokenUpdateRef.current > requestStartedAt && accessTokenRef.current) {
      return accessTokenRef.current;
    }

    try {
      const response = await fetch(`${process.env.NEXT_PUBLIC_AUTH_API}/auth/token/refresh`, {
        method: "POST",
        credentials: "include",
      });

      if (response.ok) {
        const body = await response.json();
        const token = body?.data?.accessToken;
        if (typeof token !== "string") throw new Error("Refresh response does not contain an access token");
        applyAccessToken(token);
        return token;
      }

      const terminalSessionFailure = response.status === 400 || response.status === 401 || response.status === 403;
      if (terminalSessionFailure) {
        // 요청 도중 다른 탭이 새 토큰을 공유했다면, 이 응답은 이미 회전된 과거 쿠키의 경합 결과다.
        if (lastTokenUpdateRef.current > requestStartedAt && accessTokenRef.current) {
          return accessTokenRef.current;
        }
        clearLocalSession(false);
        redirectToLogin();
        return null;
      }

      // 429/5xx는 로그인 만료가 아니라 일시 장애다. 기존 토큰과 장기 refresh cookie를 보존한다.
      scheduleRefreshRetry();
      return accessTokenRef.current;
    } catch {
      // 오프라인·절전 복귀·순간적인 CORS/네트워크 오류 때문에 사용자를 로그아웃시키지 않는다.
      scheduleRefreshRetry();
      return accessTokenRef.current;
    } finally {
      // 최초 진입 중 서버가 잠시 닿지 않으면 로그인 화면으로 보내지 않고 세션 복구를 계속 기다린다.
      if (accessTokenRef.current) setIsLoading(false);
    }
  }

  function refreshAccessToken(): Promise<string | null> {
    if (pendingRefreshRef.current) return pendingRefreshRef.current;

    const requestStartedAt = Date.now();
    const promise = (async () => {
      try {
        if (typeof navigator !== "undefined" && "locks" in navigator) {
          return await navigator.locks.request(AUTH_REFRESH_LOCK, () => requestRefresh(requestStartedAt));
        }
        return await requestRefresh(requestStartedAt);
      } finally {
        pendingRefreshRef.current = null;
      }
    })();

    pendingRefreshRef.current = promise;
    return promise;
  }

  function login(token: string, userInfo: User) {
    try {
      applyAccessToken(token);
    } catch {
      accessTokenRef.current = token;
      setAccessToken(token);
      setUser(userInfo);
      setIsLoading(false);
      // 비정상 JWT라도 로그인 API가 성공했다면 즉시 refresh를 시도해 정상 토큰으로 교체한다.
      scheduleRefreshRetry();
    }
  }

  function logout() {
    clearLocalSession(true);
  }

  return (
    <AuthContext.Provider value={{ accessToken, user, isLoading, login, logout, refresh: refreshAccessToken }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
