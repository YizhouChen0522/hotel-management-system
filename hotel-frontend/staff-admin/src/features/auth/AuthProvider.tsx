import { useEffect, useState, type ReactNode } from 'react'
import { AUTH_CHANGED_EVENT, clearSession, readSession, saveSession } from '../../lib/auth/session'
import type { AuthSession } from '../../types/auth'
import { AuthContext } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setState] = useState<AuthSession | null>(() => readSession())
  useEffect(() => {
    const sync = () => setState(readSession())
    window.addEventListener(AUTH_CHANGED_EVENT, sync)
    window.addEventListener('storage', sync)
    return () => { window.removeEventListener(AUTH_CHANGED_EVENT, sync); window.removeEventListener('storage', sync) }
  }, [])
  return <AuthContext.Provider value={{ session, setSession: saveSession, logout: clearSession }}>{children}</AuthContext.Provider>
}
