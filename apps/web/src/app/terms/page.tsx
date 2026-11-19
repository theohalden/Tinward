import type { Metadata } from "next";
import { StatusShell } from "@/components/StatusShell";
import { REPO_URL } from "@/content/site";

export const metadata: Metadata = { title: "Terms of use", description: "The terms for using the Tinward website and Android app." };

export default function Terms() {
  return (
    <StatusShell wide>
      <article className="text-[1.02rem] text-ink-2 [&_h2]:mt-12 [&_h2]:text-3xl [&_h2]:text-ink [&_li]:mt-2 [&_p]:mt-4 [&_ul]:mt-4 [&_ul]:list-disc [&_ul]:pl-6">
        <h1 className="text-5xl text-ink">Terms of use</h1>
        <p>Last updated 19 November 2026. By downloading the Tinward Android app or using this website, you agree to these terms.</p>

        <h2>What Tinward is</h2>
        <p>Tinward is a personal finance tracker for Android. It helps you organise and understand your own transactions. It is not a bank, does not hold or move your money, and does not connect to your bank accounts.</p>

        <h2>Not financial advice</h2>
        <p>Tinward and this website provide information and tools, not financial, investment, tax or legal advice. Decisions about your money are yours.</p>

        <h2>Early release, and accuracy</h2>
        <ul>
          <li>The app is an early release. Features may change, break or be removed.</li>
          <li>Reading bank messages and statements is imperfect. Transactions can be missed, duplicated or put in the wrong category.</li>
          <li>Check totals against your official bank records before relying on them for any decision.</li>
          <li>Only specific bank message and statement formats are supported. See the website for the current list.</li>
        </ul>

        <h2>Your data and your device</h2>
        <p>Your financial data stays on your phone, as described in the <a className="underline" href="/privacy">privacy policy</a>. You are responsible for keeping your device secure and for any backups you want, because the app does not yet offer export or backup. Uninstalling the app removes its data.</p>

        <h2>Installing outside Google Play</h2>
        <p>The app is distributed as an APK from this website, not from Google Play. Installing it requires allowing installs from your browser or file manager. Only download it from this website or the official project repository.</p>

        <h2>Using the app</h2>
        <p>Use the app only for your own finances and in line with the law. Do not attempt to copy, resell or misrepresent it as your own, except as the open-source licence below allows.</p>

        <h2>Open-source licence</h2>
        <p>The app&apos;s source code is released under the MIT licence, available in the <a className="underline" href={REPO_URL} target="_blank" rel="noopener noreferrer">project repository</a>. That licence governs your use of the code. The Tinward name and logo are not licensed for use as your own branding.</p>

        <h2>Free use, and Tinward Plus</h2>
        <p>The current app is free. A paid plan, Tinward Plus, is planned but not available. Its features and price are not final, and no payment is taken on this website. If paid plans launch, their terms, including billing and refunds, will be shown clearly before you pay, and these terms will be updated.</p>

        <h2>Early-access list</h2>
        <p>Joining the early-access email list is free, optional and does not guarantee any feature, release or date. You can unsubscribe at any time.</p>

        <h2>Sample content on this website</h2>
        <p>Amounts, names and screens on this website are sample data, not real people&apos;s information. Features marked &ldquo;coming soon&rdquo; are not available yet, and the app may look different from the illustrations.</p>

        <h2>No warranty and limits of liability</h2>
        <p>The app and website are provided &ldquo;as is&rdquo;, without warranties of any kind. To the extent the law allows, Tinward and its contributors are not liable for losses arising from your use of the app, including from incorrect totals, missed transactions or lost data.</p>

        <h2>Changes to these terms</h2>
        <p>We may update these terms as the product develops. The date above shows the latest version. Continuing to use the app or website after a change means you accept the updated terms.</p>

        <h2>Contact</h2>
        <p>Questions about these terms that don&apos;t involve personal data can be raised as an issue on <a className="underline" href={`${REPO_URL}/issues`} target="_blank" rel="noopener noreferrer">GitHub</a>.</p>
      </article>
    </StatusShell>
  );
}
