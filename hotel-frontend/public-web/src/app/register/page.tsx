"use client";
import Link from "next/link";
import { Suspense, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { safeReturnTo, useAuth } from "@/features/auth/AuthProvider";

function RegisterForm() {
  const [form, setForm] = useState({ username: "", password: "", realName: "", phone: "", email: "" });
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const { register } = useAuth(); const router = useRouter(); const params = useSearchParams();
  const returnTo = safeReturnTo(params.get("returnTo"));
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    try { await register(form); router.replace(`/login?returnTo=${encodeURIComponent(returnTo)}`); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "注册失败"); }
    finally { setBusy(false); }
  }
  const labels = { username: "用户名", realName: "姓名", email: "邮箱", phone: "电话", password: "密码" };
  return <form className="auth-card" onSubmit={submit}><p className="eyebrow">BEGIN YOUR STAY</p><h1>创建客户账户</h1>{(Object.keys(labels) as (keyof typeof labels)[]).map((key) => <label key={key}>{labels[key]}<input required={key !== "phone"} type={key === "password" ? "password" : key === "email" ? "email" : "text"} value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })}/></label>)}{error && <p className="form-error">{error}</p>}<button className="button-primary" disabled={busy}>{busy ? "提交中…" : "注册"}</button><p>已有账户？ <Link href={`/login?returnTo=${encodeURIComponent(returnTo)}`}>登录</Link></p></form>;
}
export default function RegisterPage() { return <main className="auth-page"><Suspense fallback={<div className="auth-card">正在准备注册…</div>}><RegisterForm /></Suspense></main>; }
