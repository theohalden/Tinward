import type { Metadata, Viewport } from "next";
import { Instrument_Serif, Manrope } from "next/font/google";
import "./globals.css";

const instrument = Instrument_Serif({ subsets: ["latin"], weight: "400", style: ["normal", "italic"], variable: "--font-instrument", display: "swap" });
const manrope = Manrope({ subsets: ["latin"], variable: "--font-manrope", display: "swap" });

const siteUrl = process.env.NEXT_PUBLIC_SITE_URL || (process.env.TINWARD_STATIC_EXPORT === "1" ? undefined : "http://localhost:3001");

export const metadata: Metadata = {
  ...(siteUrl ? { metadataBase: new URL(siteUrl) } : {}),
  title: { default: "Tinward — Know where your money went, privately", template: "%s · Tinward" },
  description: "Tinward is a free, private personal finance app for Android. It turns your bank SMS alerts and statements into a clear view of your spending, on your phone, with no bank login.",
  openGraph: { type: "website", siteName: "Tinward", title: "Tinward — Know where your money went, privately", description: "A free Android app that sorts your bank SMS and statements into a clear view of your spending. No bank login, no account, nothing leaves your phone.", locale: "en_US" },
  twitter: { card: "summary_large_image", title: "Tinward — Know where your money went, privately", description: "A free, private Android app that shows where your money goes. No bank login, no account." },
  ...(siteUrl ? { alternates: { canonical: "/" } } : {}),
};

export const viewport: Viewport = { themeColor: "#f7f5f0", width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning className={`${instrument.variable} ${manrope.variable}`}>
      <head><script dangerouslySetInnerHTML={{__html: "try{if(sessionStorage.getItem('tw-intro'))document.documentElement.classList.add('seen');else sessionStorage.setItem('tw-intro','1')}catch(e){document.documentElement.classList.add('seen')}"}} /></head>
      <body>{children}</body>
    </html>
  );
}
