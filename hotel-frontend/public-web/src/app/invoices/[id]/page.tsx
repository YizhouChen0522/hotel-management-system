import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { InvoiceDetail } from "@/features/account/InvoiceDetail";

export default async function InvoicePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ProtectedShell><main className="customer-page"><InvoiceDetail id={Number(id)} /></main></ProtectedShell>;
}
