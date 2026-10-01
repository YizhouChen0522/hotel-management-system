import type { AuthSession } from '../../types/auth'

const KEY = 'hotel.staff.session'
export const AUTH_CHANGED_EVENT = 'hotel-auth-changed'

export function readSession(): AuthSession | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const value = JSON.parse(raw) as Partial<AuthSession>
    return typeof value.token === 'string' && value.user != null && Array.isArray(value.roles) ? value as AuthSession : null
  } catch { return null }
}

export function saveSession(session: AuthSession) {
  localStorage.setItem(KEY, JSON.stringify(session))
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT))
}

export function clearSession() {
  localStorage.removeItem(KEY)
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT))
}
