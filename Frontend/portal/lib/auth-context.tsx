"use client";

import { createContext, useContext, useState, useEffect, useRef, ReactNode } from "react";

interface User {
  email: string;
}

interface AuthContextType {
  accessToken: string | null;
  user: User | null;
  isLoading: boolean;
  login: (token: string, user: User) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isRefreshingRef = useRef(false); // StrictMode 이중 호출 방지

  useEffect(() => {
    const authPaths = ["/login", "/register", "/verify"];
    const isAuthPage = authPaths.some(p => window.location.pathname.startsWith(p));
    if (isAuthPage) {
      setIsLoading(false);
    } else {
      refreshAccessToken();
    }

    return () => {
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    };
  }, []);

  async function refreshAccessToken() {
    // StrictMode 이중 호출 또는 타이머 중복 방지
    if (isRefreshingRef.current) return;
    isRefreshingRef.current = true;

    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_AUTH_API}/auth/token/refresh`, {
        method: "POST",
        credentials: "include",
      });

      if (res.ok) {
        const data = await res.json();
        const token: string = data.data.accessToken;
        setAccessToken(token);

        const payload = JSON.parse(atob(token.split(".")[1]));
        setUser({ email: payload.email });

        // 만료 1분 전에 자동 갱신 예약
        if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
        const msUntilRefresh = payload.exp * 1000 - Date.now() - 60_000;
        if (msUntilRefresh > 0) {
          refreshTimerRef.current = setTimeout(() => {
            isRefreshingRef.current = false;
            refreshAccessToken();
          }, msUntilRefresh);
        }
      } else {
        // 쿠키 만료 or 무효 — 로그인 상태 해제
        setAccessToken(null);
        setUser(null);
      }
    } catch {
      setAccessToken(null);
      setUser(null);
    } finally {
      isRefreshingRef.current = false;
      setIsLoading(false);
    }
  }

  function login(token: string, userInfo: User) {
    setAccessToken(token);
    setUser(userInfo);

    // 로그인 직후에도 만료 전 자동 갱신 예약
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
      const msUntilRefresh = payload.exp * 1000 - Date.now() - 60_000;
      if (msUntilRefresh > 0) {
        refreshTimerRef.current = setTimeout(() => {
          isRefreshingRef.current = false;
          refreshAccessToken();
        }, msUntilRefresh);
      }
    } catch {
      // payload 파싱 실패 시 타이머 없이 진행
    }
  }

  function logout() {
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    setAccessToken(null);
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ accessToken, user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
