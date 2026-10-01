import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { login } from '../features/auth/auth-api'
import { useAuth } from '../features/auth/auth-context'

export function LoginPage() {
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const { setSession } = useAuth(); const navigate = useNavigate(); const location = useLocation()
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(''); try { setSession(await login({ email, password })); const from = (location.state as { from?: string } | null)?.from; navigate(from && from !== '/login' ? from : '/', { replace: true }) } catch (cause) { setError(cause instanceof Error ? cause.message : '登录失败') } finally { setBusy(false) } }
  return <main className="login-page"><form className="login-card" onSubmit={submit}><h1>员工后台登录</h1><label>邮箱<input type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} /></label><label>密码<input type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} /></label>{error ? <p className="error" role="alert">{error}</p> : null}<button disabled={busy}>{busy ? '登录中…' : '登录'}</button></form></main>
}
