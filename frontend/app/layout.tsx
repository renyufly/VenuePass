import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "VenuePass · 活动预约演示",
  description: "VenuePass 活动预约与入场核销演示平台",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
