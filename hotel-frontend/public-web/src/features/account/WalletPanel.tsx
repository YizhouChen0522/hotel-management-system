"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ClientApiError, requestKey } from "@/lib/api/client-fetch";
import type { WalletAccount, WalletTopUp, WalletTransaction } from "@/types/financial";
import { accountApi } from "./account-api";
import { currency, dateTime } from "./format";

export function WalletPanel() {
  const { session, logout } = useAuth();
  const [wallet, setWallet] = useState<WalletAccount | null>(null);
  const [transactions, setTransactions] = useState<WalletTransaction[]>([]);
  const [topUps, setTopUps] = useState<WalletTopUp[]>([]);
  const [amount, setAmount] = useState("");
  const [hasMoreTransactions, setHasMoreTransactions] = useState(false);
  const [hasMoreTopUps, setHasMoreTopUps] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    setError("");
    try {
      const [account, ledger, requests] = await Promise.all([
        accountApi.wallet(session.token),
        accountApi.walletTransactions(session.token),
        accountApi.walletTopUps(session.token),
      ]);
      setWallet(account);
      setTransactions(ledger);
      setTopUps(requests);
      setHasMoreTransactions(ledger.length === 100);
      setHasMoreTopUps(requests.length === 100);
    } catch (reason) {
      if (reason instanceof ClientApiError && reason.status === 401) logout();
      setError(reason instanceof Error ? reason.message : "钱包加载失败");
    } finally {
      setLoading(false);
    }
  }, [session, logout]);

  async function loadMoreTransactions() {
    if (!session || transactions.length === 0) return;
    setLoadingMore(true);
    setError("");
    try {
      const next = await accountApi.walletTransactions(
        session.token,
        transactions[transactions.length - 1].id,
      );
      setTransactions((current) => [
        ...current,
        ...next.filter((item) => !current.some((existing) => existing.id === item.id)),
      ]);
      setHasMoreTransactions(next.length === 100);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "资金记录加载失败");
    } finally {
      setLoadingMore(false);
    }
  }

  async function loadMoreTopUps() {
    if (!session || topUps.length === 0) return;
    setLoadingMore(true);
    setError("");
    try {
      const next = await accountApi.walletTopUps(
        session.token,
        topUps[topUps.length - 1].id,
      );
      setTopUps((current) => [
        ...current,
        ...next.filter((item) => !current.some((existing) => existing.id === item.id)),
      ]);
      setHasMoreTopUps(next.length === 100);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "充值记录加载失败");
    } finally {
      setLoadingMore(false);
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    const numericAmount = Number(amount);
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      setError("请输入有效的充值金额");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const created = await accountApi.requestTopUp(
        session.token,
        numericAmount,
        requestKey("wallet_topup"),
      );
      setTopUps((current) => [created, ...current.filter((item) => item.id !== created.id)]);
      setAmount("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "充值申请提交失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="account-shell">
      <header className="account-heading">
        <div><p className="eyebrow">CUSTOMER WALLET</p><h1>钱包</h1></div>
        <button className="button-secondary" onClick={() => void load()}>刷新</button>
      </header>
      {loading && <p className="quiet-empty">正在读取钱包…</p>}
      {error && <p className="form-error">{error}</p>}
      {wallet && (
        <div className="wallet-balance">
          <span>可用余额</span>
          <strong>{currency(wallet.balance, wallet.currency)}</strong>
          <small>{wallet.status}</small>
        </div>
      )}

      <div className="finance-columns">
        <section className="sub-panel">
          <h2>充值申请</h2>
          <form onSubmit={submit} className="inline-form">
            <label>金额<input value={amount} onChange={(event) => setAmount(event.target.value)} inputMode="decimal" placeholder="0.00" /></label>
            <button className="button-primary" disabled={busy}>{busy ? "提交中…" : "提交申请"}</button>
          </form>
          <p className="form-help">充值需由酒店员工确认。提交成功只代表 PENDING，不会立即增加余额。</p>
          <div className="record-list">
            {topUps.length === 0 && !loading && <p className="quiet-empty">暂无充值申请。</p>}
            {topUps.map((item) => (
              <article key={item.id} className="record-row">
                <div><strong>{currency(item.amount, wallet?.currency)}</strong><small>{dateTime(item.createTime)}</small></div>
                <span className={`text-status ${item.status.toLowerCase()}`}>{item.status}</span>
              </article>
            ))}
            {hasMoreTopUps && (
              <button className="button-secondary" disabled={loadingMore} onClick={loadMoreTopUps}>
                加载更多充值记录
              </button>
            )}
          </div>
        </section>

        <section className="sub-panel">
          <h2>资金记录</h2>
          <div className="record-list">
            {transactions.length === 0 && !loading && <p className="quiet-empty">暂无资金记录。</p>}
            {transactions.map((item) => (
              <article key={item.id} className="record-row">
                <div><strong>{item.type}</strong><small>{item.sourceType} · {dateTime(item.createTime)}</small></div>
                <div className="money-stack"><b>{currency(item.amount, wallet?.currency)}</b><small>余额 {currency(item.balanceAfter, wallet?.currency)}</small></div>
              </article>
            ))}
            {hasMoreTransactions && (
              <button className="button-secondary" disabled={loadingMore} onClick={loadMoreTransactions}>
                加载更多资金记录
              </button>
            )}
          </div>
        </section>
      </div>
    </section>
  );
}

