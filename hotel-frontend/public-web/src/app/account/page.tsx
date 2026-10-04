import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { AccountOverview } from "@/features/account/AccountOverview";

export default function AccountPage() {
  return <ProtectedShell><main className="customer-page"><AccountOverview /></main></ProtectedShell>;
}
