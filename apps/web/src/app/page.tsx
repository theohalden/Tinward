import { MarketInit } from "@/components/MarketInit";
import { Intro } from "@/components/Intro";
import { Nav } from "@/components/Nav";
import { Download } from "@/components/sections/Download";
import { Guide } from "@/components/Guide";
import { Footer } from "@/components/Footer";
import { Hero } from "@/components/hero/Hero";
import { Problem } from "@/components/sections/Problem";
import { Showcase } from "@/components/sections/Showcase";
import { SmsTracking } from "@/components/sections/SmsTracking";
import { Insights } from "@/components/sections/Insights";
import { SalaryBudget } from "@/components/sections/SalaryBudget";
import { NetWorth } from "@/components/sections/NetWorth";
import { Compare } from "@/components/sections/Compare";
import { HowItWorks } from "@/components/sections/HowItWorks";
import { Privacy } from "@/components/sections/Privacy";
import { Pricing } from "@/components/sections/Pricing";
import { EarlyAccess } from "@/components/sections/EarlyAccess";
import { Faq } from "@/components/sections/Faq";
import { FAQS, REPO_URL } from "@/content/site";

const jsonLd = {
  "@context": "https://schema.org",
  "@graph": [
    { "@type": "SoftwareApplication", name: "Tinward", applicationCategory: "FinanceApplication", operatingSystem: "Android", description: "Free personal finance app for Android that turns bank SMS alerts and statements into a private, on-device view of your spending. Early release.", sameAs: [REPO_URL] },
    { "@type": "FAQPage", mainEntity: FAQS.map((f) => ({ "@type": "Question", name: f.q, acceptedAnswer: { "@type": "Answer", text: f.a } })) },
  ],
};

export default function Home() {
  return (
    <>
      <MarketInit />
      <Intro />
      <Nav />
      <main id="main" tabIndex={-1} className="focus:outline-none">
        <Hero />
        <Download />
        <Problem />
        <HowItWorks />
        <Showcase />
        <SmsTracking />
        <Insights />
        <SalaryBudget />
        <NetWorth />
        <Compare />
        <Privacy />
        <Pricing />
        <EarlyAccess enabled={process.env.TINWARD_STATIC_EXPORT !== "1"} />
        <Faq />
      </main>
      <Footer />
      <Guide />
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, "\\u003c") }} />
    </>
  );
}
