import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { BookingDetail } from "@/features/booking/BookingDetail";
export default async function BookingDetailPage({ params }: { params: Promise<{ id: string }> }) { const { id } = await params; return <ProtectedShell><main className="customer-page"><BookingDetail id={Number(id)} /></main></ProtectedShell>; }
