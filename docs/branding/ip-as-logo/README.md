# PLOT mascot candidates (IP as Logo)

Six mascot logo candidates generated with the
[ip-as-logo skill](https://github.com/s1dashu/ip-as-logo-skill) methodology:
one dominant rounded silhouette built from 4–7 large basic shapes, exactly
two character colors plus one solid background color, no sharp corners, and
legibility at 32 × 32. All colors come from the PLOT app palette.

Each file exists as the source SVG and a 1024 × 1024 PNG render.

## Directions

| Direction | Subject | Product attribute |
|---|---|---|
| A | ゾウ (elephant) | "An elephant never forgets" — PLOT remembers your day as searchable history |
| B | フクロウ (owl) | Quietly watches and wisely answers — history capture + AI search |
| C | ハムスター (hamster) | Stores treasures in its cheeks — the encrypted local vault, nothing leaves the device |

## Candidates

| File | Direction | Color 1 (body) | Color 2 (accent) | Background | Shapes |
|---|---|---|---|---|---|
| `plot-mascot-zou-blue` | A | `#2447FF` blue | `#FF7A59` orange (ears, trunk) | `#FFFCF4` | 6 |
| `plot-mascot-zou-orange` | A | `#FF7A59` orange | `#2447FF` blue (ears, trunk) | `#FFFCF4` | 6 |
| `plot-mascot-fukurou-green` | B | `#1F8A70` green | `#F2EBCB` cream (belly, beak) | `#FFFCF4` | 7 |
| `plot-mascot-fukurou-blue` | B | `#2447FF` blue | `#C8FF38` lime (belly, beak) | `#FFFCF4` | 7 |
| `plot-mascot-hamu-orange` | C | `#FF7A59` orange | `#F2EBCB` cream (muzzle) | `#FFFCF4` | 7 |
| `plot-mascot-hamu-lime` | C | `#C8FF38` lime | `#1F8A70` green (muzzle) | `#FFFCF4` | 7 |

## Generation spec (shared prompt)

> Extremely simple, cute, personified square character. One dominant
> silhouette of 4–7 large basic shapes with thick, rounded, weighty contours
> and broad color masses. Two simple dot eyes, optional tiny mouth. Exactly
> two character colors on one solid uniform background. No outlines, no
> texture, no sharp corners, no thin features. Recognizable at 32 × 32.

The SVGs are the canonical assets; regenerate PNGs at any size from them.
