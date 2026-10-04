import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { FolioList } from "@/features/account/FolioList";

export default function FoliosPage() {
  return <ProtectedShell><main className="customer-page"><FolioList /></main></ProtectedShell>;
}

