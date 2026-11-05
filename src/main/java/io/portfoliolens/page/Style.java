package io.portfoliolens.page;

/** The inline stylesheet of the page: light and dark palettes through the system color scheme. */
final class Style
{
    private Style()
    {
    }

    static final String CSS = """
                    :root{color-scheme:light dark;--bg:#fbfbfa;--fg:#1d2125;--muted:#5d646b;--line:#d9dcdf;--panel:#ffffff;
                    --c-direct:#2CA02C;--c-indirect:#1F77B4;--tm-bg:#1F77B4;--tm-label:#ffffff;--tm-other:#a3a9af;
                    --cov-companies:#7b5ea7;--cov-ungrouped:#a3b84a;--cov-cash:#9aa1a8;--cov-unverified:#e0a43a;
                    --cov-unresolved:#c4473a;--warn-bg:#fff4dc;--warn-line:#d79a1e;--info-bg:#eef4fb;--target:#fff1b8;
                    --mx-0:#eef3f8;--mx-1:#1f4e89}
                    @media (prefers-color-scheme:dark){:root{--bg:#15181b;--fg:#e6e8ea;--muted:#a1a8af;--line:#33393f;
                    --panel:#1d2125;--tm-other:#5f666d;
                    --cov-companies:#a58ad6;--cov-ungrouped:#b9cc5c;--cov-cash:#7c848c;--cov-unverified:#e8b45a;
                    --cov-unresolved:#e0675a;--warn-bg:#3a2e12;--warn-line:#d79a1e;--info-bg:#1c2b3b;--target:#4a4214;
                    --mx-0:#22303e;--mx-1:#8db8f2}}
                    *{box-sizing:border-box}
                    body{margin:0 auto;max-width:1360px;padding:16px;background:var(--bg);color:var(--fg);
                    font:14px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
                    h1{font-size:22px;margin:0}h2{font-size:15px;margin:0 0 6px}
                    .meta{color:var(--muted);margin:4px 0 0}
                    .stale{background:var(--warn-bg);border-left:4px solid var(--warn-line);padding:6px 10px;margin:8px 0 0}
                    header.page{padding-bottom:12px}
                    .banner{padding:10px 14px;margin:0 0 12px;border-radius:6px}
                    .banner p{margin:2px 0}
                    .banner.warning{background:var(--warn-bg);border-left:5px solid var(--warn-line);font-weight:600}
                    .banner.info{background:var(--info-bg)}
                    code{font:13px ui-monospace,Menlo,Consolas,monospace;background:var(--panel);padding:2px 5px;border-radius:4px}
                    .summary dl{display:flex;flex-wrap:wrap;gap:10px 28px;margin:0}
                    .summary dt{color:var(--muted);font-size:12px}.summary dd{margin:0;font-size:18px;font-weight:600}
                    .summary dd[title]{text-decoration:underline dotted;cursor:help}
                    .notices{margin:8px 0 0;color:var(--muted)}.notices summary{cursor:pointer}
                    .notices.attention summary{color:var(--fg)}.notices p{margin:4px 0;max-width:900px}
                    .coverage{border-top:1px solid var(--line);margin-top:12px;padding-top:12px}
                    svg.bar{width:100%;height:24px;display:block;border-radius:4px}
                    .legend{list-style:none;padding:0;margin:6px 0 0;display:flex;flex-wrap:wrap;gap:4px 18px}
                    .swatch{display:inline-block;width:11px;height:11px;border-radius:2px;margin-right:5px;vertical-align:-1px}
                    .cov-companies{fill:var(--cov-companies);background:var(--cov-companies)}
                    .cov-ungrouped-equity{fill:var(--cov-ungrouped);background:var(--cov-ungrouped)}
                    .cov-cash-or-other{fill:var(--cov-cash);background:var(--cov-cash)}
                    .cov-unverified{fill:var(--cov-unverified);background:var(--cov-unverified)}
                    .cov-unresolved{fill:var(--cov-unresolved);background:var(--cov-unresolved)}
                    .swatch.direct{background:var(--c-direct)}.swatch.indirect{background:var(--c-indirect)}
                    .controls{border-top:1px solid var(--line);margin-top:12px;padding:12px 0}
                    select{font:inherit;padding:3px 6px}
                    main{border-top:1px solid var(--line)}
                    .charts{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,600px),1fr));gap:16px;margin:12px 0}
                    figure{margin:0}figcaption{font-weight:600;margin-bottom:4px}
.chart-legend{margin:6px 0 0;font-size:12px;color:var(--fg);display:flex;align-items:center;flex-wrap:wrap;gap:6px}
.chart-note{margin:4px 0 0;font-size:12px;color:var(--muted)}
.legend-caption{color:var(--muted)}.gradient{display:inline-block;width:120px;height:11px;border-radius:2px}
                    svg.treemap,svg.bars{width:100%;height:auto;display:block}svg.matrix{max-width:100%;height:auto;display:block}
                    .tm-bg,.tm-group{fill:var(--tm-bg)}.tm-group{stroke:var(--bg);stroke-width:1.5}
                    .tm-tile{stroke:var(--bg);stroke-width:1;vector-effect:non-scaling-stroke}
.tm-tile.tm-other{fill:var(--tm-other)}
.tm-merged{cursor:zoom-in}.tm-merged:focus{outline:none}.tm-merged:focus rect.tm-other,.tm-merged:hover rect.tm-other{stroke:var(--fg);stroke-width:2}
.tm-mosaic rect{stroke:none}.tm-merged-frame{fill:none;stroke:var(--bg);stroke-width:2.5;vector-effect:non-scaling-stroke}
.tm-merged:hover .tm-merged-frame,.tm-merged:focus .tm-merged-frame{stroke:var(--fg)}.tm-merged-label{font-weight:600}
.crumbs{font-size:12px;margin:0 0 4px}.crumb{font:inherit;background:none;border:0;padding:0;color:var(--c-indirect);text-decoration:underline;cursor:pointer}
.crumb-current{font-weight:600}.crumb-hint{color:var(--muted)}
                    a:hover .tm-tile,a:focus .tm-tile,.tm-tile.selected,.tm-group.selected{stroke:var(--fg);stroke-width:2}
                    .tm-label,.tm-share,.tm-group-label{font-size:11px;fill:var(--tm-label);pointer-events:none;paint-order:stroke;stroke:rgba(0,0,0,.25);stroke-width:2px}
                    .tm-group-label{font-weight:600}.tm-share{opacity:.85}
                    .bar-label,.bar-value,.mx-label{font-size:11px;fill:var(--fg)}.bar-value{fill:var(--muted)}
                    .bar-direct{fill:var(--c-direct)}.bar-indirect{fill:var(--c-indirect)}
                    a:hover .bar-label{text-decoration:underline}
                    .mx-cell{fill:color-mix(in srgb,var(--mx-1) calc(var(--shade)*85%),var(--mx-0))}
                    .mx-text,.mx-detail{font-size:12px;text-anchor:middle;fill:var(--fg);paint-order:stroke;stroke:var(--bg);stroke-width:2px}
                    .mx-detail{font-size:10px}
                    table{border-collapse:collapse;width:100%;margin:8px 0 16px}
                    th,td{padding:4px 8px;border-bottom:1px solid var(--line);vertical-align:top;text-align:left}
                    th{font-size:12px;color:var(--muted);font-weight:600;white-space:nowrap}
                    th[data-sort]{cursor:pointer}th[data-sort]:hover{color:var(--fg)}
                    .num{text-align:right;white-space:nowrap;font-variant-numeric:tabular-nums}.rank{width:2.5em;color:var(--muted)}
                    tr:target,tr.selected{background:var(--target)}
                    tbody.fixed td{color:var(--muted)}
                    details>summary{cursor:pointer}
                    .contrib{margin:4px 0 2px 18px;padding:0;font-size:12px;color:var(--muted)}
                    .contrib li.direct::marker{color:var(--c-direct)}.detail{font-size:12px;color:var(--muted);margin:2px 0}
                    .toggle{font:inherit;background:none;border:0;padding:0;color:inherit;cursor:pointer;text-align:left}
                    .show-more{font:inherit;margin-left:8px}
.toggle::before{content:"\\25B8  ";color:var(--muted)}.toggle[aria-expanded=true]::before{content:"\\25BE  "}
                    tr.child td:nth-child(2){padding-left:26px}
                    .view-notice{color:var(--muted);margin:10px 0 0}.empty{color:var(--muted);padding:24px 0}
                    .overlap{border-top:1px solid var(--line);padding-top:12px}
                    .status{border-top:2px solid var(--line);margin-top:16px;padding-top:8px}
                    .status summary{font-weight:600;padding:4px 0}.status summary.warning{color:var(--warn-line)}
                    .status-line{color:var(--muted)}.status-warning{background:var(--warn-bg);padding:6px 10px;border-radius:4px}
                    table.funds tr[title]:not([title=""]){cursor:help}
                    .health-ok{color:var(--cov-companies)}.health-warning{color:var(--warn-line)}.health-error{color:var(--cov-unresolved)}
                    footer{border-top:1px solid var(--line);margin-top:24px;padding-top:8px;color:var(--muted);font-size:12px}
                    @media (max-width:600px){body{padding:16px}.summary dd{font-size:16px}th,td{padding:4px}
                    table.breakdown td:nth-child(3),table.breakdown td:nth-child(4),table.breakdown th:nth-child(3),
                    table.breakdown th:nth-child(4){display:none}}
                    """;
}
