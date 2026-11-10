package io.portfoliolens.page;

/**
 * The inline stylesheet of the page: light and dark palettes through the system color scheme, a
 * bar that stays at the top, and two panes on wide screens. Rules under {@code .js} apply only
 * when scripts run; without them the charts are shown below each other and nothing is sticky.
 */
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
                    --mx-0:#eef3f8;--mx-1:#1f4e89;--bar-h:68px}
                    @media (prefers-color-scheme:dark){:root{--bg:#15181b;--fg:#e6e8ea;--muted:#a1a8af;--line:#33393f;
                    --panel:#1d2125;--tm-other:#5f666d;
                    --cov-companies:#a58ad6;--cov-ungrouped:#b9cc5c;--cov-cash:#7c848c;--cov-unverified:#e8b45a;
                    --cov-unresolved:#e0675a;--warn-bg:#3a2e12;--warn-line:#d79a1e;--info-bg:#1c2b3b;--target:#4a4214;
                    --mx-0:#22303e;--mx-1:#8db8f2}}
                    *{box-sizing:border-box}
                    html{scrollbar-gutter:stable}
                    body{margin:0 auto;max-width:1680px;padding:0 16px 16px;background:var(--bg);color:var(--fg);
                    font:14px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
                    h1{font-size:17px;margin:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
                    h2{font-size:15px;margin:0 0 6px}
                    [hidden]{display:none!important}
                    html:not(.js) .needs-js{display:none}
                    .topbar{position:sticky;top:0;z-index:20;height:var(--bar-h);margin:0 -16px 12px;padding:8px 16px 0;
                    background:var(--bg);border-bottom:1px solid var(--line)}
                    .bar-main{display:flex;align-items:center;gap:10px;height:32px}
                    .bar-main h1{flex:0 1 auto;min-width:0;margin-right:8px}
                    .bar-main label{color:var(--muted);white-space:nowrap}
                    select,input[type=search]{font:inherit;padding:3px 6px;height:28px;color:var(--fg);background:var(--panel);
                    border:1px solid var(--line);border-radius:4px}
                    #search{flex:0 1 220px;min-width:90px}
                    .app-link{margin:0 0 0 auto;white-space:nowrap}
                    .app-link a{display:inline-block;padding:3px 10px;border:1px solid var(--line);border-radius:4px;
                    background:var(--panel);color:var(--fg);text-decoration:none;font-weight:600}
                    .app-link a::before{content:"\\2039  "}
                    .app-link a:hover,.app-link a:focus{border-color:var(--fg)}
                    .meta{color:var(--muted);margin:2px 0 0;height:22px;line-height:22px;white-space:nowrap;overflow:hidden;
                    text-overflow:ellipsis;font-size:12px}
                    .stale{background:var(--warn-bg);border-left:4px solid var(--warn-line);color:var(--fg);padding:1px 8px;
                    margin-right:8px}
                    .banner{padding:10px 14px;margin:0 0 12px;border-radius:6px}
                    .banner p{margin:2px 0}
                    .banner.warning{background:var(--warn-bg);border-left:5px solid var(--warn-line);font-weight:600}
                    .banner.info{background:var(--info-bg)}
                    code{font:13px ui-monospace,Menlo,Consolas,monospace;background:var(--panel);padding:2px 5px;border-radius:4px}
                    .summary dl{display:flex;flex-wrap:wrap;gap:8px 28px;margin:0}
                    .summary dl>div{position:relative}
                    .summary dt{color:var(--muted);font-size:12px}.summary dd{margin:0;font-size:17px;font-weight:600}
                    .summary dd span[title]{text-decoration:underline dotted;cursor:help}
                    .hint{display:inline-block;position:relative;font-size:13px;font-weight:400;vertical-align:1px}
                    .hint>summary{list-style:none;display:inline-block;color:var(--muted);padding:0 3px;border-radius:3px}
                    .hint>summary::-webkit-details-marker{display:none}
                    .hint>summary:hover,.hint[open]>summary{color:var(--fg);background:var(--line)}
                    .hint>p{position:absolute;z-index:15;left:0;top:100%;margin:4px 0 0;width:min(420px,80vw);
                    padding:8px 10px;background:var(--panel);border:1px solid var(--line);border-radius:6px;
                    box-shadow:0 4px 14px rgba(0,0,0,.18);white-space:pre-line;color:var(--fg);font-size:13px;line-height:1.4}
                    .notices{margin:6px 0 0;color:var(--muted)}.notices summary{cursor:pointer}
                    .notices.attention summary{color:var(--fg)}.notices p{margin:4px 0;max-width:900px}
                    .coverage{margin-top:10px}.coverage h2{font-size:12px;color:var(--muted);font-weight:600;margin:0 0 3px}
                    svg.bar{width:100%;height:14px;display:block;border-radius:4px}
                    .legend{list-style:none;padding:0;margin:5px 0 0;display:flex;flex-wrap:wrap;gap:4px 18px}
                    .legend-amount{color:var(--muted)}
                    .swatch{display:inline-block;width:11px;height:11px;border-radius:2px;margin-right:5px;vertical-align:-1px}
                    .cov-companies{fill:var(--cov-companies);background:var(--cov-companies)}
                    .cov-ungrouped-equity{fill:var(--cov-ungrouped);background:var(--cov-ungrouped)}
                    .cov-cash-or-other{fill:var(--cov-cash);background:var(--cov-cash)}
                    .cov-unverified{fill:var(--cov-unverified);background:var(--cov-unverified)}
                    .cov-unresolved{fill:var(--cov-unresolved);background:var(--cov-unresolved)}
                    .swatch.direct{background:var(--c-direct)}.swatch.indirect{background:var(--c-indirect)}
                    main.panes{border-top:1px solid var(--line);margin-top:12px;padding-top:12px;display:grid;
                    grid-template-columns:minmax(0,1fr);gap:16px 24px;align-items:start}
                    .chart-pane,.table-pane{min-width:0}
                    .table-pane{container-type:inline-size}
                    @media (min-width:1100px){main.panes{grid-template-columns:minmax(0,5fr) minmax(0,6fr)}
                    .js .chart-pane{position:sticky;top:calc(var(--bar-h) + 12px);
                    max-height:calc(100vh - var(--bar-h) - 20px);overflow:auto}}
                    .tabs{display:flex;gap:4px;border-bottom:1px solid var(--line);margin-bottom:8px}
                    .tabs button{font:inherit;color:var(--muted);background:none;border:0;border-bottom:2px solid transparent;
                    padding:5px 12px;margin-bottom:-1px;cursor:pointer}
                    .tabs button:hover{color:var(--fg)}
                    .tabs button[aria-selected=true]{color:var(--fg);font-weight:600;border-bottom-color:var(--c-indirect)}
                    figure.chart{margin:0 0 16px}
                    .js figure.chart{margin:0}
                    .js .chart-pane[data-tab=treemap] .chart:not([data-chart=treemap]),
                    .js .chart-pane[data-tab=bars] .chart:not([data-chart=bars]),
                    .js .chart-pane[data-tab=overlap] .chart:not([data-chart=overlap]){display:none}
                    .chart-head{display:flex;align-items:center;gap:8px;height:24px;font-size:12px;overflow:hidden;white-space:nowrap}
                    .chart-caption{font-weight:600;font-size:14px}
                    .js .chart-caption{display:none}
                    .js .chart-head.overview .chart-caption,.js .chart[data-chart=bars] .chart-caption,
                    .js .chart[data-chart=overlap] .chart-caption{display:inline}
                    .back{font:inherit;padding:0 8px;height:20px;border:1px solid var(--line);border-radius:4px;
                    background:var(--panel);color:var(--fg);cursor:pointer}
                    .crumbs{overflow:hidden;text-overflow:ellipsis}
                    .crumb{font:inherit;background:none;border:0;padding:0;color:var(--c-indirect);text-decoration:underline;cursor:pointer}
                    .crumb-current{font-weight:600}
                    .chart-box{aspect-ratio:640/380;width:100%;overflow:auto}
                    .chart-box svg{width:100%;height:100%;display:block}
                    .chart-box .empty{padding:24px 0}
                    .chart-legend{margin:6px 0 0;height:20px;overflow:hidden;font-size:12px;color:var(--fg);display:flex;
                    align-items:center;gap:6px;white-space:nowrap}
                    .chart-note{margin:2px 0 0;height:18px;overflow:hidden;font-size:12px;color:var(--muted);white-space:nowrap;
                    text-overflow:ellipsis}
                    .coverage .chart-note{height:auto;white-space:normal}
                    .legend-caption{color:var(--muted)}.gradient{display:inline-block;width:120px;height:11px;border-radius:2px}
                    .tm-bg,.tm-group{fill:var(--tm-bg)}.tm-group{stroke:var(--bg);stroke-width:1.5}
                    .tm-tile{stroke:var(--bg);stroke-width:1;vector-effect:non-scaling-stroke}
                    .tm-tile.tm-other{fill:var(--tm-other)}
                    .tm-merged[role=button]{cursor:zoom-in}.tm-merged:focus{outline:none}
                    .tm-merged[role=button]:focus rect.tm-other,.tm-merged[role=button]:hover rect.tm-other{stroke:var(--fg);stroke-width:2}
                    .tm-merged-label{font-weight:600}
                    .tm-overlay{fill:transparent;stroke:none;cursor:zoom-in;vector-effect:non-scaling-stroke}
                    a.tm-zoom:focus{outline:none}
                    a.tm-zoom:hover .tm-overlay,a.tm-zoom:focus .tm-overlay,.tm-overlay.selected{stroke:var(--fg);stroke-width:2.5}
                    a:hover .tm-tile,a:focus .tm-tile,.tm-tile.selected{stroke:var(--fg);stroke-width:2}
                    .tm-label,.tm-share,.tm-group-label{font-size:11px;fill:var(--tm-label);pointer-events:none;paint-order:stroke;stroke:rgba(0,0,0,.25);stroke-width:2px}
                    .tm-group-label{font-weight:600}.tm-share{opacity:.85}
                    .bar-label,.bar-value,.mx-label{font-size:11px;fill:var(--fg)}.bar-value{fill:var(--muted)}
                    .bar-direct{fill:var(--c-direct)}.bar-indirect{fill:var(--c-indirect)}
                    a:hover .bar-label{text-decoration:underline}
                    .bars a .selected{stroke:var(--fg);stroke-width:1.5}
                    .mx-cell{fill:color-mix(in srgb,var(--mx-1) calc(var(--shade)*85%),var(--mx-0))}
                    .mx-text,.mx-detail{font-size:12px;text-anchor:middle;fill:var(--fg);paint-order:stroke;stroke:var(--bg);stroke-width:2px}
                    .mx-detail{font-size:10px}
                    table{border-collapse:collapse;width:100%;margin:0 0 16px;table-layout:fixed}
                    th,td{padding:4px 8px;border-bottom:1px solid var(--line);vertical-align:top;text-align:left;
                    overflow-wrap:anywhere}
                    th{font-size:12px;color:var(--muted);font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
                    table.breakdown thead th{position:sticky;top:var(--bar-h);background:var(--bg);z-index:5}
                    th[data-sort]{cursor:pointer}th[data-sort]:hover{color:var(--fg)}
                    th[data-dir=asc]::after{content:" \\25B4"}th[data-dir=desc]::after{content:" \\25BE"}
                    .num{text-align:right;white-space:nowrap;font-variant-numeric:tabular-nums}
                    th.rank{width:3.2em}th.num{width:calc(12ch + 16px)}th.num.share{width:calc(7ch + 16px)}
                    td.rank{color:var(--muted)}
                    table.breakdown tr.top,table.breakdown tr.child{scroll-margin-top:calc(var(--bar-h) + 34px);scroll-margin-bottom:110px}
                    tr:target,tr.selected{background:var(--target)}
                    tbody.fixed td{color:var(--muted)}
                    details>summary{cursor:pointer}
                    tr.detail{display:none}tr.detail td{border-bottom:1px solid var(--line);padding-top:0}
                    tr:has(details[open])+tr.detail,tr.open+tr.detail{display:table-row}
                    tr:has(details[open])>td,tr.open>td{border-bottom-color:transparent}
                    @supports not selector(:has(*)){html:not(.js) tr.detail{display:table-row}}
                    .contrib{margin:0 0 4px 18px;padding:0;font-size:12px;color:var(--muted)}
                    .contrib li.direct::marker{color:var(--c-direct)}.detail p.detail,p.detail{font-size:12px;color:var(--muted);margin:0 0 2px}
                    .toggle{font:inherit;background:none;border:0;padding:0;color:inherit;cursor:pointer;text-align:left}
                    .show-more{font:inherit;margin-left:8px}
                    .toggle::before{content:"\\25B8  ";color:var(--muted)}.toggle[aria-expanded=true]::before{content:"\\25BE  "}
                    tr.child td:nth-child(2){padding-left:26px}
                    table.searching tbody:not(.result){display:none}
                    tbody.result.info td{color:var(--muted)}
                    .view-notice{color:var(--muted);margin:0 0 8px}.empty{color:var(--muted)}
                    .status{border-top:2px solid var(--line);margin-top:16px;padding-top:8px}
                    .status summary{font-weight:600;padding:4px 0}.status #data-status>summary.warning{color:var(--warn-line)}
                    .status table summary{font-weight:400;padding:0}
                    .status-line,.status-explanation{color:var(--muted);margin:6px 0}
                    .status-explanation{white-space:pre-line}
                    .status-warning{background:var(--warn-bg);padding:6px 10px;border-radius:4px;margin:6px 0}
                    table.funds{margin-top:8px}table.funds th.retrieved{width:9em}table.funds th.num{width:8em}
                    .health-ok{color:var(--cov-companies)}.health-warning{color:var(--warn-line)}.health-error{color:var(--cov-unresolved)}
                    footer{border-top:1px solid var(--line);margin-top:24px;padding-top:8px;color:var(--muted);font-size:12px}
                    @container (max-width:640px){table.breakdown td:nth-child(3),table.breakdown td:nth-child(4),
                    table.breakdown th:nth-child(3),table.breakdown th:nth-child(4){display:none}}
                    @media (max-width:700px){:root{--bar-h:98px}.topbar{padding-top:6px}
                    .bar-main{flex-wrap:wrap;height:62px;gap:4px 8px;align-content:flex-start}
                    .bar-main h1{flex:1 1 60%;height:26px;line-height:26px;margin:0}.bar-main label{display:none}
                    .app-link{order:1}#grouping{order:2;flex:0 1 auto;min-width:0}#search{order:3;flex:1 1 90px}
                    .summary dd{font-size:15px}th,td{padding:4px}}
                    """;
}
