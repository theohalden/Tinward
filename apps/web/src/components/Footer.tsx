import Link from "next/link";
import { Logo } from "./Logo";

const COLUMNS: { title: string; links: [string, string][] }[] = [
  { title: "Product", links: [["Features", "/#features"], ["How it works", "/#how"], ["Compare", "/#compare"], ["Pricing", "/#pricing"]] },
  { title: "Resources", links: [["FAQ", "/#faq"], ["Download for Android", "/#download"]] },
  { title: "Legal", links: [["Privacy Policy", "/privacy"], ["Terms of Use", "/terms"]] },
];

export function Footer() {
  return (
    <footer className="site-footer on-ink">
      <div className="shell footer-inner">
        <div className="footer-top">
          <div className="footer-brand">
            <Logo />
            <p className="footer-tagline">Know where your money went.</p>
            <p className="footer-sub">Private personal finance for Android.</p>
            <Link href="/#download" className="btn btn-light footer-cta">Download for Android <span aria-hidden="true">↗</span></Link>
          </div>
          <nav aria-label="Footer" className="footer-cols">
            {COLUMNS.map((c) => (
              <div key={c.title}>
                <h2 className="footer-heading">{c.title}</h2>
                <ul>{c.links.map(([label, href]) => <li key={href}><Link href={href} className="footer-link"><span>{label}</span></Link></li>)}</ul>
              </div>
            ))}
          </nav>
        </div>
        <p className="footer-mark" aria-hidden="true">Tinward</p>
        <p className="footer-legal">© 2026 Tinward.</p>
      </div>
    </footer>
  );
}
