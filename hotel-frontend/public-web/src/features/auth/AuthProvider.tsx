"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { clientApi, jsonBody } from "@/lib/api/client-fetch";

export interface Customer { id: number; username: string; realName: string | null; email: string; phone: string | null; status: number }
export interface CustomerSession { token: string; user: Customer; roles: string[] }
type Registration = { username: string; password: string; realName: string; phone: string; email: string };
type Auth = { session: CustomerSession | null; ready: boolean; login: (email: string, password: string) => Promise<void>; register: (value: Registration) => Promise<void>; logout: () => void };

const AuthContext = createContext<Auth | null>(null);
const STORAGE_KEY = "hotel.customer.session";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<CustomerSession | null>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (raw) setSession(JSON.parse(raw) as CustomerSession);
      } catch { localStorage.removeItem(STORAGE_KEY); }
      finally { setReady(true); }
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);
  const save = useCallback((value: CustomerSession | null) => {
    setSession(value);
    if (value) localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
    else localStorage.removeItem(STORAGE_KEY);
  }, []);
  const login = useCallback(async (email: string, password: string) => {
    save(await clientApi<CustomerSession>("/api/public/auth/customer/login", { method: "POST", ...jsonBody({ email, password }) }));
  }, [save]);
  const register = useCallback(async (value: Registration) => {
    await clientApi<Customer>("/api/public/auth/customer/register", { method: "POST", ...jsonBody(value) });
  }, []);
  const logout = useCallback(() => save(null), [save]);
  const value = useMemo(() => ({ session, ready, login, register, logout }), [session, ready, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("AuthProvider missing");
  return value;
}

export function safeReturnTo(value: string | null, fallback = "/account") {
  return value?.startsWith("/") && !value.startsWith("//") ? value : fallback;
}
