"use client";

import { createContext, useContext, useState, useEffect, ReactNode } from "react";

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

  useEffect(() => {
    const authPaths = ["/login", "/register", "/verify"];
    const isAuthPage = authPaths.some(p => window.location.pathname.startsWith(p));
    if (isAuthPage) {
      setIsLoading(false);
    } else {
      refreshAccessToken();
    }
  }, []);

  async function refreshAccessToken() {
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_AUTH_API}/auth/token/refresh`, {
        method: "POST",
        credentials: "include",
      });
      if (res.ok) {
        const data = await res.json();
        const token: string = data.data.accessToken;
        setAccessToken(token);
        // JWT payload에서 email 추출
        const payload = JSON.parse(atob(token.split(".")[1]));
        setUser({ email: payload.email });
      }
    } finally {
      setIsLoading(false);
    }
  }

  function login(token: string, userInfo: User) {
    setAccessToken(token);
    setUser(userInfo);
  }

  function logout() {
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
