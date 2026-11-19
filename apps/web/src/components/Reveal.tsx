"use client";

import type { CSSProperties, ElementType, ReactNode } from "react";
import { useInView } from "@/lib/useInView";

export function Reveal({ children, delay = 0, as: Tag = "div", className = "" }: { children: ReactNode; delay?: number; as?: ElementType; className?: string }) {
  const [ref, seen] = useInView<HTMLElement>(0.15);
  return (
    <Tag ref={ref} className={`reveal ${seen ? "in" : ""} ${className}`} style={{ "--d": `${delay}ms` } as CSSProperties}>
      {children}
    </Tag>
  );
}

export function PlannedChip({ children = "Coming soon" }: { children?: ReactNode }) {
  return <span className="status"><i className="dev" />{children}</span>;
}
export function LiveChip({ children = "Available now" }: { children?: ReactNode }) {
  return <span className="status"><i className="live" />{children}</span>;
}
export function IllusChip() {
  return null;
}
