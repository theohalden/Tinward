import "./hero.css";
import { Tilt } from "../Tilt";
import { AppPhone } from "../app/AppPhone";
import { HomeScreen } from "../app/Screens";
import { HeroScroll } from "./HeroScroll";

export function Hero() {
  return (
    <section className="hero-wrap on-ink" id="top" aria-labelledby="hero-heading">
      <HeroScroll />
      <div className="hero-glow" aria-hidden="true" />
      <div className="hero-rings" aria-hidden="true"><i /><i /><i /></div>
      <div className="shell hero">
        <p className="hero-kicker">Tinward for Android</p>
        <h1 id="hero-heading" className="hero-heading">Know where your money went.<br /><em>Privately.</em></h1>
        <p className="hero-sub">Your bank messages, sorted on your phone.</p>
        <div className="hero-actions">
          <a href="#download" className="btn btn-light">Download for Android <span aria-hidden="true">↓</span></a>
        </div>
        <p className="hero-note">Free · No account · Nothing leaves your phone</p>
        <div className="hero-stage">
          <div className="hero-phone"><Tilt><AppPhone label="Tinward Home concept with synthetic financial figures"><HomeScreen /></AppPhone></Tilt></div>
        </div>
      </div>
    </section>
  );
}
