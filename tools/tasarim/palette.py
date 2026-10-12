"""Palette study for the two main-screen drafts.

Re-implements the colour maths of desktop-kit (theme/Palette.kt: mixSrgb,
contrastRatio, ensureContrast, DeskPalette.light/dark) so the drafts show the
colours the kit would really produce, then warms the surfaces the way the app
will do through `colors = { it.copy(...) }` and checks every text/background
pair for WCAG AA (4.5:1).

Usage: palette.py OUT_DIR   -> writes tokens.json, tokens.css, PALETTE.md
"""
import json
import os
import sys

MIN_CONTRAST = 4.5


def rgb(hex_color):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[i:i + 2], 16) / 255 for i in (0, 2, 4))


def to_hex(color):
    return "#" + "".join(f"{round(max(0.0, min(1.0, c)) * 255):02X}" for c in color)


def mix(a, b, fraction):
    """desktopkit mixSrgb(a, b, fraction): a * fraction + b * (1 - fraction)."""
    return tuple(x * fraction + y * (1 - fraction) for x, y in zip(a, b))


def luminance(color):
    def linear(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (linear(c) for c in color)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def ensure(color, against, lighten, minimum=4.6):
    """desktopkit ensureContrast: move toward white/black in 5 % steps."""
    if contrast(color, against) >= minimum:
        return color
    target = (1.0, 1.0, 1.0) if lighten else (0.0, 0.0, 0.0)
    step = 0.05
    while step <= 1.0 + 1e-9:
        candidate = mix(target, color, step)
        if contrast(candidate, against) >= minimum:
            return candidate
        step += 0.05
    return target


WHITE, BLACK = rgb("#FFFFFF"), rgb("#000000")

# ---- desktop-kit 0.1.0 constants (theme/Palette.kt)
KIT_LIGHT_HOVER = rgb("#EDF0F4")
KIT_DARK_HOVER = rgb("#262C34")
KIT_DARK_ON_ACCENT = rgb("#0E1420")


def kit_light_deep(base):
    return ensure(ensure(mix(base, BLACK, 0.55), WHITE, lighten=False), KIT_LIGHT_HOVER, lighten=False)


def kit_dark_tone(base):
    return ensure(ensure(base, KIT_DARK_HOVER, lighten=True), KIT_DARK_ON_ACCENT, lighten=True)


# ---- the app's accents: Accent.custom(name, base)
ACCENTS = {
    "Bordo": "#C2364B",
    "Mürekkep laciverti": "#3F6CC4",
    "Zeytin yeşili": "#93A847",
    "Eskitilmiş altın": "#D9A32B",
}
KIT_PRESETS = {"Blue": "#8AB0FF", "Amber": "#F2B84B", "Green": "#59D49A", "Coral": "#FF7A6E"}

# ---- warmed surfaces and text, per draft and theme
THEMES = {
    "A": {
        "light": dict(background="#F3ECDD", chrome="#EBE2CF", surface="#FBF7EE", surfaceRaised="#FFFCF5",
                      hover="#E6DCC7", pressed="#DBD0B9", border="#DDD2BC", borderStrong="#C9BCA2",
                      text="#2A2018", textSecondary="#4E4034", textTertiary="#63554A",
                      success="#2F6B3F", warning="#7A5510", danger="#A3312A"),
        "dark": dict(background="#1E1915", chrome="#171310", surface="#27211B", surfaceRaised="#302921",
                     hover="#372F27", pressed="#41382E", border="#342C25", borderStrong="#43392F",
                     text="#F0E8DA", textSecondary="#D3C8B6", textTertiary="#AFA28F",
                     success="#7FCB93", warning="#E3B45C", danger="#F08A7C"),
    },
    "B": {
        "light": dict(background="#F4EFE4", chrome="#FAF6EE", surface="#FDFBF6", surfaceRaised="#FFFDF9",
                      hover="#E9E2D3", pressed="#DED6C5", border="#DFD7C7", borderStrong="#CCC2AE",
                      text="#18223B", textSecondary="#3B4660", textTertiary="#525D76",
                      success="#2B6A45", warning="#7A5510", danger="#A3312A"),
        "dark": dict(background="#1C1A18", chrome="#151412", surface="#252220", surfaceRaised="#2E2A27",
                     hover="#35312D", pressed="#3F3A35", border="#322E2A", borderStrong="#423D37",
                     text="#EEE9E0", textSecondary="#D0C9BD", textTertiary="#ABA498",
                     success="#7FCB93", warning="#E3B45C", danger="#F08A7C"),
    },
}


def build(draft, mode, accent_hex):
    """Final tokens: kit result, then the app's warm overrides."""
    theme = {key: rgb(value) for key, value in THEMES[draft][mode].items()}
    base = rgb(accent_hex)
    surfaces = [theme[k] for k in ("background", "chrome", "surface", "surfaceRaised", "hover")]
    if mode == "light":
        accent = kit_light_deep(base)
        for surface in surfaces:                      # readable as text on every warm surface
            accent = ensure(accent, surface, lighten=False)
        on_accent = WHITE
        selection = ensure(mix(base, theme["surface"], 0.20), theme["textTertiary"], lighten=True)
        on_selection = theme["text"]
    else:
        accent = kit_dark_tone(base)
        for surface in surfaces:
            accent = ensure(accent, surface, lighten=True)
        on_accent = ensure(theme["chrome"], accent, lighten=False)
        selection = ensure(mix(base, theme["background"], 0.20), theme["textTertiary"], lighten=False)
        on_selection = ensure(mix(base, WHITE, 0.3), selection, lighten=True)
    theme.update(accent=accent, onAccent=on_accent, accentText=accent, selection=selection,
                 onSelection=on_selection, focusRing=accent)
    return theme


def pairs(theme):
    surfaces = ("background", "chrome", "surface", "surfaceRaised", "hover")
    for text in ("text", "textSecondary", "textTertiary"):
        for surface in surfaces:
            yield f"{text} / {surface}", theme[text], theme[surface]
    for surface in surfaces:
        yield f"accentText / {surface}", theme["accentText"], theme[surface]
    yield "onAccent / accent", theme["onAccent"], theme["accent"]
    yield "onSelection / selection", theme["onSelection"], theme["selection"]
    yield "textSecondary / selection", theme["textSecondary"], theme["selection"]
    yield "textTertiary / selection", theme["textTertiary"], theme["selection"]
    yield "accentText / selection", theme["accentText"], theme["selection"]
    for status in ("success", "warning", "danger"):
        for surface in ("background", "surface", "surfaceRaised"):
            yield f"{status} / {surface}", theme[status], theme[surface]


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    tokens, failures, worst = {}, [], {}
    for draft in THEMES:
        for mode in ("light", "dark"):
            for name, accent_hex in {**ACCENTS, **KIT_PRESETS}.items():
                theme = build(draft, mode, accent_hex)
                key = f"{draft}/{mode}/{name}"
                tokens[key] = {k: to_hex(v) for k, v in theme.items()}
                low = None
                for label, fg, bg in pairs(theme):
                    ratio = contrast(fg, bg)
                    if low is None or ratio < low[1]:
                        low = (label, ratio)
                    if ratio < MIN_CONTRAST:
                        failures.append(f"{key}: {label} = {ratio:.2f}")
                worst[key] = low

    with open(os.path.join(out_dir, "tokens.json"), "w", encoding="utf-8") as handle:
        json.dump(tokens, handle, ensure_ascii=False, indent=1)

    lines = ["# Renkler ve kontrast", "",
             "Kitin renk hesabı (`mixSrgb`, `ensureContrast`) aynen uygulanıp yüzeyler ısıtıldı. "
             "Her tema × vurgu için kitin denetlediği bütün metin/arka plan çiftleri ve ek olarak "
             "seçili satır ve durum renkleri ölçüldü. Alt sınır 4.5:1.", "",
             "| Taslak / tema / vurgu | Vurgu | Seçili satır | En düşük çift | Oran |", "| --- | --- | --- | --- | --- |"]
    for key, (label, ratio) in worst.items():
        lines.append(f"| {key} | `{tokens[key]['accent']}` | `{tokens[key]['selection']}` | {label} | {ratio:.2f} |")
    lines += ["", "## Yüzey ve metin renkleri", ""]
    for draft in THEMES:
        for mode in ("light", "dark"):
            lines.append(f"### Taslak {draft}, {'Kâğıt' if mode == 'light' else 'Gece okuması'}")
            lines.append("")
            lines.append(", ".join(f"{k} `{v}`" for k, v in THEMES[draft][mode].items()))
            lines.append("")
    lines += ["## Vurgu renkleri (`Accent.custom`)", ""]
    lines += [f"- {name}: `{value}`" for name, value in ACCENTS.items()]
    with open(os.path.join(out_dir, "PALETTE.md"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"{len(tokens)} palettes, {len(failures)} failing pairs")
    for failure in failures:
        print("  FAIL", failure)
    for key, (label, ratio) in worst.items():
        if key.split("/")[2] in ACCENTS:
            t = tokens[key]
            print(f"  {key:34s} accent {t['accent']} sel {t['selection']} onSel {t['onSelection']}  lowest {ratio:.2f} ({label})")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
