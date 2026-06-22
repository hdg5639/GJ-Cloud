"use client";

import { createContext, useContext, useState, useEffect, useRef, ReactNode } from "react";
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

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isRefreshingRef = useRef(false);
  const pendingRefreshRef = useRef<Promise<string | null> | null>(null);

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

  // api-client가 401 시 호출할 수 있도록 refresher 등록
  useEffect(() => {
    setTokenRefresher(refreshAccessToken);
  }, []);

  async function refreshAccessToken(): Promise<string | null> {
    // 이미 진행 중이면 같은 Promise 반환 (중복 요청 방지)
    if (pendingRefreshRef.current) return pendingRefreshRef.current;
    if (isRefreshingRef.current) return null;

    isRefreshingRef.current = true;
    const promise = (async () => {
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

          scheduleRefresh(payload.exp);
          return token;
        } else {
          setAccessToken(null);
          setUser(null);
          return null;
        }
      } catch {
        setAccessToken(null);
        setUser(null);
        return null;
      } finally {
        isRefreshingRef.current = false;
        pendingRefreshRef.current = null;
        setIsLoading(false);
      }
    })();

    pendingRefreshRef.current = promise;
    return promise;
  }

  function scheduleRefresh(exp: number) {
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    const msUntilRefresh = exp * 1000 - Date.now() - 60_000;
    if (msUntilRefresh > 0) {
      refreshTimerRef.current = setTimeout(() => {
        refreshAccessToken();
      }, msUntilRefresh);
    }
  }

  function login(token: string, userInfo: User) {
    setAccessToken(token);
    setUser(userInfo);
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      scheduleRefresh(payload.exp);
    } catch {}
  }

  function logout() {
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    setAccessToken(null);
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ accessToken, user, isLoading, login, logout, refresh: refreshAccessToken }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
