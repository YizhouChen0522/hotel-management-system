"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ClientApiError } from "@/lib/api/client-fetch";
import type { WalletAccount } from "@/types/financial";
import { accountApi } from "./account-api";
import { currency } from "./format";

export function AccountOverview() {
  const { session, logout } = useAuth();
  const [wallet, setWallet] = useState<WalletAccount | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!session) return;
    accountApi.wallet(session.token)
      .then(setWallet)
      .catch((reason: unknown) => {
        if (reason instanceof ClientApiError && reason.status === 401) logout();
        setError(reason instanceof Error ? reason.message : "账户摘要加载失败");
      });
  }, [session, logout]);

  return (
    <section className="account-shell">
      <header className="account-heading">
        <div>
          <p className="eyebrow">CUSTOMER ACCOUNT</p>
          <h1>欢迎回来，{session?.user.realName || session?.user.username}</h1>
          <p>{session?.user.email}</p>
        </div>
        <button className="button-secondary" onClick={logout}>退出登录</button>
      </header>

      {error && <p className="form-error">{error}</p>}
      <div className="account-cards">
        <Link href="/wallet" className="account-card featured">
          <span>Wallet</span>
          <strong>{wallet ? currency(wallet.balance, wallet.currency) : "正在读取…"}</strong>
          <small>{wallet ? `状态：${wallet.status}` : "查看余额与充值申请"}</small>
        </Link>
        <Link href="/bookings" className="account-card">
          <span>Reservations</span>
          <strong>我的预订</strong>
          <small>查看申请、订金和入住关联</small>
        </Link>
        <Link href="/folios" className="account-card">
          <span>Bills</span>
          <strong>住宿账单</strong>
          <small>Folio 明细、支付与退款</small>
        </Link>
        <Link href="/invoices" className="account-card">
          <span>Invoices</span>
          <strong>正式发票</strong>
          <small>查看已开具的不可变快照</small>
        </Link>
      </div>

      <dl className="profile-strip">
        <div><dt>用户名</dt><dd>{session?.user.username}</dd></div>
        <div><dt>电话</dt><dd>{session?.user.phone || "未填写"}</dd></div>
        <div><dt>账户状态</dt><dd>{session?.user.status === 1 ? "正常" : "受限"}</dd></div>
      </dl>
    </section>
  );
}

