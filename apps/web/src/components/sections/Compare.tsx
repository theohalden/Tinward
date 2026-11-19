import { Reveal } from "../Reveal";

type Row = { label: string; tinward: string; link: string; sheet: string; bank: string };
const COLS = ["Tinward", "Bank-linking apps", "Spreadsheets", "Your bank’s app"] as const;
const ROWS: Row[] = [
  { label: "Needs your bank login", tinward: "No", link: "Usually", sheet: "No", bank: "Yes (it’s your bank)" },
  { label: "Needs an account or sign-up", tinward: "No", link: "Usually", sheet: "No", bank: "Yes" },
  { label: "Where your data lives", tinward: "Only on your phone", link: "Usually on their servers", sheet: "Wherever you keep the file", bank: "With your bank" },
  { label: "Sorts transactions for you", tinward: "Yes", link: "Yes", sheet: "No, by hand", bank: "Varies" },
  { label: "Covers all your banks together", tinward: "Not yet: 3 Indian banks and UPI", link: "Often", sheet: "Yes, if you type it in", bank: "No, one bank only" },
  { label: "Budgets and net worth", tinward: "Coming soon", link: "Often", sheet: "If you build them", bank: "Rarely" },
  { label: "Sync across devices", tinward: "No", link: "Yes", sheet: "Via a cloud file", bank: "Yes" },
  { label: "Cost", tinward: "Free (Plus planned)", link: "Often a paid subscription", sheet: "Free", bank: "Free" },
  { label: "Source code you can inspect", tinward: "Yes", link: "Rarely", sheet: "Not applicable", bank: "No" },
];

export function Compare() {
  return (
    <section id="compare" className="shell section border-t border-line" aria-labelledby="compare-h">
      <div className="grid gap-12 lg:grid-cols-[0.7fr_1.3fr] lg:gap-20">
        <div>
          <Reveal><span className="eyebrow">How Tinward compares</span></Reveal>
          <Reveal delay={80}><h2 id="compare-h" className="h2 mt-6">Private by design. Honest about the trade-offs.</h2></Reveal>
          <Reveal delay={160}>
            <p className="lead mt-8">Tinward’s strength is privacy and simplicity: no bank login, no account, nothing uploaded. The cost is that it supports fewer banks and doesn’t sync between devices.</p>
          </Reveal>
          <Reveal delay={220}>
            <p className="mt-8 max-w-md border-t border-line pt-5 text-[0.95rem] text-ink-2"><strong className="text-ink">It may not be right for you yet if</strong> you bank outside HDFC, Kotak or Slice, want your data on several devices, or use an iPhone.</p>
          </Reveal>
        </div>
        <Reveal delay={100} className="min-w-0">
          <div className="overflow-x-auto rounded-[14px] border border-line bg-card" tabIndex={0} role="region" aria-label="Comparison table, scrolls sideways on small screens">
            <table className="w-full min-w-[640px] border-collapse text-left text-[0.92rem]">
              <caption className="sr-only">Tinward compared with bank-linking apps, spreadsheets and your bank’s own app</caption>
              <thead>
                <tr>
                  <th scope="col" className="p-4"><span className="sr-only">Feature</span></th>
                  {COLS.map((c, i) => (
                    <th key={c} scope="col" className={`p-4 align-bottom font-bold ${i === 0 ? "bg-ink text-paper" : "text-ink-2"}`}>{c}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {ROWS.map((r) => (
                  <tr key={r.label} className="border-t border-line">
                    <th scope="row" className="p-4 font-semibold text-ink">{r.label}</th>
                    <td className="bg-ink p-4 font-bold text-paper">{r.tinward}</td>
                    <td className="p-4 text-ink-2">{r.link}</td>
                    <td className="p-4 text-ink-2">{r.sheet}</td>
                    <td className="p-4 text-ink-2">{r.bank}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-4 text-xs text-ink-3">This compares types of apps, not specific products, so features vary. The Tinward column describes the current Android release.</p>
        </Reveal>
      </div>
    </section>
  );
}
