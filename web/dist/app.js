const $ = id => document.getElementById(id);
const state = { status: {}, events: [], settings: {} };
let staticDemo = false;
const history = { motion: [], area: [], stability: [], light: [], contrast: [], dx: [], dy: [], processing: [], fps: [], speed: [], edge: [], objectAge: [] };
let lastSampleAt = 0;
const percent = value => `${Math.round(Number(value || 0) * 100)}%`;

function pushSample(sample) {
  const stamp = Number(sample.updatedAt || 0);
  if (!stamp || stamp === lastSampleAt) return;
  lastSampleAt = stamp;
  const values = { motion:+sample.motion||0, area:+sample.motionArea||0, stability:Number.isFinite(+sample.stability)?+sample.stability:1, light:+sample.brightness||0, contrast:+sample.sceneContrast||0, dx:+sample.directionX||0, dy:+sample.directionY||0, processing:+sample.processingMs||0, fps:+sample.fps||0, speed:+sample.movementSpeed||0, edge:+sample.boundaryDistance||0, objectAge:(+sample.newObjectPersistenceMs||0)/1000 };
  Object.entries(values).forEach(([key,value]) => { history[key].push(value); if (history[key].length > 60) history[key].shift(); });
  drawCharts();
}

function chart(id, values, min, max, color) {
  const canvas=$(id); if(!canvas) return;
  const ratio=devicePixelRatio||1, width=canvas.clientWidth||300, height=canvas.clientHeight||92;
  canvas.width=width*ratio; canvas.height=height*ratio;
  const ctx=canvas.getContext('2d'); ctx.scale(ratio,ratio); ctx.clearRect(0,0,width,height);
  ctx.strokeStyle='#17332d12'; ctx.lineWidth=1;
  [.25,.5,.75].forEach(pos=>{ctx.beginPath();ctx.moveTo(0,height*pos);ctx.lineTo(width,height*pos);ctx.stroke()});
  if(values.length<2) return;
  const point=(value,index)=>[index/(values.length-1)*width,height*(1-Math.max(0,Math.min(1,(value-min)/(max-min))))];
  const gradient=ctx.createLinearGradient(0,0,0,height); gradient.addColorStop(0,color+'42'); gradient.addColorStop(1,color+'00');
  ctx.beginPath(); let [x,y]=point(values[0],0); ctx.moveTo(x,height); ctx.lineTo(x,y);
  values.slice(1).forEach((value,index)=>{[x,y]=point(value,index+1);ctx.lineTo(x,y)}); ctx.lineTo(width,height); ctx.closePath(); ctx.fillStyle=gradient; ctx.fill();
  ctx.beginPath(); values.forEach((value,index)=>{const [a,b]=point(value,index);index?ctx.lineTo(a,b):ctx.moveTo(a,b)}); ctx.strokeStyle=color; ctx.lineWidth=2.4; ctx.lineJoin='round'; ctx.stroke();
  const [a,b]=point(values.at(-1),values.length-1); ctx.beginPath();ctx.arc(a,b,3.5,0,Math.PI*2);ctx.fillStyle=color;ctx.fill();
}

function drawCharts(){chart('motionChart',history.motion,0,1,'#f36f5d');chart('areaChart',history.area,0,1,'#34b98d');chart('stabilityChart',history.stability,0,1,'#5c8ff0');chart('lightChart',history.light,0,1,'#e3a02e');chart('contrastChart',history.contrast,0,1,'#71859f');chart('dxChart',history.dx,-.08,.08,'#36a883');chart('dyChart',history.dy,-.08,.08,'#8d70c8');chart('processingChart',history.processing,0,120,'#8d70c8');chart('fpsChart',history.fps,0,30,'#17332d');chart('speedChart',history.speed,0,1,'#e3a02e');chart('edgeChart',history.edge,0,.5,'#36a8b8');chart('objectAgeChart',history.objectAge,0,20,'#8d70c8')}

