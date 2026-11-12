package io.portfoliolens.page;

/**
 * The inline script of the page. The embedded data holds every company once; the script builds
 * table rows from it on demand: more rows of the company table, the companies of an expanded
 * group, search results, and the row of a selected chart tile. It also switches the grouping and
 * the chart tab, draws the levels of the treemap (all three kept in the address), sorts, and shows
 * the staleness notice. The page works without it.
 */
final class Script
{
    private Script()
    {
    }


    /**
     * The squarified layout, the merge of small tiles, the merged-tile text and the tile colour,
     * as JavaScript. They follow {@link TreemapLayout}, {@code PageWriter.merge},
     * {@code PageWriter.mergedLabel} and {@link ChartColors} step by step, so a zoomed level in the
     * page looks exactly as the server would draw it. A test runs both on the same input.
     */
    static final String LAYOUT = """
                    var Lens=(function(){
                    'use strict';
                    function ratio(sum,max,min,side){if(sum<=0||min<=0||side<=0)return Number.MAX_VALUE;
                      var ss=side*side,su=sum*sum;return Math.max(ss*max/su,su/(ss*min));}
                    function worst(row,rowArea,scale,side){var max=0,min=Number.MAX_VALUE;
                      for(var i=0;i<row.length;i++){var v=row[i].w*scale;max=Math.max(max,v);min=Math.min(min,v);}
                      return ratio(rowArea,max,min,side);}
                    function worstWith(row,rowArea,added,scale,side){var max=added,min=added;
                      for(var i=0;i<row.length;i++){var v=row[i].w*scale;max=Math.max(max,v);min=Math.min(min,v);}
                      return ratio(rowArea+added,max,min,side);}
                    function place(row,rowArea,scale,a,out,last){
                      if(a.w>=a.h){var width=last?a.w:Math.min(a.w,rowArea/a.h),y=a.y;
                        for(var i=0;i<row.length;i++){var h=i===row.length-1?a.y+a.h-y:row[i].w*scale/width;
                          out.push({item:row[i],x:a.x,y:y,w:width,h:h});y+=h;}
                        return {x:a.x+width,y:a.y,w:a.w-width,h:a.h};}
                      var height=last?a.h:Math.min(a.h,rowArea/a.w),x=a.x;
                      for(var j=0;j<row.length;j++){var w=j===row.length-1?a.x+a.w-x:row[j].w*scale/height;
                        out.push({item:row[j],x:x,y:a.y,w:w,h:height});x+=w;}
                      return {x:a.x,y:a.y+height,w:a.w,h:a.h-height};}
                    // items: {w: weight, last: placed after the others}; returns tiles in placement order
                    function squarify(items,area){
                      var sorted=items.map(function(it,i){return {it:it,i:i};}).filter(function(e){return e.it.w>0;});
                      sorted.sort(function(a,b){return ((a.it.last?1:0)-(b.it.last?1:0))||(b.it.w-a.it.w)||(a.i-b.i);});
                      sorted=sorted.map(function(e){return e.it;});
                      var out=[];if(!sorted.length||area.w*area.h<=0)return out;
                      var total=0;for(var k=0;k<sorted.length;k++)total+=sorted[k].w;
                      var scale=area.w*area.h/total,remaining=area,row=[],rowArea=0,index=0;
                      while(index<sorted.length){var item=sorted[index],itemArea=item.w*scale,side=Math.min(remaining.w,remaining.h);
                        if(!row.length||worst(row,rowArea,scale,side)>=worstWith(row,rowArea,itemArea,scale,side)){
                          row.push(item);rowArea+=itemArea;index++;}
                        else{remaining=place(row,rowArea,scale,remaining,out,false);row=[];rowArea=0;}}
                      if(row.length)place(row,rowArea,scale,remaining,out,true);
                      return out;}
                    // members: {w, d (direct fraction)}; tiles below the minimum area merge into one
                    // explore: an explored level always makes progress. Members sorted by weight, largest first, are
                    // shown beyond the minimum area until the merged rest covers at most half of the level, or the
                    // level shows MAX_TILES members. The overview is merged without it and matches the Java layout.
                    var MAX_TILES=3000;
                    function merge(members,scale,minArea,explore){var shown=[],others=[],weight=0,direct=0,total=0;
                      for(var i=0;i<members.length;i++){var m=members[i];if(m.w>0)total+=m.w;
                        if(m.w*scale>=minArea)shown.push(m);else if(m.w>0){others.push(m);weight+=m.w;direct+=m.w*m.d;}}
                      var threshold=scale>0?minArea/scale:0;
                      if(explore){var n=0;
                        while(n<others.length&&weight*2>total&&shown.length<MAX_TILES){var o=others[n++];
                          shown.push(o);weight-=o.w;direct-=o.w*o.d;threshold=o.w;}
                        if(n>0)others=others.slice(n);
                        if(!others.length){weight=0;direct=0;}}
                      return {shown:shown,others:others,weight:weight,direct:direct,threshold:threshold};}
                    function upperPercent(percent){if(!(percent>0))return '0';
                      var exponent=Math.floor(Math.log10(percent)),step=Math.pow(10,exponent-1);
                      var up=Math.ceil(percent/step-1e-9)*step,s=up.toFixed(Math.max(0,1-exponent));
                      return s.indexOf('.')>=0?s.replace(/\\.?0+$/,''):s;}
                    function label(count,weight,threshold,denominator){
                      var c=count.toLocaleString('en-US')+(count===1?' company':' companies');
                      if(denominator==null||denominator<=0)return c;
                      return c+', '+(weight/denominator*100).toFixed(1)+'%, each below '+upperPercent(threshold/denominator*100)+'%';}
                    function tileColor(f){f=Math.round(Math.max(0,Math.min(1,f))*20)/20;
                      var a=[31,119,180],b=[44,160,44],s='#';
                      for(var i=0;i<3;i++){var v=Math.round(a[i]+(b[i]-a[i])*f);s+=(v<16?'0':'')+v.toString(16).toUpperCase();}
                      return s;}
                    // ---- page logic without DOM: address, treemap levels, breadcrumb, search
                    // #grouping=<NAME>[&zoom=<key>.<depth>][&chart=bars], parts in any order, unknown parts ignored;
                    // chart=overlap of earlier pages gives the treemap, the matrix is in the Funds section
                    function parseAddress(hash){var r={g:null,zoom:null,chart:'treemap'};
                      String(hash||'').replace(/^#/,'').split('&').forEach(function(p){
                        var i=p.indexOf('='),k=i<0?p:p.substring(0,i),v=i<0?'':p.substring(i+1);
                        if(k==='grouping'){if(/^[A-Z_]+$/.test(v))r.g=v;}
                        else if(k==='chart'){if(v==='bars'||v==='treemap')r.chart=v;}
                        else if(k==='zoom'){var m=/^(all|[0-9]+)[.]([0-9]+)$/.exec(v);if(m)r.zoom={key:m[1],depth:+m[2]};}});
                      return r;}
                    function formatAddress(s){return '#grouping='+s.g+(s.zoom?'&zoom='+s.zoom.key+'.'+s.zoom.depth:'')
                      +(s.chart&&s.chart!=='treemap'?'&chart='+s.chart:'');}
                    function total(D,i){return D.c[i][2]+D.c[i][3];}
                    function member(D,i,f){var t0=total(D,i),t=t0*f;
                      return t>0?{idx:i,w:t,d:Math.max(0,Math.min(1,D.c[i][2]/t0))}:null;}
                    // The companies of a treemap level, largest first, or null when the level does not exist.
                    // key 'all' (Company view only): depth n>=1 is the n-th level of merged small companies.
                    // key <group number>: depth 0 is the group, depth n>=1 the n-th merged level inside it.
                    function levelMembers(D,g,key,depth){var list=[],x;
                      if(!(depth>=0)||depth>50)return null;
                      if(key==='all'){if(g!=='COMPANY'||depth<1)return null;
                        for(var i=0;i<D.c.length;i++){x=member(D,i,1);if(x)list.push(x);}}
                      else{var ms=g==='COMPANY'?null:(D.g[g]||[])[+key];if(!ms)return null;
                        ms.forEach(function(m){x=member(D,m[0],m[1]);if(x)list.push(x);});}
                      list.sort(function(a,b){return b.w-a.w||a.idx-b.idx;});
                      var area=D.chartWidth*D.chartHeight;
                      for(var d=0;d<depth;d++){var sum=0;list.forEach(function(m){sum+=m.w;});
                        list=merge(list,sum>0?area/sum:0,D.minTileArea,!(key==='all'&&d===0)).others;}
                      return list.length?list:null;}
                    // The levels from the overview down to the given one, with their names.
                    function crumbs(key,depth,groupLabel){var out=[{name:'All',level:null}];
                      if(key!=='all')out.push({name:groupLabel||'Group',level:{key:key,depth:0}});
                      for(var d=1;d<=depth;d++)out.push({name:'Smaller companies'+(d>1?' ('+d+')':''),level:{key:key,depth:d}});
                      return out;}
                    // Indexes of the companies whose name contains the text, in rank order.
                    function search(D,q){q=String(q||'').trim().toLowerCase();var out=[];if(!q)return out;
                      for(var i=0;i<D.c.length;i++)if(String(D.c[i][1]).toLowerCase().indexOf(q)>=0)out.push(i);
                      return out;}
                    return {squarify:squarify,merge:merge,label:label,upperPercent:upperPercent,tileColor:tileColor,
                      parseAddress:parseAddress,formatAddress:formatAddress,levelMembers:levelMembers,crumbs:crumbs,search:search};
                    })();
                    """;

