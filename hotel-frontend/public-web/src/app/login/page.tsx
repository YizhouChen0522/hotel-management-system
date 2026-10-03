"use client";
import Link from "next/link";
import { Suspense, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { safeReturnTo, useAuth } from "@/features/auth/AuthProvider";

function LoginForm() {
  const [email, setEmail] = useState(""); const [password, setPassword] = useState("");
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const { login } = useAuth(); const router = useRouter(); const params = useSearchParams();
  const returnTo = safeReturnTo(params.get("returnTo"));
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    try { await login(email, password); router.replace(returnTo); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "登录失败"); }
    finally { setBusy(false); }
  }
  return <form className="auth-card" onSubmit={submit}><p className="eyebrow">WELCOME BACK</p><h1>登录您的旅程</h1><label>邮箱<input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}/></label><label>密码<input type="password" required value={password} onChange={(e) => setPassword(e.target.value)}/></label>{error && <p className="form-error">{error}</p>}<button className="button-primary" disabled={busy}>{busy ? "登录中…" : "登录"}</button><p>还没有账户？ <Link href={`/register?returnTo=${encodeURIComponent(returnTo)}`}>注册</Link></p></form>;
}
export default function LoginPage() { return <main className="auth-page"><Suspense fallback={<div className="auth-card">正在准备登录…</div>}><LoginForm /></Suspense></main>; }
