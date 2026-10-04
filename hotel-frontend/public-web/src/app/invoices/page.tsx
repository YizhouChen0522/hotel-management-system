import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { InvoiceList } from "@/features/account/InvoiceList";

export default function InvoicesPage() {
  return <ProtectedShell><main className="customer-page"><InvoiceList /></main></ProtectedShell>;
}

