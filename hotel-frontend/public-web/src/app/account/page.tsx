"use client";
import Link from "next/link";
import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { useAuth } from "@/features/auth/AuthProvider";
export default function Account() { const { session, logout } = useAuth(); return <ProtectedShell><main className="customer-page"><section className="detail-panel"><p className="eyebrow">YOUR STAY</p><h1>客户账户</h1><dl className="detail-grid"><div><dt>姓名</dt><dd>{session?.user.realName || session?.user.username}</dd></div><div><dt>邮箱</dt><dd>{session?.user.email}</dd></div><div><dt>电话</dt><dd>{session?.user.phone || "未填写"}</dd></div><div><dt>账户状态</dt><dd>{session?.user.status === 1 ? "正常" : "受限"}</dd></div></dl><div className="form-actions"><Link className="button-primary" href="/bookings">我的预订</Link><Link className="button-secondary" href="/booking">新建预订</Link><button className="button-secondary" onClick={logout}>退出登录</button></div></section></main></ProtectedShell>; }
