"use client";
import { useEffect } from "react";

/** Gentle scroll parallax for the hero stage. Writes one CSS variable; skipped for reduced motion. */
export function HeroScroll() {
  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const root = document.getElementById("top");
    if (!root || media.matches) return;
    let raf = 0;
    const onScroll = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => { root.style.setProperty("--sy", String(Math.min(window.scrollY, 900))); });
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => { cancelAnimationFrame(raf); window.removeEventListener("scroll", onScroll); };
  }, []);
  return null;
}