function renderStatus(sample) {
  state.status=sample;
  const fresh=sample.online&&Date.now()-Number(sample.updatedAt||0)<10000;
  $('deviceName').textContent=sample.device||'Waiting for Android';
  $('lastSeen').textContent=fresh?'Receiving live measurements now':'Last update was over 10 seconds ago';
  $('onlineLabel').textContent=fresh?'ONLINE':'OFFLINE'; $('onlineDot').classList.toggle('live',fresh);
  $('status').textContent=sample.status||'No signal'; $('motion').textContent=percent(sample.motion); $('area').textContent=percent(sample.motionArea); $('stability').textContent=percent(sample.stability??1); $('direction').textContent=sample.direction||'STILL'; $('directionTag').textContent=sample.direction||'STILL'; $('light').textContent=percent(sample.brightness); $('contrast').textContent=percent(sample.sceneContrast); $('processing').textContent=`${Math.round(sample.processingMs||0)} ms`; $('fps').textContent=sample.fps||0;
  $('speed').textContent=`${Math.round((+sample.movementSpeed||0)*100)} /s`; $('edge').textContent=sample.boundaryDistance==null?'—':percent(sample.boundaryDistance); $('eta').textContent=sample.boundaryEtaSeconds==null?'—':`${(+sample.boundaryEtaSeconds).toFixed(1)} s`; $('quiet').textContent=`${Math.round((+sample.longestQuietMs||0)/1000)} s`; $('objectAge').textContent=`${Math.round((+sample.newObjectPersistenceMs||0)/1000)} s`; $('objects').textContent=(sample.detectedObjects||[]).map(item=>item.label).join(', ')||'NONE';
  $('speedNow').textContent=$('speed').textContent; $('edgeNow').textContent=$('edge').textContent; $('objectAgeNow').textContent=$('objectAge').textContent;
  $('motionNow').textContent=$('motion').textContent; $('areaNow').textContent=$('area').textContent; $('stabilityNow').textContent=$('stability').textContent; $('lightNow').textContent=$('light').textContent; $('contrastNow').textContent=$('contrast').textContent; $('dxNow').textContent=(+sample.directionX||0).toFixed(3); $('dyNow').textContent=(+sample.directionY||0).toFixed(3); $('processingNow').textContent=$('processing').textContent; $('fpsNow').textContent=`${sample.fps||0} fps`;
  const dot=$('trace'); dot.style.setProperty('--x',`${50+Math.max(-35,Math.min(35,(+sample.directionX||0)*500))}%`); dot.style.setProperty('--y',`${50+Math.max(-35,Math.min(35,(+sample.directionY||0)*500))}%`);
  const zone=$('zone'); zone.className=`zone ${sample.insideZone===false?'bad':sample.insideZone===true?'good':'neutral'}`; zone.textContent=sample.insideZone===false?'OUTSIDE ZONE':sample.insideZone===true?'INSIDE ZONE':'NO ZONE SIGNAL'; pushSample(sample);
}

function escapeHtml(value){const el=document.createElement('span');el.textContent=String(value);return el.innerHTML}
function renderEvents(events){state.events=events;$('events').innerHTML=events.length?events.map(event=>`<div class="event ${event.severity>=3?'high':''}"><div><b>${escapeHtml(event.title)}</b><small>${new Date(Number(event.timestamp)).toLocaleTimeString()}</small></div><p>${escapeHtml(event.detail||event.kind)}</p><em>${escapeHtml(event.kind)}</em></div>`).join(''):'<div class="empty"><span>✓</span><b>All quiet</b><small>Saved moments appear here.</small></div>'}
function renderSettings(settings){state.settings=settings||{};document.querySelectorAll('[data-rule]').forEach(input=>{if(typeof settings?.[input.dataset.rule]==='boolean')input.checked=settings[input.dataset.rule]});$('settingsState').textContent='SYNCED'}

