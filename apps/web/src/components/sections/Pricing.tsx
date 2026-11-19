import { Reveal } from "../Reveal";
import { PRICING } from "@/content/pricing";

const COPY = {
  free: { label: "AVAILABLE NOW", state: "Everything you need to track and understand your spending.", cta: "Download for Android", href: "#download", icon: "↓", btn: "btn-ghost" },
  plus: { label: "PLANNED", state: "For people who want to plan ahead, not just look back.", cta: "Get notified about Plus", href: "#early-access", icon: "↗", btn: "btn-light" },
} as const;

export function Pricing() {
  const staticExport = process.env.TINWARD_STATIC_EXPORT === "1";
  return (
    <section id="pricing" className="shell section" aria-labelledby="pricing-h">
      <div className="grid gap-12 lg:grid-cols-[.8fr_1.2fr] lg:gap-20">
        <div>
          <Reveal>
            <span className="eyebrow">Pricing</span>
            <h2 id="pricing-h" className="h2 mt-6">Free to use. Plus is planned.</h2>
            <p className="lead mt-8">Tracking your spending is free. Tinward Plus is planned for people who want budgets and net worth as well.</p>
            <p className="mt-6 text-sm text-ink-2">Plus has no price or launch date yet, and which features it includes isn’t final. Nothing is charged on this website.</p>
          </Reveal>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          {(Object.entries(PRICING.plans) as [keyof typeof COPY, (typeof PRICING.plans)[keyof typeof PRICING.plans]][]).map(([key, plan]) => {
            const c = COPY[key];
            return (
              <Reveal key={key}>
                <article className={`pricing-plan ${key === "plus" ? "pricing-plus on-ink" : "paper-card"}`}>
                  <p className="pricing-label">{c.label}</p>
                  <h3>{plan.name}</h3>
                  <p className="pricing-state">{c.state}</p>
                  <ul>{plan.features.map((f) => <li key={f}><span aria-hidden="true">↗</span>{f}</li>)}</ul>
                  {key === "plus" && staticExport ? <span className="pricing-soon">Planned, not yet available</span> : <a href={c.href} className={`btn ${c.btn}`}>{c.cta} <span aria-hidden="true">{c.icon}</span></a>}
                </article>
              </Reveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
