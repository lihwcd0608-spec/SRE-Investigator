# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** SRE Investigator
**Generated:** 2026-08-27
**Category:** Developer / Observability console
**Design Dials:** Variance 6/10 (Balanced / Modern) | Motion 4/10 (Standard) | Density 8/10 (Dense / Dashboard)

**Overrides vs generated defaults:** The first `--design-system` pass returned a light Swiss enterprise palette, which does not fit this product. Verified domain searches selected **OLED Dark** (style) + **Coding Bootcamp / terminal dark** (color) + **Developer Mono** (IBM Plex Sans + JetBrains Mono). This file is the source of truth.

---

## Global Rules

### Color Palette (dark only)

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Background | `#05070d` | `--bg` |
| Elevated | `#0b0f18` | `--bg-elevated` |
| Card / Surface | `#10151f` | `--surface` |
| Surface Hover | `#161c28` | `--surface-hover` |
| Foreground | `#f1f5f9` | `--text` |
| Muted Foreground | `#94a3b8` | `--muted` |
| Faint | `#64748b` | `--faint` |
| Border | `rgba(148, 163, 184, 0.16)` | `--border` |
| Accent / CTA | `#22d3ee` | `--accent` |
| On Accent | `#042f36` | `--on-accent` |
| Success | `#34d399` | `--ok` |
| Warning | `#fbbf24` | `--warn` |
| Destructive | `#f87171` | `--danger` |
| Ring | `#22d3ee` | `--ring` |

**Color Notes:** Terminal dark + cyan signal (observability). Never use a white page background. Status must not rely on color alone.

### Typography

- **Heading / Body:** IBM Plex Sans
- **Mono (paths, JSON, env, IDs):** JetBrains Mono
- **Mood:** technical, precise, premium developer utility
- **Google Fonts:** [IBM Plex Sans + JetBrains Mono](https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap)

### Spacing Variables

*Density: 8/10 — Dense / Dashboard*

| Token | Value | Usage |
|-------|-------|-------|
| `--space-xs` | `2px` | Tight gaps |
| `--space-sm` | `4px` | Icon gaps |
| `--space-md` | `8px` | Standard padding |
| `--space-lg` | `12px` | Section padding |
| `--space-xl` | `16px` | Large gaps |
| `--space-2xl` | `24px` | Section margins |
| `--space-3xl` | `32px` | Page padding |

### Motion

- Hover / color: 160–200ms
- Panel open: 180ms
- Respect `prefers-reduced-motion`
- No layout-shifting hover transforms on dense lists (1px lift max on cards)

---

## Product Rules

- Audience: SRE / backend engineers troubleshooting large microservice systems
- Tone: calm, precise, read-only control plane — not a marketing site, not a wizard
- Navigation: icon + label, not numbered setup steps
- Chat is the primary workspace and must fill remaining viewport height
- Empty states include a next action
- Forms use visible labels (not placeholder-only)
- Icons: inline SVG, Phosphor-like 1.75 stroke, no emoji as structure

### Anti-patterns

- Numbered sidebar that looks like an onboarding wizard
- Huge empty cards with sparse content
- Native unstyled selects next to a green status pill
- Color-only status (green/red dots without text)
- Chat transcript as a small box in a sea of dead space
- Mixing filled and outline icons in the same nav layer
