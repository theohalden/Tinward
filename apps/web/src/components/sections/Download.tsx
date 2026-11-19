import { ANDROID_RELEASE } from "@/content/download";
import { Reveal } from "../Reveal";

export function Download() {
  return (
    <section id="download" className="shell section border-t border-line" aria-labelledby="download-heading">
      <Reveal>
        <span className="eyebrow">Android early release</span>
        <h2 id="download-heading" className="h2 mt-6">Get Tinward on your Android phone.</h2>
        <p className="mt-6 max-w-2xl text-ink-2">Download the app, open the file, and start tracking. It installs on your phone as “Kosh”, the Android app that powers Tinward.</p>
        <div className="mt-8">
          <a className="btn btn-primary" href={ANDROID_RELEASE.apkUrl} download>Download Android APK <span aria-hidden="true">↓</span></a>
        </div>
        <p className="mt-4 text-sm text-ink-2">Version {ANDROID_RELEASE.version} · Android {ANDROID_RELEASE.minimumAndroid}+ · Free</p>
        <p className="mt-6 max-w-2xl text-sm text-ink-2">Already have an earlier Kosh installed? Keep it: this version can’t update over it, and uninstalling erases its data.</p>
      </Reveal>
    </section>
  );
}
