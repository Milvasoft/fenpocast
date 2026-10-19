"""Google Play art for Fenpo Cast: icon, feature graphic, TV banner and TV screenshots.

Run from the repository root after `./gradlew :app:recordRoborazziDebug`:

    python store/build.py

Screenshots are the app's own Roborazzi renders (app/src/test/screenshots), dark theme, 1920x1080.
Gabarito (SIL Open Font License) is used only for the brand text, as in Fenpo.
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SHOTS = ROOT.parent / "app" / "src" / "test" / "screenshots"
OUT = ROOT / "out"

MUREKKEP = "#1B1F3B"
CICEK = "#E8577E"
KREM = "#FFF6F0"
CAM = "#DCE6F5"
S = 4  # draw at 4x, then downsample

TEXT = {
    "tr": {"tagline": "iPhone ekranın, müziğin ve videoların TV'de", "sub": "Kurulum yok, kablo yok. Aynı Wi-Fi yeter."},
    "en": {"tagline": "Your iPhone's screen, music and videos on your TV", "sub": "Nothing to install, no cables. Just the same Wi-Fi."},
}
SCREENS = ["home_ready", "music", "pin", "settings", "notice_connected"]


def font(weight, size):
    return ImageFont.truetype(str(ROOT / "fonts" / f"Gabarito-{weight}.ttf"), size)


def bezier(p0, p1, p2, p3, n=40):
    return [
        (
            (1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t * t * p2[0] + t**3 * p3[0],
            (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t * t * p2[1] + t**3 * p3[1],
        )
        for t in (i / n for i in range(n + 1))
    ]


SEED = bezier((32, 15.5), (35.4, 19.7), (35.7, 24.5), (32, 27.1)) + bezier((32, 27.1), (28.3, 24.5), (28.6, 19.7), (32, 15.5))


def draw_logo(draw, x, y, size, square=True):
    """Çekirdek (Fenpo repo, design/logo/svg/1-cekirdek.svg) at (x, y), size px wide."""
    k = size / 64

    def tx(px, py):
        return (x + px * k, y + py * k)

    if square:
        draw.rounded_rectangle([tx(3, 3), tx(61, 61)], radius=19 * k, fill=CICEK)
    draw.ellipse([tx(11, 11), tx(53, 53)], fill=KREM)
    for r in range(5):
        a = math.radians(72 * r)
        pts = [tx(32 + (px - 32) * math.cos(a) - (py - 32) * math.sin(a), 32 + (px - 32) * math.sin(a) + (py - 32) * math.cos(a)) for px, py in SEED]
        draw.polygon(pts, fill=MUREKKEP)


def canvas(w, h, color=MUREKKEP):
    img = Image.new("RGB", (w * S, h * S), color)
    return img, ImageDraw.Draw(img)


def finish(img, w, h, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.resize((w, h), Image.LANCZOS).save(path, optimize=True)
    print(path.relative_to(ROOT))


def icon():
    # Play wants a full-bleed square; it rounds the corners itself.
    img, d = canvas(512, 512, CICEK)
    draw_logo(d, -64 * S, -64 * S, 640 * S, square=False)
    finish(img, 512, 512, OUT / "icon-512.png")


def wordmark(d, x, y, size):
    d.text((x, y), "Fenpo", font=font(700, size), fill=KREM)
    w = d.textlength("Fenpo ", font=font(700, size))
    d.text((x + w, y), "Cast", font=font(700, size), fill=CICEK)


def tv_banner():
    w, h = 1280, 720
    img, d = canvas(w, h)
    draw_logo(d, 150 * S, 210 * S, 300 * S)
    d.text((510 * S, 215 * S), "Fenpo", font=font(700, 150 * S), fill=KREM)
    d.text((510 * S, 365 * S), "Cast", font=font(700, 150 * S), fill=CICEK)
    finish(img, w, h, OUT / "tv-banner-1280x720.png")


def feature(lang):
    w, h = 1024, 500
    img, d = canvas(w, h)
    draw_logo(d, 64 * S, 64 * S, 84 * S)
    wordmark(d, 168 * S, 76 * S, 54 * S)
    tagline = TEXT[lang]["tagline"]
    f = font(700, 40 * S)
    words, lines, line = tagline.split(), [], ""
    for word in words:
        trial = (line + " " + word).strip()
        if d.textlength(trial, font=f) > 470 * S and line:
            lines.append(line)
            line = word
        else:
            line = trial
    lines.append(line)
    y = 200 * S
    for text in lines:
        d.text((64 * S, y), text, font=f, fill=KREM)
        y += 50 * S
    d.text((64 * S, y + 16 * S), TEXT[lang]["sub"], font=font(500, 20 * S), fill=CAM)
    # A TV showing the app's home screen.
    shot = Image.open(SHOTS / f"home_ready_{lang}_dark.png").convert("RGB")
    tv_w, tv_h = 400, 225
    x0, y0 = 584, 120
    d.rounded_rectangle([(x0 - 10) * S, (y0 - 10) * S, (x0 + tv_w + 10) * S, (y0 + tv_h + 10) * S], radius=14 * S, fill="#0B0D1C", outline="#3A3F63", width=2 * S)
    img.paste(shot.resize((tv_w * S, tv_h * S), Image.LANCZOS), (x0 * S, y0 * S))
    d.rounded_rectangle([(x0 + tv_w / 2 - 50) * S, (y0 + tv_h + 22) * S, (x0 + tv_w / 2 + 50) * S, (y0 + tv_h + 30) * S], radius=4 * S, fill="#3A3F63")
    finish(img, w, h, OUT / f"feature-graphic-{lang}.png")


def screenshots(lang):
    for i, name in enumerate(SCREENS, start=1):
        src = SHOTS / f"{name}_{lang}_dark.png"
        out = OUT / "tv-screenshots" / lang / f"{i}-{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        Image.open(src).convert("RGB").save(out, optimize=True)
        print(out.relative_to(ROOT))


if __name__ == "__main__":
    icon()
    tv_banner()
    for language in ("tr", "en"):
        feature(language)
        screenshots(language)