    static final String JS = LAYOUT + """
                    (function(){
                    'use strict';
                    var D=JSON.parse(document.getElementById('lens-data').textContent);
                    var select=document.getElementById('grouping'),heading=document.getElementById('heading');
                    var searchBox=document.getElementById('search'),pane=document.querySelector('.chart-pane');
                    var nf=new Intl.NumberFormat('en-US',{minimumFractionDigits:2,maximumFractionDigits:2});
                    function plain(h){return nf.format(h/100);}
                    function amount(h){return D.currency+' '+plain(h);}
                    function share(h){if(D.denominator==null||D.denominator<=0)return '';
                      var p=h/D.denominator*100;if(p>0&&p<0.005)return '< 0.01%';return p.toFixed(2)+'%';}
                    function el(tag,cls,text){var e=document.createElement(tag);if(cls)e.className=cls;
                      if(text!=null)e.textContent=text;return e;}
                    function cells(tr,d,i,t){[d,i,t].forEach(function(v){tr.appendChild(el('td','num',plain(v)));});
                      tr.appendChild(el('td','num',share(t)));}
                    // ---- rows: the row of a company, and below it the row with its contributions
                    function companyRow(index,factor,detail,id,rank,child){
                      var c=D.c[index],f=factor||1,dir=Math.round(c[2]*f),ind=Math.round(c[3]*f);
                      var tr=el('tr',child?'child':'top');tr.id=id;tr.dataset.i=index;tr.dataset.f=f;
                      tr.dataset.d=detail==null?-1:detail;
                      tr.appendChild(el('td','rank',rank==null?'':String(rank)));
                      var td=el('td'),det=el('details');det.appendChild(el('summary',null,c[1]));
                      td.appendChild(det);tr.appendChild(td);cells(tr,dir,ind,dir+ind);return tr;}
                    function detailRow(index,f,detail){var c=D.c[index],tr=el('tr','detail'),td=el('td');
                      tr.appendChild(el('td'));td.colSpan=5;
                      var dt=detail>=0?detail:c[4];if(dt>=0)td.appendChild(el('p','detail',D.s[dt]));
                      var ul=el('ul','contrib sums');c[5].forEach(function(x){var v=Math.round(x[1]*f),li=el('li',x[2]?'direct':'indirect');
                        li.appendChild(el('span',null,D.s[x[0]]));
                        [x[2],!x[2],true].forEach(function(on){li.appendChild(el('span','num',on?plain(v):''));});
                        li.appendChild(el('span','num',share(v)));ul.appendChild(li);});
                      td.appendChild(ul);tr.appendChild(td);return tr;}
                    // rows built here get their second row when they are opened first
                    document.addEventListener('toggle',function(ev){var d=ev.target;
                      if(!d||d.tagName!=='DETAILS'||!d.parentNode)return;
                      var tr=d.parentNode.parentNode;if(!tr||tr.tagName!=='TR')return;
                      tr.classList.toggle('open',d.open);
                      if(d.open&&tr.dataset.i!=null){var n=tr.nextElementSibling;
                        if(!n||!n.classList.contains('detail'))
                          tr.parentNode.insertBefore(detailRow(+tr.dataset.i,+tr.dataset.f,+tr.dataset.d),tr.nextSibling);}},true);
                    // ---- company table: the rows of the server stay until sorting or more rows need others
                    var order=D.c.map(function(c,i){return i;}),limit=D.topRows;
                    var companyTable=document.querySelector('table.breakdown[data-grouping=COMPANY]');
                    var more=companyTable&&companyTable.querySelector('tbody.more');
                    function value(i,key){var c=D.c[i];return key==='label'?c[1]:key==='direct'?c[2]:key==='indirect'?c[3]:c[2]+c[3];}
                    function updateMore(){if(!more)return;var btn=more.querySelector('.show-more'),left=D.c.length-limit;
                      btn.hidden=left<=0;btn.textContent='Show '+Math.min(left,500)+' more ('+Math.max(left,0)+' not shown)';}
                    function renderCompanies(){
                      if(!companyTable)return;
                      companyTable.querySelectorAll('tbody.company').forEach(function(b){b.remove();});
                      var anchor=companyTable.querySelector('tbody.fixed');
                      order.slice(0,limit).forEach(function(i){var b=el('tbody','block company');b.dataset.index=i;
                        b.appendChild(companyRow(i,1,-1,D.c[i][0],i+1,false));companyTable.insertBefore(b,anchor);});
                      updateMore();}
                    if(more)more.querySelector('.show-more').addEventListener('click',function(){limit+=500;renderCompanies();});
                    // ---- groups
                    function expand(block,on){
                      var b=block.querySelector('.toggle');if(!b)return;b.setAttribute('aria-expanded',on?'true':'false');
                      if(on&&!block.dataset.built){
                        var g=block.closest('table').dataset.grouping,members=D.g[g][+block.dataset.group];
                        members.forEach(function(m){block.appendChild(companyRow(m[0],m[1],m[2],
                          g.toLowerCase()+'-'+D.c[m[0]][0],null,true));});
                        block.dataset.built='1';}
                      block.querySelectorAll('tr.child,tr.detail').forEach(function(r){r.hidden=!on;});}
                    document.querySelectorAll('tbody.group .toggle').forEach(function(b){
                      b.addEventListener('click',function(){var block=b.closest('tbody');
                        expand(block,b.getAttribute('aria-expanded')!=='true');});});
                    // ---- state: grouping, chart tab, treemap level, search text
                    var SVG='http://www.w3.org/2000/svg',AREA={x:0,y:0,w:D.chartWidth,h:D.chartHeight};
                    var zoom=null,current='COMPANY',tab='treemap',overview={},crumbList=[];
                    var query='',matches=[],shown=0;
                    function writeHash(){history.replaceState(null,'',Lens.formatAddress(
                      {g:current,zoom:zoom&&zoom.g===current?zoom:null,chart:tab}));}
                    // ---- treemap levels
                    function node(tag,attrs){var e=document.createElementNS(SVG,tag);
                      for(var k in attrs)e.setAttribute(k,attrs[k]);return e;}
                    function svgOf(g){return pane.querySelector('svg.treemap[data-grouping='+g+']');}
                    function tableOf(g){return document.querySelector('table.breakdown[data-grouping='+g+']');}
                    function groupBlock(g,key){var t=tableOf(g);return t&&t.querySelector('tbody.group[data-group="'+key+'"]');}
                    function rowId(g,i){return g==='COMPANY'?D.c[i][0]:g.toLowerCase()+'-'+D.c[i][0];}
                    function amountText(w){return amount(Math.round(w))+(share(w)?', '+share(w):'');}
                    function textAt(parent,x,y,width,text,cls){var max=Math.floor(width/6.2);if(max<2)return;
                      if(text.length>max)text=text.substring(0,Math.max(1,max-1))+'…';
                      var t=node('text',{'class':cls,x:x.toFixed(1),y:y.toFixed(1)});t.textContent=text;parent.appendChild(t);}
                    function drawCrumbs(g){var svg=svgOf(g);if(!svg)return;
                      var head=svg.closest('figure').querySelector('.chart-head'),nav=head.querySelector('nav.crumbs');
                      var on=!!zoom&&zoom.g===g;nav.textContent='';head.classList.toggle('overview',!on);
                      head.querySelector('.back').hidden=!on;if(!on)return;
                      var block=zoom.key==='all'?null:groupBlock(g,zoom.key);
                      crumbList=Lens.crumbs(zoom.key,zoom.depth,block?block.dataset.label:null);
                      crumbList.forEach(function(c,n){if(n>0)nav.appendChild(document.createTextNode(' › '));
                        if(n===crumbList.length-1){var s=el('span','crumb-current',c.name);s.setAttribute('aria-current','page');nav.appendChild(s);}
                        else{var b=el('button','crumb',c.name);b.type='button';b.dataset.n=n;nav.appendChild(b);}});}
                    function renderZoom(){var g=zoom.g,svg=svgOf(g);if(!svg)return;
                      if(overview[g]==null)overview[g]=svg.innerHTML;
                      while(svg.firstChild)svg.removeChild(svg.firstChild);
                      svg.appendChild(node('rect',{'class':'tm-bg',x:0,y:0,width:AREA.w,height:AREA.h}));
                      var list=Lens.levelMembers(D,g,zoom.key,zoom.depth)||[],sum=0;list.forEach(function(m){sum+=m.w;});
                      var merged=Lens.merge(list,sum>0?AREA.w*AREA.h/sum:0,D.minTileArea,true);
                      var items=merged.shown.slice();
                      if(merged.others.length)items.push({w:merged.weight,d:merged.weight>0?merged.direct/merged.weight:0,last:true,merged:merged});
                      Lens.squarify(items,AREA).forEach(function(t){var it=t.item,title=node('title',{});
                        if(it.merged){var text=Lens.label(it.merged.others.length,it.merged.weight,it.merged.threshold,D.denominator);
                          var gm=node('g',{'class':'tm-merged',role:'button',tabindex:'0','data-zoom':zoom.key,'data-depth':zoom.depth,
                            'aria-label':text+'. Press Enter to explore.'});
                          title.textContent=text+'\\nClick to explore';gm.appendChild(title);
                          gm.appendChild(node('rect',{'class':'tm-tile tm-other',x:t.x.toFixed(1),y:t.y.toFixed(1),width:t.w.toFixed(1),height:t.h.toFixed(1)}));
                          if(t.w>=48&&t.h>=20){textAt(gm,t.x+4,t.y+14,t.w-8,text,'tm-label tm-merged-label');
                            if(t.h>=34)textAt(gm,t.x+4,t.y+28,t.w-8,'Click to explore ›','tm-share tm-merged-label');}
                          svg.appendChild(gm);return;}
                        var id=rowId(g,it.idx),a=node('a',{href:'#'+id,'data-row':id});
                        var r=node('rect',{'class':'tm-tile',fill:Lens.tileColor(it.d),x:t.x.toFixed(1),y:t.y.toFixed(1),width:t.w.toFixed(1),height:t.h.toFixed(1)});
                        title.textContent=D.c[it.idx][1]+'\\n'+amountText(it.w);r.appendChild(title);a.appendChild(r);
                        if(t.w>=48&&t.h>=30){textAt(a,t.x+4,t.y+14,t.w-8,D.c[it.idx][1],'tm-label');textAt(a,t.x+4,t.y+27,t.w-8,share(it.w),'tm-share');}
                        svg.appendChild(a);});}
                    function restore(g){var svg=svgOf(g);
                      if(svg&&overview[g]!=null){svg.innerHTML=overview[g];delete overview[g];}}
                    // z: {g,key,depth}, or null for the overview; a level the page does not have gives the overview
                    function setZoom(z,focusTile,silent){var g=z?z.g:(zoom?zoom.g:current);
                      if(z&&!Lens.levelMembers(D,z.g,z.key,z.depth))z=null;
                      if(!z){zoom=null;restore(g);}else{zoom=z;renderZoom();}
                      drawCrumbs(g);if(!silent)writeHash();
                      if(focusTile){var svg=svgOf(g);
                        var f=svg&&(svg.querySelector('a[data-row]:not([tabindex="-1"])')||svg.querySelector('g.tm-merged[role=button]'));
                        if(f&&f.focus)f.focus({preventScroll:true});}}
                    function back(){if(!zoom)return;var l=Lens.crumbs(zoom.key,zoom.depth,null),p=l[l.length-2].level;
                      setZoom(p?{g:zoom.g,key:p.key,depth:p.depth}:null,true);}
                    function enter(m){var depth=+(m.dataset.depth||0)+1;
                      setZoom({g:m.closest('svg').dataset.grouping,key:m.dataset.zoom,depth:depth},true);}
                    // ---- chart tabs
                    var tabs=Array.prototype.slice.call(document.querySelectorAll('.tabs [role=tab]'));
                    function setTab(name,focus){if(name!=='bars')name='treemap';
                      tab=name;pane.dataset.tab=name;
                      tabs.forEach(function(b){var on=b.dataset.chart===name;b.setAttribute('aria-selected',on?'true':'false');
                        b.tabIndex=on?0:-1;if(on&&focus)b.focus();});}
                    tabs.forEach(function(b,n){
                      b.addEventListener('click',function(){setTab(b.dataset.chart,false);writeHash();});
                      b.addEventListener('keydown',function(ev){var to=ev.key==='ArrowRight'?n+1:ev.key==='ArrowLeft'?n-1:
                        ev.key==='Home'?0:ev.key==='End'?tabs.length-1:null;if(to==null)return;ev.preventDefault();
                        setTab(tabs[(to+tabs.length)%tabs.length].dataset.chart,true);writeHash();});});
                    // ---- search: a flat list of the matching companies in the table of the current grouping
                    function renderSearch(){
                      document.querySelectorAll('tbody.result').forEach(function(b){b.remove();});
                      document.querySelectorAll('table.searching').forEach(function(t){t.classList.remove('searching');});
                      if(!query)return;var table=tableOf(current);if(!table)return;table.classList.add('searching');
                      function info(text){var b=el('tbody','result info'),tr=el('tr'),td=el('td',null,text);td.colSpan=5;
                        tr.appendChild(el('td'));tr.appendChild(td);b.appendChild(tr);table.appendChild(b);return td;}
                      info(matches.length===0?'No company matches “'+query+'”.'
                        :(matches.length===1?'1 company matches':matches.length.toLocaleString('en-US')+' of '
                          +D.c.length.toLocaleString('en-US')+' companies match')+' “'+query+'”.');
                      matches.slice(0,shown).forEach(function(i){var b=el('tbody','result');
                        var r=companyRow(i,1,-1,'q-'+D.c[i][0],i+1,false);r.dataset.ref=D.c[i][0];b.appendChild(r);table.appendChild(b);});
                      if(matches.length>shown){var left=matches.length-shown,btn=el('button','show-more','Show '+Math.min(left,200)+' more');
                        btn.type='button';btn.addEventListener('click',function(){shown+=200;renderSearch();});
                        var td=info(left+' more not shown.');td.appendChild(btn);}}
                    function setQuery(text){query=String(text||'').trim();matches=Lens.search(D,query);shown=200;renderSearch();}
                    if(searchBox)searchBox.addEventListener('input',function(){setQuery(searchBox.value);});
                    // ---- grouping
                    function show(name){
                      if(!document.getElementById('view-'+name))name='COMPANY';
                      if(zoom&&zoom.g!==name){var old=zoom.g;zoom=null;restore(old);drawCrumbs(old);}
                      current=name;
                      document.querySelectorAll('section.view,figure.chart[data-grouping]').forEach(function(v){
                        v.hidden=v.dataset.grouping!==name;});
                      select.value=name;var title=D.headings[name]||heading.textContent;
                      heading.textContent=title;document.title=title;renderSearch();}
                    function fromAddress(){var a=Lens.parseAddress(location.hash);show(a.g||'COMPANY');setTab(a.chart,false);
                      setZoom(a.zoom?{g:current,key:a.zoom.key,depth:a.zoom.depth}:null,false,true);}
                    select.disabled=false;fromAddress();updateMore();
                    select.addEventListener('change',function(){show(select.value);writeHash();});
                    window.addEventListener('hashchange',fromAddress);
                    // ---- selection: highlight and open the row, scroll only when it is not visible
                    function clearSelection(){document.querySelectorAll('.selected').forEach(function(e){e.classList.remove('selected');});}
                    function mark(id){document.querySelectorAll('[data-row="'+id+'"] rect').forEach(function(t){t.classList.add('selected');});}
                    function findRow(id){
                      var row=document.getElementById(id);if(row)return row;
                      var m=/^(?:([a-z_]+)-)?(c[0-9a-f]+)$/.exec(id);if(!m)return null;
                      var index=D.c.findIndex(function(c){return c[0]===m[2];});if(index<0)return null;
                      if(!m[1]){var pos=order.indexOf(index);if(pos>=limit){limit=pos+1;renderCompanies();}
                        return document.getElementById(id);}
                      var g=m[1].toUpperCase(),groups=D.g[g]||[];
                      // on a level of one group, the row below that group; else the first group holding the company
                      var keys=[];if(zoom&&zoom.g===g&&zoom.key!=='all')keys.push(+zoom.key);
                      for(var gi=0;gi<groups.length;gi++)keys.push(gi);
                      for(var k=0;k<keys.length;k++)if((groups[keys[k]]||[]).some(function(x){return x[0]===index;})){
                        var block=groupBlock(g,keys[k]);if(block)expand(block,true);break;}
                      return document.getElementById(id);}
                    function selectRow(id){if(query){if(searchBox)searchBox.value='';setQuery('');}
                      clearSelection();var row=findRow(id);if(!row)return;
                      var block=row.closest('tbody');
                      if(row.classList.contains('child')||block.classList.contains('group'))expand(block,true);
                      var d=row.querySelector('details');if(d)d.open=true;
                      row.classList.add('selected');row.scrollIntoView({block:'nearest'});mark(id);}
                    document.addEventListener('click',function(ev){var t=ev.target;if(!t.closest)return;
                      // section links scroll; the address keeps the state of the view
                      var sec=t.closest('a[data-section]');
                      if(sec){var to=document.getElementById(sec.dataset.section);
                        if(to){ev.preventDefault();to.scrollIntoView({block:'start'});}return;}
                      document.querySelectorAll('details.hint[open]').forEach(function(h){if(!h.contains(t))h.open=false;});
                      var m=t.closest('g.tm-merged[role=button]');if(m){ev.preventDefault();enter(m);return;}
                      var z=t.closest('a.tm-zoom');
                      if(z){ev.preventDefault();setZoom({g:z.closest('svg').dataset.grouping,key:z.dataset.zoom,depth:0},true);
                        selectRow(z.dataset.row);return;}
                      var c=t.closest('nav.crumbs button.crumb');
                      if(c&&zoom){var lv=crumbList[+c.dataset.n].level;
                        setZoom(lv?{g:zoom.g,key:lv.key,depth:lv.depth}:null,true);return;}
                      if(t.closest('button.back')){back();return;}
                      // delegated, so tiles of a zoomed level select their rows too
                      var a=t.closest('a[data-row]');if(a){ev.preventDefault();selectRow(a.dataset.row);}});
                    document.addEventListener('keydown',function(ev){var t=ev.target;
                      var m=t.closest&&t.closest('g.tm-merged[role=button]');
                      if(m&&(ev.key==='Enter'||ev.key===' ')){ev.preventDefault();enter(m);return;}
                      if(ev.key!=='Escape')return;
                      if(t===searchBox){if(searchBox.value){searchBox.value='';setQuery('');}return;}
                      var h=document.querySelector('details.hint[open]');if(h){h.open=false;return;}
                      if(zoom&&tab==='treemap'){ev.preventDefault();back();}});
                    document.querySelectorAll('table.breakdown').forEach(function(t){t.addEventListener('click',function(ev){
                      var r=ev.target.closest('tr.top,tr.child');if(!r||!r.id||ev.target.closest('summary,button'))return;
                      clearSelection();r.classList.add('selected');mark(r.dataset.ref||r.id);});});
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
                    // ---- staleness: the notice has its place in the bar, so nothing moves when it appears
                    var hours=Math.floor((Date.now()-new Date(D.generatedAt).getTime())/3600000);
                    if(hours>=1){var s=document.getElementById('stale');
                      var age=hours<48?hours+(hours===1?' hour':' hours'):Math.floor(hours/24)+' days';
                      s.textContent='This page is '+age+' old. The Portfolio Lens job may not be running.';s.hidden=false;}
                    })();
                    """;
}
