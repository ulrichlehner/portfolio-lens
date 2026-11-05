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
                    function merge(members,scale,minArea){var shown=[],others=[],weight=0,direct=0;
                      for(var i=0;i<members.length;i++){var m=members[i];
                        if(m.w*scale>=minArea)shown.push(m);else if(m.w>0){others.push(m);weight+=m.w;direct+=m.w*m.d;}}
                      return {shown:shown,others:others,weight:weight,direct:direct,threshold:scale>0?minArea/scale:0};}
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
                    return {squarify:squarify,merge:merge,label:label,upperPercent:upperPercent,tileColor:tileColor};
                    })();
                    """;

    static final String JS = LAYOUT + """
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
                    // ---- treemap: mosaic of merged tiles and drill-down into them
                    var SVG='http://www.w3.org/2000/svg',AREA={x:0,y:0,w:D.chartWidth,h:D.chartHeight};
                    var zoom=null,current='COMPANY',overview={};
                    function node(tag,attrs){var e=document.createElementNS(SVG,tag);
                      for(var k in attrs)e.setAttribute(k,attrs[k]);return e;}
                    function total(i){return D.c[i][2]+D.c[i][3];}
                    // the merged members of a tile of the overview, with their weights in that grouping
                    function members(g,key){var list=(D.m[g]||{})[key]||[],factor={};
                      if(key!=='all')(D.g[g][+key]||[]).forEach(function(m){factor[m[0]]=m[1];});
                      return list.map(function(i){var f=factor[i]==null?1:factor[i],t=total(i)*f;
                        return {idx:i,w:t,d:t>0?Math.max(0,Math.min(1,D.c[i][2]/total(i))):0};})
                        .sort(function(a,b){return b.w-a.w;});}
                    // the members of a zoom level: each deeper level holds the merged tail of the one above
                    function levelMembers(z){var list=members(z.g,z.key);
                      for(var d=1;d<z.depth;d++){var sum=0;list.forEach(function(m){sum+=m.w;});
                        list=Lens.merge(list,sum>0?AREA.w*AREA.h/sum:0,D.minTileArea).others;}
                      return list;}
                    function rowId(g,i){return g==='COMPANY'?D.c[i][0]:g.toLowerCase()+'-'+D.c[i][0];}
                    function amountText(w){return amount(Math.round(w))+(share(w)?', '+share(w):'');}
                    function textAt(parent,x,y,width,text,cls){var max=Math.floor(width/6.2);if(max<2)return;
                      if(text.length>max)text=text.substring(0,Math.max(1,max-1))+'\u2026';
                      var t=node('text',{'class':cls,x:x.toFixed(1),y:y.toFixed(1)});t.textContent=text;parent.appendChild(t);}
                    // draws every merged company as an unlabelled cell; the tooltip is made on hover
                    function mosaic(group,rect,list){if(group.dataset.mosaic)return;group.dataset.mosaic='1';
                      var area={x:+rect.getAttribute('x'),y:+rect.getAttribute('y'),w:+rect.getAttribute('width'),h:+rect.getAttribute('height')};
                      var cells=node('g',{'class':'tm-mosaic'});
                      Lens.squarify(list,area).forEach(function(t){cells.appendChild(node('rect',{x:t.x.toFixed(2),y:t.y.toFixed(2),
                        width:t.w.toFixed(2),height:t.h.toFixed(2),fill:Lens.tileColor(t.item.d),'data-i':t.item.idx}));});
                      cells.addEventListener('mouseover',function(ev){var r=ev.target;if(!r.dataset||r.dataset.i==null||r.firstChild)return;
                        var i=+r.dataset.i,title=node('title',{});title.textContent=D.c[i][1]+'\\n'+amountText(total(i));r.appendChild(title);});
                      rect.parentNode.insertBefore(cells,rect.nextSibling);
                      // a frame keeps the merged companies readable as one tile that can be explored
                      var frame=node('rect',{'class':'tm-merged-frame',x:area.x,y:area.y,width:area.w,height:area.h});
                      cells.parentNode.insertBefore(frame,cells.nextSibling);}
                    function mosaics(g){var svg=document.querySelector('svg.treemap[data-grouping='+g+']');if(!svg)return;
                      svg.querySelectorAll('g.tm-merged').forEach(function(m){var depth=+(m.dataset.depth||0);
                        var list=depth?levelMembers({g:g,key:m.dataset.zoom,depth:depth+1}):members(g,m.dataset.zoom);
                        mosaic(m,m.querySelector('rect.tm-other'),list);});}
                    function crumbs(g){var svg=document.querySelector('svg.treemap[data-grouping='+g+']');if(!svg)return;
                      var nav=svg.parentNode.querySelector('nav.crumbs');nav.textContent='';
                      if(!zoom||zoom.g!==g){nav.hidden=true;return;}
                      nav.hidden=false;
                      var block=zoom.key==='all'?null:document.querySelector('table.breakdown[data-grouping='+g+'] tbody.group[data-group="'+zoom.key+'"]');
                      var prefix=block?block.dataset.label+': ':'';
                      for(var d=0;d<=zoom.depth;d++){if(d>0)nav.appendChild(document.createTextNode(' \u203A '));
                        var name=d===0?'All':prefix+'smaller companies'+(d>1?' ('+d+')':'');
                        if(d>0&&!prefix)name='S'+name.substring(1);
                        if(d===zoom.depth){var s=el('span','crumb-current',name);s.setAttribute('aria-current','page');nav.appendChild(s);}
                        else{var b=el('button','crumb',name);b.type='button';b.dataset.depth=d;nav.appendChild(b);}}
                      nav.appendChild(el('span','crumb-hint',' (Esc goes back)'));}
                    function renderZoom(){var g=zoom.g,svg=document.querySelector('svg.treemap[data-grouping='+g+']');if(!svg)return;
                      if(overview[g]==null)overview[g]=svg.innerHTML;
                      while(svg.firstChild)svg.removeChild(svg.firstChild);
                      svg.appendChild(node('rect',{'class':'tm-bg',x:0,y:0,width:AREA.w,height:AREA.h}));
                      var list=levelMembers(zoom),sum=0;list.forEach(function(m){sum+=m.w;});
                      var merged=Lens.merge(list,sum>0?AREA.w*AREA.h/sum:0,D.minTileArea);
                      var items=merged.shown.slice();
                      if(merged.others.length)items.push({w:merged.weight,d:merged.weight>0?merged.direct/merged.weight:0,last:true,merged:merged});
                      Lens.squarify(items,AREA).forEach(function(t){var it=t.item;
                        if(it.merged){var text=Lens.label(it.merged.others.length,it.merged.weight,it.merged.threshold,D.denominator);
                          var gm=node('g',{'class':'tm-merged',role:'button',tabindex:'0','data-zoom':zoom.key,'data-depth':zoom.depth,
                            'aria-label':text+'. Press Enter to explore.'});
                          var title=node('title',{});title.textContent=text+'\\nClick to explore';gm.appendChild(title);
                          gm.appendChild(node('rect',{'class':'tm-tile tm-other',x:t.x.toFixed(1),y:t.y.toFixed(1),width:t.w.toFixed(1),height:t.h.toFixed(1)}));
                          if(t.w>=48&&t.h>=20){textAt(gm,t.x+4,t.y+14,t.w-8,text,'tm-label tm-merged-label');
                            if(t.h>=34)textAt(gm,t.x+4,t.y+28,t.w-8,'Click to explore \u203A','tm-share tm-merged-label');}
                          svg.appendChild(gm);return;}
                        var id=rowId(g,it.idx),a=node('a',{href:'#'+id,'data-row':id});
                        var r=node('rect',{'class':'tm-tile',fill:Lens.tileColor(it.d),x:t.x.toFixed(1),y:t.y.toFixed(1),width:t.w.toFixed(1),height:t.h.toFixed(1)});
                        var title=node('title',{});title.textContent=D.c[it.idx][1]+'\\n'+amountText(it.w);r.appendChild(title);a.appendChild(r);
                        if(t.w>=48&&t.h>=30){textAt(a,t.x+4,t.y+14,t.w-8,D.c[it.idx][1],'tm-label');textAt(a,t.x+4,t.y+27,t.w-8,share(it.w),'tm-share');}
                        svg.appendChild(a);});
                      mosaics(g);}
                    function restore(g){var svg=document.querySelector('svg.treemap[data-grouping='+g+']');
                      if(svg&&overview[g]!=null){svg.innerHTML=overview[g];delete overview[g];}}
                    function setZoom(z,focusTile){var g=z?z.g:current;
                      if(!z||z.depth===0){zoom=null;restore(g);mosaics(g);}
                      else{zoom=z;renderZoom();}
                      crumbs(g);writeHash();
                      if(focusTile){var svg=document.querySelector('svg.treemap[data-grouping='+g+']');
                        var f=svg&&(svg.querySelector('g.tm-merged')||svg.querySelector('a[data-row]'));if(f)f.focus();}}
                    function enter(m){var depth=+(m.dataset.depth||0)+1;
                      setZoom({g:m.closest('svg').dataset.grouping,key:m.dataset.zoom,depth:depth},true);}
                    document.addEventListener('click',function(ev){
                      var m=ev.target.closest&&ev.target.closest('g.tm-merged');if(m){ev.preventDefault();enter(m);return;}
                      var c=ev.target.closest&&ev.target.closest('nav.crumbs button.crumb');
                      if(c&&zoom){setZoom({g:zoom.g,key:zoom.key,depth:+c.dataset.depth},true);}});
                    document.addEventListener('keydown',function(ev){
                      var m=ev.target.closest&&ev.target.closest('g.tm-merged');
                      if(m&&(ev.key==='Enter'||ev.key===' ')){ev.preventDefault();enter(m);return;}
                      if(ev.key==='Escape'&&zoom){ev.preventDefault();setZoom({g:zoom.g,key:zoom.key,depth:zoom.depth-1},true);}});
                    // ---- grouping
                    function writeHash(){history.replaceState(null,'','#grouping='+current
                      +(zoom&&zoom.g===current?'&zoom='+zoom.key+'.'+zoom.depth:''));}
                    function show(name){
                      var view=document.getElementById('view-'+name);if(!view){name='COMPANY';view=document.getElementById('view-COMPANY');}
                      if(zoom&&zoom.g!==name){restore(zoom.g);crumbs(zoom.g);zoom=null;}
                      current=name;
                      document.querySelectorAll('section.view').forEach(function(v){v.hidden=v!==view;});
                      select.value=name;var title=D.headings[name]||heading.textContent;
                      heading.textContent=title;document.title=title;mosaics(name);}
                    function fromHash(){var m=/^#grouping=([A-Z_]+)(?:&zoom=(all|[0-9]+)\\.([0-9]+))?$/.exec(location.hash);
                      return m?{g:m[1],zoom:m[2]!=null?{g:m[1],key:m[2],depth:+m[3]}:null}:null;}
                    function fromAddress(){var h=fromHash();show(h?h.g:'COMPANY');
                      if(h&&h.zoom&&(D.m[h.g]||{})[h.zoom.key]){setZoom(h.zoom,false);}else{crumbs(current);}}
                    select.disabled=false;fromAddress();
                    select.addEventListener('change',function(){show(select.value);writeHash();});
                    window.addEventListener('hashchange',fromAddress);
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
                    // delegated, so tiles of a zoomed level select their rows too
                    document.addEventListener('click',function(ev){var a=ev.target.closest&&ev.target.closest('a[data-row]');
                      if(!a)return;ev.preventDefault();selectRow(a.dataset.row);});
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
