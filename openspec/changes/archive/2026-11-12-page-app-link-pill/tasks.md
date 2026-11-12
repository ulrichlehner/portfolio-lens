# Tasks

## 1. Page

- [x] 1.1 Move the application link out of the header in `PageWriter` to the end of the body and drop the paragraph wrapper
- [x] 1.2 Style the pill in `Style.java` (tokens `--pill-bg`, `--pill-fg` on `:root`, fixed bottom right, hover and focus, print, padding for the content) and remove the former top bar rules
- [x] 1.3 Update `PageSmokeTest`: the link is outside the header, still one link, same attributes, no script, no extra request; no link without the setting

## 2. Documentation and specs

- [x] 2.1 Update the README sentence on the bar
- [x] 2.2 Update the requirements "Application link in the header" and "Top bar" in `breakdown-view`

## 3. Verification

- [x] 3.1 Run `scripts/dev.sh test` and verify that all tests pass
- [x] 3.2 Render a page with an application link and check in the browser at a wide and a narrow viewport, light and dark, that the pill is at the bottom right, does not cover the last table row after scrolling, and is reachable and visible by keyboard
