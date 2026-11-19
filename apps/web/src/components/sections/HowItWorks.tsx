import { Reveal } from "../Reveal";

const STEPS = [
  { n: "01", title: "Download and install.", body: "Get the APK from this page and open it. Android may ask you to allow installs from your browser, once." },
  { n: "02", title: "Choose how to import.", body: "Allow SMS access so Tinward can read your bank alerts, or import a bank statement instead. Either way, everything stays on your phone." },
  { n: "03", title: "Review and explore.", body: "Check the few transactions Tinward couldn’t categorise, then see where your money goes each month." },
];

export function HowItWorks() {
  return (
    <section id="how" className="shell section border-t border-line" aria-labelledby="how-h">
      <div className="grid gap-12 lg:grid-cols-[0.8fr_1.2fr] lg:gap-24">
        <div>
          <Reveal><span className="eyebrow">How it works</span></Reveal>
          <Reveal delay={80}><h2 id="how-h" className="h2 mt-6">Up and running in three steps.</h2></Reveal>
        </div>
        <ol className="divide-y divide-line border-y border-line">
          {STEPS.map((s, i) => (
            <li key={s.n}>
              <Reveal delay={i * 70} className="grid gap-3 py-8 sm:grid-cols-[5rem_1fr] sm:gap-8">
                <span className="display text-[2.2rem] leading-none text-coral" aria-hidden="true">{s.n}</span>
                <div>
                  <h3 className="text-[clamp(1.6rem,2.6vw,2.2rem)] leading-tight tracking-[-0.02em]"><span className="sr-only">Step {i + 1}: </span>{s.title}</h3>
                  <p className="mt-3 max-w-lg text-ink-2">{s.body}</p>
                </div>
              </Reveal>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
