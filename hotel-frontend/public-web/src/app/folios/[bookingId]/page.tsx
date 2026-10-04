import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { FolioDetail } from "@/features/account/FolioDetail";

export default async function FolioPage({ params }: { params: Promise<{ bookingId: string }> }) {
  const { bookingId } = await params;
  return <ProtectedShell><main className="customer-page"><FolioDetail bookingId={Number(bookingId)} /></main></ProtectedShell>;
}

