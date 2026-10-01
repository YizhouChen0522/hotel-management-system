import { createContext, useContext } from 'react'
import type { AuthSession } from '../../types/auth'

export interface AuthContextValue { session: AuthSession | null; setSession: (session: AuthSession) => void; logout: () => void; }
export const AuthContext = createContext<AuthContextValue | null>(null)
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error('useAuth must be used inside AuthProvider'); return value }
