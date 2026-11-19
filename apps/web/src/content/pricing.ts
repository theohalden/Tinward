/** Plan definitions are separate from market-specific commercial prices.
 * Empty markets deliberately means no billing currency or price is offered yet. */
export const PLAN_DEFINITIONS = {
  free: { name: "Tinward Free", features: ["Import bank SMS and statements", "Searchable transaction history", "Automatic and custom categories", "Monthly spending insights"] },
  plus: { name: "Tinward Plus", features: ["Budgets and spending plans", "Upcoming bills and commitments", "Net worth with manual holdings"] },
} as const;
export type BillingMarket = { country: string; locale: string; currency: string; monthly: number; annual: number };
export const PRICING = { status: "proposed", plans: PLAN_DEFINITIONS, markets: [] as BillingMarket[], checkoutEnabled: false } as const;
