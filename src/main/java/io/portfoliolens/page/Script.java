package io.portfoliolens.page;

/**
 * The inline script of the page. The embedded data holds every company once; the script builds
 * table rows from it on demand: more rows of the company table, the companies of an expanded
 * group, and the row of a selected chart tile. It also switches the grouping (kept in the address
 * and in the heading), sorts, and shows the staleness notice. The page works without it.
 */
final class Script
{
    private Script()
    {
    }

    static final String JS = """
                    (function(){
                    'use strict';
                    var D=JSON.parse(document.getElementById('lens-data').textContent);
                    var select=document.getElementById('grouping'),heading=document.getElementById('heading');
                    var nf=new Intl.NumberFormat('en-US',{minimumFractionDigits:2,maximumFractionDigits:2});
                    function amount(h){return D.currency+' '+nf.format(h/100);}
                    function share(h){if(D.denominator==null||D.denominator<=0)return '';
                      var p=h/D.denominator*100;if(p>0&&p<0.005)return '< 0.01%';return p.toFixed(2)+'%';}
                    function el(tag,cls,text){var e=document.createElement(tag);if(cls)e.className=cls;
                      if(text!=null)e.textContent=text;return e;}
                    function cells(tr,d,i,t){[d,i,t].forEach(function(v){tr.appendChild(el('td','num',amount(v)));});
                      tr.appendChild(el('td','num',share(t)));}
                    function companyRow(index,factor,detail,id,rank,child){
                      var c=D.c[index],f=factor||1,dir=Math.round(c[2]*f),ind=Math.round(c[3]*f);
                      var tr=el('tr',child?'child':'top');tr.id=id;tr.appendChild(el('td','rank',rank==null?'':String(rank)));
                      var td=el('td'),det=el('details'),sum=el('summary',null,c[1]);det.appendChild(sum);
                      var dt=detail!=null&&detail>=0?detail:c[4];if(dt>=0)det.appendChild(el('p','detail',D.s[dt]));
                      var ul=el('ul','contrib');c[5].forEach(function(x){
                        var v=Math.round(x[1]*f),s=share(v);
                        ul.appendChild(el('li',x[2]?'direct':'indirect',D.s[x[0]]+': '+amount(v)+(s?' ('+s+')':'')));});
                      det.appendChild(ul);td.appendChild(det);tr.appendChild(td);cells(tr,dir,ind,dir+ind);return tr;}
                    // ---- company table
                    var order=D.c.map(function(c,i){return i;}),limit=D.topRows,sortKey=null,sortAsc=false;
                    var companyTable=document.querySelector('table.breakdown[data-grouping=COMPANY]');
                    var more=companyTable&&companyTable.querySelector('tbody.more');
                    function value(i,key){var c=D.c[i];return key==='label'?c[1]:key==='direct'?c[2]:key==='indirect'?c[3]:c[2]+c[3];}
                    function renderCompanies(){
                      if(!companyTable)return;
                      companyTable.querySelectorAll('tbody.company').forEach(function(b){b.remove();});
                      var anchor=companyTable.querySelector('tbody.fixed');
                      order.slice(0,limit).forEach(function(i){var b=el('tbody','block company');b.dataset.index=i;
                        b.appendChild(companyRow(i,1,-1,D.c[i][0],i+1,false));companyTable.insertBefore(b,anchor);});
                      if(more){var btn=more.querySelector('.show-more'),left=D.c.length-limit;
                        btn.hidden=left<=0;btn.textContent='Show '+Math.min(left,500)+' more ('+Math.max(left,0)+' not shown)';}
                    }
                    if(more)more.querySelector('.show-more').addEventListener('click',function(){limit+=500;renderCompanies();});
                    // ---- groups
                    function expand(block,on){
                      var b=block.querySelector('.toggle');if(!b)return;b.setAttribute('aria-expanded',on?'true':'false');
                      if(on&&!block.dataset.built){
                        var g=block.closest('table').dataset.grouping,members=D.g[g][+block.dataset.group];
                        members.forEach(function(m){block.appendChild(companyRow(m[0],m[1],m[2],
                          g.toLowerCase()+'-'+D.c[m[0]][0],null,true));});
                        block.dataset.built='1';}
                      block.querySelectorAll('tr.child').forEach(function(r){r.hidden=!on;});}
                    document.querySelectorAll('tbody.group .toggle').forEach(function(b){
                      b.addEventListener('click',function(){var block=b.closest('tbody');
                        expand(block,b.getAttribute('aria-expanded')!=='true');});});
                    // ---- grouping
                    function show(name){
                      var view=document.getElementById('view-'+name);if(!view){name='COMPANY';view=document.getElementById('view-COMPANY');}
                      document.querySelectorAll('section.view').forEach(function(v){v.hidden=v!==view;});
                      select.value=name;var title=D.headings[name]||heading.textContent;
                      heading.textContent=title;document.title=title;}
                    function fromHash(){var m=/^#grouping=([A-Z_]+)$/.exec(location.hash);return m?m[1]:null;}
                    select.disabled=false;show(fromHash()||'COMPANY');
                    select.addEventListener('change',function(){history.replaceState(null,'','#grouping='+select.value);show(select.value);});
                    window.addEventListener('hashchange',function(){var g=fromHash();if(g)show(g);});
                    // ---- selection
                    function clearSelection(){document.querySelectorAll('.selected').forEach(function(e){e.classList.remove('selected');});}
                    function findRow(id){
                      var row=document.getElementById(id);if(row)return row;
                      var m=/^(?:([a-z_]+)-)?(c[0-9a-f]+)$/.exec(id);if(!m)return null;
                      var index=D.c.findIndex(function(c){return c[0]===m[2];});if(index<0)return null;
                      if(!m[1]){var pos=order.indexOf(index);if(pos>=limit){limit=pos+1;renderCompanies();}
                        return document.getElementById(id);}
                      var g=m[1].toUpperCase(),groups=D.g[g]||[];
                      for(var gi=0;gi<groups.length;gi++)if(groups[gi].some(function(x){return x[0]===index;})){
                        var block=document.querySelector('table.breakdown[data-grouping='+g+'] tbody.group[data-group="'+gi+'"]');
                        if(block)expand(block,true);break;}
                      return document.getElementById(id);}
                    function selectRow(id){clearSelection();var row=findRow(id);if(!row)return;
                      var block=row.closest('tbody');if(row.classList.contains('child'))expand(block,true);
                      else if(block.classList.contains('group'))expand(block,true);
                      var d=row.querySelector('details');if(d)d.open=true;
                      row.classList.add('selected');row.scrollIntoView({block:'center',behavior:'smooth'});
                      document.querySelectorAll('[data-row="'+id+'"] rect').forEach(function(t){t.classList.add('selected');});}
                    document.querySelectorAll('a[data-row]').forEach(function(a){
                      a.addEventListener('click',function(ev){ev.preventDefault();selectRow(a.dataset.row);});});
                    document.querySelectorAll('table.breakdown').forEach(function(t){t.addEventListener('click',function(ev){
                      var r=ev.target.closest('tr.top,tr.child');if(!r||!r.id||ev.target.closest('summary,button'))return;
                      clearSelection();r.classList.add('selected');
                      document.querySelectorAll('[data-row="'+r.id+'"] rect').forEach(function(x){x.classList.add('selected');});});});
                    // ---- sorting
                    document.querySelectorAll('table.breakdown th[data-sort]').forEach(function(th){
                      th.addEventListener('click',function(){
                        var table=th.closest('table'),key=th.dataset.sort,asc=th.dataset.dir!=='asc';
                        table.querySelectorAll('th').forEach(function(h){delete h.dataset.dir;});th.dataset.dir=asc?'asc':'desc';
                        if(table===companyTable){order.sort(function(a,b){var x=value(a,key),y=value(b,key);
                            var r=key==='label'?x.localeCompare(y):x-y;return asc?r:-r;});renderCompanies();return;}
                        var blocks=Array.prototype.slice.call(table.querySelectorAll('tbody.block:not(.fixed)'));
                        var fixed=Array.prototype.slice.call(table.querySelectorAll('tbody.fixed'));
                        blocks.sort(function(a,b){var x=a.dataset[key],y=b.dataset[key];
                          var r=key==='label'?x.localeCompare(y):(parseFloat(x)-parseFloat(y));return asc?r:-r;});
                        blocks.concat(fixed).forEach(function(b){table.appendChild(b);});});});
                    if(more)renderCompanies();
                    // ---- staleness
                    var hours=Math.floor((Date.now()-new Date(D.generatedAt).getTime())/3600000);
                    if(hours>=1){var s=document.getElementById('stale');
                      var age=hours<48?hours+(hours===1?' hour':' hours'):Math.floor(hours/24)+' days';
                      s.textContent='This page is '+age+' old. The Portfolio Lens job may not be running.';s.hidden=false;}
                    })();
                    """;
}
