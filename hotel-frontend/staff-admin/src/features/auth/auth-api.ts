import { apiFetch } from '../../lib/api/client'
import type { AuthSession, LoginRequest } from '../../types/auth'
export function login(request: LoginRequest) { return apiFetch<AuthSession>('/api/internal/auth/login', { method: 'POST', body: JSON.stringify(request) }) }
