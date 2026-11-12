# Proposal

## Why

Portfolio Performance (PP) now shows a small pill "Lens" at the bottom right of its window that
leads to Lens. In Lens the way back is a bordered button in the top bar, which looks different and
takes room in a bar that is already full. Both directions should look and behave the same, so that
the two applications feel like one when they run in one web app window.

## What Changes

- The application link is a pill fixed at the bottom right of the viewport, not an item of the top
  bar. It is a plain link with the configured label, in the same style as the "Lens" pill in PP:
  small, rounded, a dark translucent surface with light text, 55 % opaque until hovered or focused.
- The top bar no longer contains the application link. Without a configured link the page has no
  pill and no placeholder, as before.
- The link keeps its behaviour: same tab, `rel="noopener"`, URL and label escaped, no script, no
  request. The settings `LENS_APP_LINK_URL` and `LENS_APP_LINK_LABEL` do not change.
- The pill does not cover content at the end of the page: the page gets room below the footer when
  the pill is present.

The interface of a release (commands, settings, exit statuses, `status.json`, cache) does not
change. The look of `index.html` is not part of it, so this is a MINOR change by `feat`.
