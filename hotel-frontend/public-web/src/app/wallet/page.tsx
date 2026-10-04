import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { WalletPanel } from "@/features/account/WalletPanel";

export default function WalletPage() {
  return <ProtectedShell><main className="customer-page"><WalletPanel /></main></ProtectedShell>;
}

