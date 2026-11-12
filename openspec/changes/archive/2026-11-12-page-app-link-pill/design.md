# Design

## Decisions

### D1 Placement

`PageWriter` writes the link after the footer, as the last element of the body, instead of in the
header: `<a class="app-link" href="…" rel="noopener">label</a>`. The paragraph wrapper goes. The top
bar loses its `.app-link` item and the narrow-screen ordering rule for it.

### D2 Style

`position: fixed; right: 12px; bottom: 12px`, `z-index` above the content and below nothing else
the page overlays. Two tokens on `:root`, equal in light and dark, because the pill is a fixed
dark surface in both themes like the pill in PP: `--pill-bg: rgba(49,49,49,.7)` and
`--pill-fg: #fff`. Height 26 pixels, radius `--r-pill`, 13 pixels semibold text, 55 % opacity,
100 % on hover and keyboard focus, the standard focus ring. A leading chevron (`‹`) shows the
direction; the label is truncated with an ellipsis after 40 characters of width so a 60-character
label cannot grow over the page.

### D3 Room for the content

`body:has(.app-link)` gets 56 pixels of bottom padding, so the last line of the page can be scrolled
clear of the pill. The pill is hidden in print.

## Risks

- A long table row at the end of the page can sit under the pill until the user scrolls to the
  padding; the padding covers this.
- `:has()` needs a current browser. Without it the padding is missing and the footer can sit under
  the pill; the pill stays usable.
