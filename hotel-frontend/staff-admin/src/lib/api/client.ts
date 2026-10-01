import { clearSession, readSession } from '../auth/session'
import type { ApiResult } from '../../types/auth'

const BASE_URL = (import.meta.env.VITE_BACKEND_BASE_URL?.trim() || '').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number
  constructor(message: string, status: number) { super(message); this.status = status }
}

export function resolveApiUrl(path: string) { return `${BASE_URL}${path}` }

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = readSession()?.token
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${BASE_URL}${path}`, { ...init, headers })
  if (response.status === 401) clearSession()
  let result: ApiResult<T> | null = null
  try { result = await response.json() as ApiResult<T> } catch { /* handled below */ }
  if (result?.code === 401) clearSession()
  if (!response.ok || !result || result.code !== 200) throw new ApiError(result?.message || 'Request failed', response.status || result?.code || 500)
  return result.data
}
