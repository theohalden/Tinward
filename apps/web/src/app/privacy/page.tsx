import type { Metadata } from "next";
import { StatusShell } from "@/components/StatusShell";
import { DeletionRequest } from "@/components/DeletionRequest";
import { REPO_URL } from "@/content/site";

export const metadata: Metadata = { title: "Privacy policy", description: "How the Tinward Android app and website handle your data: what stays on your phone, what the website collects, and how to delete it." };

const H = ({ children, id }: { children: React.ReactNode; id?: string }) => <h2 id={id} className="mt-12 scroll-mt-24 text-3xl">{children}</h2>;

export default function Privacy() {
  const staticPreview = process.env.TINWARD_STATIC_EXPORT === "1";
  return (
    <StatusShell wide>
      <article className="[&_li]:mt-2 [&_p]:mt-4 [&_ul]:mt-4 [&_ul]:list-disc [&_ul]:pl-6 text-[1.02rem] text-ink-2">
        <h1 className="text-5xl text-ink">Privacy policy</h1>
        <p>Last updated 19 November 2026. This policy covers two separate things: the <strong>Android app</strong> and this <strong>website</strong>.</p>

        <H>In short</H>
        <ul>
          <li>The app keeps your financial data <strong>on your phone</strong>. We never receive it.</li>
          <li>The app has no account, no analytics and no internet permission.</li>
          <li>The website collects an email address only if you join the early-access list, and you can delete it at any time.</li>
        </ul>

        <H>The Android app</H>
        <p>The app is open source and still in early release. This section describes what the current code does, and you can check it yourself in the <a className="underline" href={REPO_URL} target="_blank" rel="noopener noreferrer">source code</a>.</p>
        <h3 className="mt-8 text-xl text-ink">What the app stores</h3>
        <p>Transactions, the original text of the bank messages they came from, your category choices and the links between messages and statements are stored in a database on your device. Nothing is sent to us or to anyone else.</p>
        <h3 className="mt-8 text-xl text-ink">Permissions and why</h3>
        <ul>
          <li><code>READ_SMS</code>: reads your existing bank messages once, to build your transaction history.</li>
          <li><code>RECEIVE_SMS</code>: notices new transaction messages as they arrive.</li>
        </ul>
        <p>SMS access is optional. You can import a supported bank statement through Android&apos;s file picker instead, which needs no SMS permission. The app does not declare the <code>INTERNET</code> permission, so it cannot send data off your device.</p>
        <h3 className="mt-8 text-xl text-ink">What the app does not do</h3>
        <ul>
          <li>It does not ask for your bank login or connect to your bank.</li>
          <li>It has no account system and no analytics or advertising code.</li>
          <li>Android&apos;s app backup is switched off, so the database is not copied into device backups.</li>
        </ul>
        <h3 className="mt-8 text-xl text-ink">Current limits</h3>
        <ul>
          <li>The app does not encrypt its own database yet. Your phone&apos;s screen lock and device encryption protect it.</li>
          <li>There is no in-app export, backup or delete option yet. Uninstalling the app removes its local data, and clearing the app&apos;s storage does the same.</li>
          <li>The app uses the Manrope font through Google Play services, with a system-font fallback. That is a font download, not financial data.</li>
        </ul>

        {staticPreview ? <>
          <H>This website preview</H>
          <p>Email signup, deletion forms and other account APIs are disabled on this static preview. We do not collect early-access email addresses or send emails from this deployment.</p>
          <p>The temporary HTTPS connection is provided by Cloudflare. Cloudflare and the hosting infrastructure process network requests, including IP addresses, to deliver and protect the website. There are no advertising trackers or financial-data uploads.</p>
          <p>Downloading the APK takes you to GitHub, which handles that request under its own privacy policy.</p>
        </> : <>
        <H>The website: early-access list</H>
        <p>Joining the list is optional. If you do, we store:</p>
        <ul>
          <li>your <strong>email address</strong> (required);</li>
          <li>your <strong>optional interest</strong> choice;</li>
          <li>whether you ticked the optional <strong>product-news</strong> box, with the date and the wording you saw;</li>
          <li>when you signed up, confirmed your email, were invited, or unsubscribed.</li>
        </ul>
        <p>We never ask for bank details, phone numbers, SMS content or payment information. To limit abuse we briefly keep salted hashes of IP addresses, never the addresses themselves, in a rate-limit counter that expires within days.</p>
        <p><strong>Why we use it:</strong> to confirm your address, run the early-access list, and email you when there is news about Tinward, such as new releases or Plus. If you ticked the product-news box we may also send occasional product updates. We don&apos;t sell or share the list, and the website uses no advertising trackers.</p>
        <p><strong>Who handles it:</strong> our database host (Supabase) and email provider (Resend) process this data on our behalf.</p>
        <p><strong>How long we keep it:</strong> until the early-access programme ends, or until you unsubscribe or ask for deletion, whichever comes first.</p>

        <H>Unsubscribe</H>
        <p>Every email from us includes an unsubscribe link. Unsubscribing stops all email and clears your product-news consent.</p>

        <H id="delete">Delete my data</H>
        <p>Enter the address you signed up with. If it&apos;s on the list we&apos;ll email a confirmation link, and clicking it permanently deletes your record. We show the same message either way, so this form can&apos;t be used to check who is on the list.</p>
        <div className="mt-5"><DeletionRequest /></div>
        </>}

        <H>Downloading the app</H>
        <p>The APK is hosted on GitHub. When you download it, GitHub processes that request under its own privacy policy. We do not receive anything about it beyond what GitHub reports as aggregate download counts.</p>

        <H>Changes to this policy</H>
        <p>If the app or website changes how it handles data, we will update this page and its date before the change takes effect.</p>

        <H>Contact</H>
        <p>For questions that don&apos;t involve personal data, open an issue on <a className="underline" href={`${REPO_URL}/issues`} target="_blank" rel="noopener noreferrer">GitHub</a>. {staticPreview ? "Please do not post personal financial information in public issues." : "To have your website data deleted, use the form above."}</p>
      </article>
    </StatusShell>
  );
}
