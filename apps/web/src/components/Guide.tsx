"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { FAQS } from "@/content/site";
import "./guide.css";

/**
 * "Tin": a friendly on-site guide. It is NOT a live AI model: it answers common questions
 * from the site's own FAQ and scrolls to sections. The panel says so.
 */
type Msg = { from: "tin" | "you"; text: string };
const faq = (start: string) => FAQS.find((f) => f.q.startsWith(start))?.a ?? "";
const TOPICS: { label: string; answer: () => string }[] = [
  { label: "What is Tinward?", answer: () => faq("What is Tinward") },
  { label: "Is my data private?", answer: () => faq("Where is my data stored") },
  { label: "Which banks work?", answer: () => faq("Which banks") },
  { label: "Is it free?", answer: () => faq("Is it free") },
  { label: "Why not on Google Play?", answer: () => faq("Why isn’t it on Google Play") },
];
const GO: { label: string; target: string; say: string }[] = [
  { label: "Show me the app", target: "product", say: "Here’s the app, screen by screen." },
  { label: "Compare with other apps", target: "compare", say: "Here’s an honest comparison, including where Tinward falls short." },
  { label: "Take me to the download", target: "download", say: "The download button is right here." },
];
const HELLO: Msg = { from: "tin", text: "Hi, I’m Tin. Ask me about Tinward, or let me show you around." };

/** Tin is the Tinward mark brought to life: the ink "T" on an app-icon tile, with the gold square as a small face that looks, blinks and squints. */
function Face({ look }: { look?: boolean }) {
  return (
    <svg viewBox="0 0 64 64" width="100%" height="100%" aria-hidden="true" className="tin-face" data-look={look ? "1" : undefined}>
      <defs>
        <linearGradient id="tin-tile" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#262925" /><stop offset="1" stopColor="#101210" /></linearGradient>
        <linearGradient id="tin-gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#f0b54a" /><stop offset="1" stopColor="#c98a1e" /></linearGradient>
      </defs>
      <rect x=".75" y=".75" width="62.5" height="62.5" rx="18" fill="url(#tin-tile)" stroke="#f7f5f0" strokeOpacity=".22" strokeWidth="1.5" />
      <path d="M24 15H212Q218 15 218 21V228Q218 268 178 268H132Q125 268 125 261V98H18Q10 98 10 90V29Q10 15 24 15Z" transform="translate(8.500 12) scale(.158)" fill="#f7f5f0" />
      <g className="tin-sq">
        <rect x="33" y="9" width="23" height="23" rx="6.500" fill="url(#tin-gold)" />
        <g className="tin-look">
          <g className="tin-eyes">
            <rect x="39" y="16" width="3" height="7.500" rx="1.500" fill="#121412" />
            <rect x="47" y="16" width="3" height="7.500" rx="1.500" fill="#121412" />
          </g>
        </g>
      </g>
    </svg>
  );
}

