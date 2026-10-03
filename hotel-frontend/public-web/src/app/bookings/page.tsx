import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { BookingList } from "@/features/booking/BookingList";
export default function BookingsPage() { return <ProtectedShell><main className="customer-page"><BookingList /></main></ProtectedShell>; }
