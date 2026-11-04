package io.portfoliolens.page;

/**
 * The inline script of the page: grouping switch kept in the address, table sorting, linked
 * selection between charts and table, expanding groups, and the staleness notice. The page
 * works without it.
 */
final class Script
{
    private Script()
    {
    }

    static final String JS = """
                    (function(){
                    'use strict';
                    var data=JSON.parse(document.getElementById('lens-data').textContent);
                    var select=document.getElementById('grouping');
                    function show(name){
                      var found=false;
                      document.querySelectorAll('section.view').forEach(function(v){
                        var on=v.dataset.grouping===name;v.hidden=!on;if(on)found=true;});
                      if(!found){show('COMPANY');return;}
                      select.value=name;
                    }
                    function fromHash(){var h=decodeURIComponent(location.hash.replace(/^#/,''));
                      var m=/^grouping=([A-Z_]+)$/.exec(h);return m?m[1]:null;}
                    select.disabled=false;
                    show(fromHash()||'COMPANY');
                    select.addEventListener('change',function(){history.replaceState(null,'','#grouping='+select.value);show(select.value);});
                    window.addEventListener('hashchange',function(){var g=fromHash();if(g)show(g);});
                    function setExpanded(block,on){var b=block.querySelector('.toggle');if(!b)return;
                      b.setAttribute('aria-expanded',on?'true':'false');
                      block.querySelectorAll('tr.child').forEach(function(r){r.hidden=!on;});}
                    document.querySelectorAll('tbody.group .toggle').forEach(function(b){
                      b.addEventListener('click',function(){var block=b.closest('tbody');
                        setExpanded(block,b.getAttribute('aria-expanded')!=='true');});});
                    function selectRow(id){
                      document.querySelectorAll('.selected').forEach(function(e){e.classList.remove('selected');});
                      var row=document.getElementById(id);if(!row)return;
                      var block=row.closest('tbody');
                      if(row.classList.contains('child'))setExpanded(block,true);
                      else if(block.classList.contains('group'))setExpanded(block,true);
                      var d=row.querySelector('details');if(d)d.open=true;
                      row.classList.add('selected');row.scrollIntoView({block:'center',behavior:'smooth'});
                      document.querySelectorAll('[data-row="'+id+'"] rect.tm-tile').forEach(function(t){t.classList.add('selected');});
                    }
                    document.querySelectorAll('a[data-row]').forEach(function(a){
                      a.addEventListener('click',function(ev){ev.preventDefault();selectRow(a.dataset.row);});});
                    document.querySelectorAll('table.breakdown tbody.block > tr.top').forEach(function(r){
                      r.addEventListener('click',function(ev){if(ev.target.closest('summary,button'))return;
                        document.querySelectorAll('.selected').forEach(function(e){e.classList.remove('selected');});
                        r.classList.add('selected');
                        document.querySelectorAll('[data-row="'+r.id+'"] rect.tm-tile').forEach(function(t){t.classList.add('selected');});});});
                    document.querySelectorAll('table.breakdown th[data-sort]').forEach(function(th){
                      th.addEventListener('click',function(){
                        var table=th.closest('table'),key=th.dataset.sort;
                        var asc=th.dataset.dir!=='asc';
                        table.querySelectorAll('th').forEach(function(h){delete h.dataset.dir;});th.dataset.dir=asc?'asc':'desc';
                        var blocks=Array.prototype.slice.call(table.querySelectorAll('tbody.block:not(.fixed)'));
                        var fixed=Array.prototype.slice.call(table.querySelectorAll('tbody.block.fixed'));
                        blocks.sort(function(a,b){var x=a.dataset[key],y=b.dataset[key];
                          var r=key==='label'?x.localeCompare(y):(parseFloat(x)-parseFloat(y));return asc?r:-r;});
                        blocks.concat(fixed).forEach(function(b){table.appendChild(b);});});});
                    var generated=new Date(data.generatedAt);
                    var hours=Math.floor((Date.now()-generated.getTime())/3600000);
                    if(hours>=1){var s=document.getElementById('stale');
                      s.textContent='This page is '+hours+(hours===1?' hour':' hours')+' old. The Portfolio Lens job may not be running.';
                      s.hidden=false;}
                    })();
                    """;
}