export function Guide() {
  const [ready, setReady] = useState(false);
  const [open, setOpen] = useState(false);
  const [nudge, setNudge] = useState<string | null>(null);
  const [msgs, setMsgs] = useState<Msg[]>([HELLO]);
  const btn = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const list = useRef<HTMLDivElement>(null);
  const reduced = useRef(false);
  const openRef = useRef(false);
  const nudgeRef = useRef(false);

  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    reduced.current = media.matches;
    const update = () => { reduced.current = media.matches; };
    media.addEventListener("change", update);
    const show = window.setTimeout(() => setReady(true), 3200);
    const read = (k: string) => { try { return sessionStorage.getItem(k); } catch { return null; } };
    const write = (k: string, v: string) => { try { sessionStorage.setItem(k, v); } catch {} };
    const say = (text: string, ms: number) => {
      if (read("tw-guide-quiet") === "1" || openRef.current || nudgeRef.current) return false;
      nudgeRef.current = true; setNudge(text);
      window.setTimeout(() => { nudgeRef.current = false; setNudge(null); }, ms);
      return true;
    };
    /* One friendly hello, then at most a few short reactions as sections come into view, each shown once. */
    const hello = read("tw-guide-hello") === "1" ? 0 : window.setTimeout(() => { if (say("Hi, I’m Tin. Questions about Tinward? Tap me.", 8000)) write("tw-guide-hello", "1"); }, 9000);
    const seen: string[] = (() => { try { return JSON.parse(read("tw-guide-seen") || "[]"); } catch { return []; } })();
    const LINES: Record<string, string> = {
      download: "One tap and the APK is yours.",
      compare: "Honest table: it shows where we lose too.",
      privacy: "Short version: nothing leaves your phone.",
    };
    const io = new IntersectionObserver((entries) => {
      for (const e of entries) {
        const id = e.target.id;
        if (!e.isIntersecting || seen.includes(id) || !LINES[id]) continue;
        if (say(LINES[id], 5500)) { seen.push(id); write("tw-guide-seen", JSON.stringify(seen)); }
      }
    }, { threshold: 0.45 });
    Object.keys(LINES).forEach((id) => { const el = document.getElementById(id); if (el) io.observe(el); });
    /* An occasional hop while idle and closed. */
    const hop = window.setInterval(() => { const el = btn.current; if (!el || openRef.current || reduced.current) return; el.classList.remove("tin-hop"); void el.offsetWidth; el.classList.add("tin-hop"); }, 22000);
    return () => { media.removeEventListener("change", update); clearTimeout(show); if (hello) clearTimeout(hello); io.disconnect(); clearInterval(hop); };
  }, []);

  /* Eyes follow the pointer (mouse only, never with reduced motion). */
  useEffect(() => {
    if (!ready) return;
    let raf = 0;
    const onMove = (e: PointerEvent) => {
      if (e.pointerType !== "mouse" || reduced.current) return;
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => {
        const el = btn.current; if (!el) return;
        const r = el.getBoundingClientRect();
        const dx = e.clientX - (r.left + r.width / 2), dy = e.clientY - (r.top + r.height / 2);
        const d = Math.hypot(dx, dy) || 1, k = Math.min(1, d / 260) * 3.2;
        el.style.setProperty("--ex", `${(dx / d) * k}px`);
        el.style.setProperty("--ey", `${(dy / d) * k}px`);
      });
    };
    window.addEventListener("pointermove", onMove, { passive: true });
    return () => { window.removeEventListener("pointermove", onMove); cancelAnimationFrame(raf); };
  }, [ready]);

  useEffect(() => { openRef.current = open; }, [open]);
  useEffect(() => { list.current?.scrollTo({ top: list.current.scrollHeight, behavior: reduced.current ? "auto" : "smooth" }); }, [msgs, open]);

  const close = useCallback(() => { setOpen(false); btn.current?.focus(); }, []);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") close(); };
    document.addEventListener("keydown", onKey);
    panel.current?.focus();
    return () => document.removeEventListener("keydown", onKey);
  }, [open, close]);

  const ask = (t: (typeof TOPICS)[number]) => setMsgs((m) => [...m, { from: "you", text: t.label }, { from: "tin", text: t.answer() }]);
  const go = (g: (typeof GO)[number]) => {
    setMsgs((m) => [...m, { from: "you", text: g.label }, { from: "tin", text: g.say }]);
    const el = document.getElementById(g.target);
    if (el) {
      if (window.innerWidth < 640) setOpen(false);
      el.scrollIntoView({ behavior: reduced.current ? "auto" : "smooth", block: "start" });
    }
  };

  if (!ready) return null;
  return (
    <div className="tin" data-open={open}>
      {open && (
        <div ref={panel} tabIndex={-1} role="dialog" aria-modal="false" aria-labelledby="tin-title" className="tin-panel">
          <header className="tin-head">
            <span className="tin-avatar"><Face /></span>
            <div><h2 id="tin-title">Tin</h2><p>Quick answers about Tinward</p></div>
            <button type="button" className="tin-x" onClick={close} aria-label="Close Tin"><svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="m2 2 10 10M12 2 2 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg></button>
          </header>
          <div ref={list} className="tin-list" role="log" aria-live="polite" aria-label="Conversation with Tin">
            {msgs.map((m, i) => <p key={i} className={`tin-msg tin-${m.from}`}>{m.text}</p>)}
          </div>
          <div className="tin-chips" role="group" aria-label="Ask a question">
            {TOPICS.map((t) => <button key={t.label} type="button" onClick={() => ask(t)}>{t.label}</button>)}
          </div>
          <div className="tin-chips tin-go" role="group" aria-label="Go to a section">
            {GO.map((g) => <button key={g.label} type="button" onClick={() => go(g)}>{g.label} <span aria-hidden="true">→</span></button>)}
          </div>
          <p className="tin-foot">Tin isn’t a live AI. Answers come from our <a href="#faq" onClick={() => { if (window.innerWidth < 640) setOpen(false); }}>FAQ</a>.</p>
        </div>
      )}
      {!open && nudge && (
        <div className="tin-nudge" role="status">
          <button type="button" className="tin-nudge-x" onClick={() => { setNudge(null); nudgeRef.current = false; try { sessionStorage.setItem("tw-guide-quiet", "1"); } catch {} }} aria-label="Dismiss and stop these tips">×</button>
          <p>{nudge}</p>
        </div>
      )}
      <button ref={btn} type="button" className="tin-btn" aria-expanded={open} aria-label={open ? "Close Tin, the site guide" : "Open Tin, the site guide"} onClick={() => { setOpen((o) => !o); setNudge(null); nudgeRef.current = false; }}>
        <Face />
      </button>
    </div>
  );
}
