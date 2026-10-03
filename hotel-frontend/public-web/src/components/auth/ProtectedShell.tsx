"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/AuthProvider";
export function ProtectedShell({ children }: { children: React.ReactNode }) {
  const { session, ready } = useAuth(); const router = useRouter();
  useEffect(() => { if (ready && !session) router.replace(`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`); }, [ready, session, router]);
  if (!ready || !session) return <main className="center-state">正在确认账户…</main>;
  return <>{children}</>;
}
