import { Reveal } from "../Reveal";

const FACTS = [
  { k: "Stored on your phone", v: "Your transactions, the original message text and your categories live in a database on your device." },
  { k: "No internet permission", v: "The app doesn’t ask for internet access. It has no analytics and no uploads, so your data has nowhere to go." },
  { k: "No account", v: "There’s no sign-up, and no Tinward server holding your money data." },
  { k: "Not in backups", v: "Android’s app backup is switched off, so the database isn’t copied to cloud backups." },
];

export function Privacy() {
  return (
    <section id="privacy" className="on-ink bg-ink text-paper" aria-labelledby="privacy-h">
      <div className="shell section">
        <div className="grid gap-14 lg:grid-cols-[0.85fr_1.15fr] lg:gap-24">
          <div className="lg:sticky lg:top-28 lg:self-start">
            <Reveal><span className="eyebrow !text-paper/60">Privacy</span></Reveal>
            <Reveal delay={80}><h2 id="privacy-h" className="h2 mt-6">Your money stays on your phone.</h2></Reveal>
            <Reveal delay={160}><p className="lead mt-8 !text-paper/70">Tinward works entirely on your device: no bank login, no account, no cloud copy of your finances. The app is open source, so you can check the code instead of taking our word for it.</p></Reveal>
            <Reveal delay={220}>
              <a href="/privacy" className="mt-8 inline-flex min-h-11 items-center gap-2 border-b border-paper/50 pb-1 font-bold text-paper no-underline hover:border-paper">
                Read the privacy policy <span aria-hidden="true">→</span>
              </a>
            </Reveal>
          </div>
          <Reveal>
            <dl className="divide-y divide-paper/15 border-y border-paper/15">
              {FACTS.map((f) => (
                <div key={f.k} className="grid gap-2 py-6 sm:grid-cols-[14rem_1fr] sm:gap-8">
                  <dt className="display text-[1.55rem] leading-tight">{f.k}</dt>
                  <dd className="text-[0.98rem] text-paper/65">{f.v}</dd>
                </div>
              ))}
            </dl>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