const demoEvents=[{id:'demo-zone',timestamp:Date.now()-420000,kind:'boundary',title:'Safe-zone crossing',detail:'Demo evidence: movement stayed outside the custom drawn shape for 2 seconds.',severity:3},{id:'demo-camera',timestamp:Date.now()-780000,kind:'system',title:'Camera view changed',detail:'Demo evidence: most of the picture changed at once.',severity:2}];
function staticSample(){const t=Date.now()/1000;const motion=(Math.sin(t*.8)+1)/2*.18+.025;return {online:true,device:'Kola showcase mode',status:motion>.13?'ACTIVE':'CALM',fps:6,motion,motionArea:motion*.72,stability:.96-motion*.12,brightness:.34,sceneContrast:.34,direction:Math.sin(t*.5)>.2?'RIGHT':'STILL',directionX:.012,directionY:0,movementSpeed:motion*.55,boundaryDistance:.14,boundaryEtaSeconds:motion>.13?4.8:null,longestQuietMs:Math.round((1-motion)*21000),newObjectPersistenceMs:3400,detectedObjects:[{label:'baby',confidence:.82}],updatedAt:Date.now()}}
fetch('/api/state').then(response=>{if(!response.ok)throw Error();return response.json()}).then(data=>{renderStatus(data.status);renderEvents(data.events);renderSettings(data.settings)}).catch(()=>{staticDemo=true;renderStatus(staticSample());renderEvents(demoEvents);renderSettings({zone_exit:true,movement_burst:true,extended_quiet:true,camera_moved:true,view_blocked:true,unsafe_item:true})});
const stream=new EventSource('/api/stream');
stream.addEventListener('state',event=>{const data=JSON.parse(event.data);renderStatus(data.status);renderEvents(data.events);renderSettings(data.settings)});
stream.addEventListener('status',event=>renderStatus(JSON.parse(event.data)));
stream.addEventListener('incident',event=>renderEvents([JSON.parse(event.data),...state.events].slice(0,100)));
stream.addEventListener('settings',event=>renderSettings(JSON.parse(event.data)));
stream.addEventListener('error',()=>{if(!state.status.updatedAt||!state.status.online){staticDemo=true;renderEvents(demoEvents)}});
setInterval(()=>{if(staticDemo)renderStatus(staticSample())},1200);
setInterval(()=>renderStatus(state.status),3000); addEventListener('resize',drawCharts);
$('demo').addEventListener('click',async()=>{const event={id:String(Date.now()),timestamp:Date.now(),kind:'motion',title:'Test movement event',detail:'Manual dashboard test; no automatic detection claim.',severity:1};try{const response=await fetch('/api/events',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(event)});if(!response.ok)throw Error()}catch{renderEvents([event,...state.events])}});
document.querySelectorAll('[data-rule]').forEach(input=>input.addEventListener('change',async()=>{state.settings[input.dataset.rule]=input.checked;$('settingsState').textContent='SAVING';try{const response=await fetch('/api/settings',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(state.settings)});if(!response.ok)throw Error();renderSettings(await response.json())}catch{$('settingsState').textContent='RETRY'}}));

const videoSelect=$('videoSelect'), demoVideo=$('demoVideo'), videoPlay=$('videoPlay'), videoStop=$('videoStop');
async function loadVideos(){if(!videoSelect)return;try{let response=await fetch('/api/videos');let videos=response.ok?await response.json():[];if(!videos.length){response=await fetch('/videos.json');videos=await response.json()}videoSelect.innerHTML=videos.length?videos.map(video=>`<option value="${escapeHtml(video.url)}">${escapeHtml(video.name)}</option>`).join(''):'<option value="">No saved videos found</option>';selectVideo()}catch{videoSelect.innerHTML='<option value="">No saved videos found</option>'}}
function selectVideo(){const option=videoSelect?.selectedOptions?.[0];if(!option?.value)return;demoVideo.src=option.value;demoVideo.load();$('videoFile').textContent=option.textContent}
videoSelect?.addEventListener('change',selectVideo);videoPlay?.addEventListener('click',()=>{if(!demoVideo.src)selectVideo();demoVideo.play().catch(()=>{})});videoStop?.addEventListener('click',()=>{demoVideo.pause();demoVideo.currentTime=0});loadVideos();
