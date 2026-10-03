import { ProtectedShell } from "@/components/auth/ProtectedShell";
import { BookingFlow } from "@/features/booking/BookingFlow";

export default function BookingPage() {
  return <ProtectedShell><main className="customer-page"><BookingFlow /></main></ProtectedShell>;
}
