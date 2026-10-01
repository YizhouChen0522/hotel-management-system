import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = { title: "Hotel", description: "Hotel public website" };
export default function RootLayout({ children }: LayoutProps<"/">) {
  return <html lang="zh-CN"><body className="flex min-h-screen flex-col">{children}</body></html>;
}
