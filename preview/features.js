'use strict';
let lastRemoteSequence=null;
const aerialQueue=new CinemaScenes.Queue();let requestedFilm='', renderedSelection='';
let cinemaState, aerialFilms = [], motionTimer, saverTimer, sceneTimer, lastState = '', lastMediaSource = '', saver = false, sceneIndex = 0;
const sceneLayer = document.createElement('div'); sceneLayer.id = 'scene-layer';
const sceneImage = document.createElement('img'); sceneImage.alt = ''; sceneImage.hidden = true;
const sceneVideo = document.createElement('video'); sceneVideo.muted = true; sceneVideo.playsInline = true; sceneVideo.preload = 'none'; sceneVideo.hidden = true;
const ambientCaption = document.createElement('div'); ambientCaption.id = 'ambient-caption';
const ambientTime = document.createElement('time'); ambientTime.id = 'ambient-time';
sceneLayer.append(sceneImage,sceneVideo); tv.insertBefore(sceneLayer,tv.querySelector('.shade')); tv.append(ambientCaption,ambientTime);
const brandFor = id => id.includes('nuvio')?'nuvio':id.includes('smarttube')?'smarttube':id.includes('spotify')?'spotify':id.includes('netflix')?'netflix':id.includes('vending')?'playstore':'inputs';
async function cinemaEdit(command) { try { const response = await fetch('/phone/api/edit',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(command)});const result=await response.json();if(!response.ok)throw new Error(result.error);await syncCinema();return true; } catch(e){notify(e.message);return false;} }
function settingsChoice(key,value){return cinemaEdit({op:'settings',settings:{[key]:value}});}
async function syncCinema() {
  try { const response=await fetch('/phone/api/state');if(!response.ok)throw new Error('Preview server unavailable');const state=await response.json();const event=state.remoteEvent;const visual={...state};delete visual.remoteEvent;delete visual.remoteEvents;const signature=JSON.stringify(visual);if(signature!==lastState){lastState=signature;cinemaState=state;renderCinema();}if(lastRemoteSequence===null)lastRemoteSequence=event?.seq||0;else for(const pending of state.remoteEvents||[event])if(pending&&pending.seq>lastRemoteSequence){lastRemoteSequence=pending.seq;previewRemote(pending.key);} }
  catch(e){notify('Preview server unavailable. Restart python3 preview/server.py.');}
}
function cinemaTile(app, themed) {
  const node=tile(app.name,brandFor(app.id),themed);node.dataset.app=app.id;
  // The unthemed lower row follows Android; the dock keeps our custom cream geometry.
  if(themed&&brandFor(app.id)==='netflix')node.querySelector('svg').innerHTML='<path d="M24 8H42V92H24ZM59 8H77V92H59ZM24 8H44L77 92H57Z" fill="#eaeae2"/>';
  if(app.icon){const image=document.createElement('img');image.src='/phone/media/'+app.icon;image.alt='';node.querySelector('svg').replaceWith(image);}
  node.addEventListener('contextmenu',event=>{event.preventDefault();appMenu(app);});
  let hold;node.addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.repeat)hold=setTimeout(()=>appMenu(app),650);if(event.key==='ContextMenu'){event.preventDefault();appMenu(app);}});node.addEventListener('keyup',()=>clearTimeout(hold));node.addEventListener('blur',()=>clearTimeout(hold));return node;
}
function renderCinema(){
  const settings=cinemaState.settings;const activeId=document.activeElement?.dataset.app;const wasBrowsing=tv.classList.contains('browsing');
  dock.replaceChildren(...cinemaState.dock.map(id=>cinemaState.apps.find(app=>app.id===id&&!app.hidden)).filter(Boolean).map(app=>cinemaTile(app,true)));
  const rest=cinemaState.apps.filter(app=>!app.hidden&&!cinemaState.dock.includes(app.id)&&!app.input);
  const others=document.querySelector('#other-apps');others.replaceChildren();
  for(const app of rest){const node=cinemaTile(app,false);const folder=Object.entries(cinemaState.folders).find(([,ids])=>ids.includes(app.id));if(folder){const label=document.createElement('small');label.textContent=folder[0];node.append(label);}others.append(node);}
  document.querySelector('header').classList.toggle('clock-right',settings.clock_right);tv.classList.toggle('large-clock',settings.large_clock);tv.classList.toggle('two-rows',settings.two_rows);tv.classList.toggle('peek-row',settings.peek_row);tv.classList.toggle('no-names',!settings.show_names);tv.classList.toggle('no-plate',!settings.home_plate);tv.classList.toggle('high-contrast',settings.high_contrast);tv.classList.toggle('reduced-motion',settings.reduced_motion);tv.classList.toggle('lite',settings.effects==='lite');
  tv.style.setProperty('--dim',settings.wallpaper_dim/100);
  renderWeather();
  panel.querySelectorAll('.setting-switch').forEach(button=>{if(!button.dataset.pending)button.setAttribute('aria-checked',String(!!settings[button.dataset.setting]));});
  const source=settings.wallpaper_selection;
  if(['misty','dunes','moon_lake','aurora','golden_coast','clouds','dusk_peaks'].includes(source)){tv.style.backgroundImage=`url('/assets/${source}.jpg')`;}
  else if(source==='automatic'){const hour=new Date().getHours();tv.style.backgroundImage=`url('/assets/${hour<7||hour>=19?'moon_lake':hour<10||hour>=17?'dunes':'aurora'}.jpg')`;}
  else if(source==='rotation'){tv.style.backgroundImage=`url('/assets/${['misty','dunes','moon_lake','golden_coast','clouds'][Math.floor(Date.now()/(settings.wallpaper_interval*60000))%5]}.jpg')`;}
  else if(source==='photos'||source==='photo_daily'){const photo=cinemaState.personalWallpaper||cinemaState.media.find(item=>!item.video)?.id;if(photo)tv.style.backgroundImage=`url('/phone/media/${photo}')`;}
  pauseScene(false);if(activeId){const target=[...tv.querySelectorAll('[data-app]')].find(node=>node.dataset.app===activeId);target?.focus({preventScroll:true});}
  if(document.activeElement===document.body&&!panel.open)dock.querySelector('button')?.focus({preventScroll:true});
  if(wasBrowsing)tv.classList.add('browsing');scheduleAmbient();const selected=cinemaState.scenes?.selected?cinemaState.scenes.selected+'/'+(cinemaState.scenes.revision||0):'';if(selected&&selected!==renderedSelection&&source==='aerial'){renderedSelection=selected;if(panel.open)panel.close();playScene('aerial');}
}
let weatherKey='', weatherSummary='', weatherUpdated=0, weatherRequest=0;
async function renderWeather(){
  const node=document.querySelector('.weather'),location=cinemaState.weather||{};
  const enabled=cinemaState.settings.weather_enabled&&!!location.name;
  node.hidden=!enabled;
  if(!enabled){weatherKey='';weatherSummary='';weatherRequest++;return;}
  const key=JSON.stringify(location);
  if(key!==weatherKey){weatherKey=key;weatherSummary=location.name+' · Updating weather…';weatherUpdated=0;}
  if(node.textContent!==weatherSummary)node.textContent=weatherSummary;
  if(Date.now()-weatherUpdated<3600000)return;
  weatherUpdated=Date.now();const request=++weatherRequest;
  try{const response=await fetch('/phone/api/weather');if(!response.ok)throw new Error();const result=await response.json();if(request!==weatherRequest)return;weatherSummary=result.summary;weatherUpdated=Date.now()-(result.available?0:3300000);}
  catch(e){if(request!==weatherRequest)return;weatherSummary=location.name+' · Weather unavailable';weatherUpdated=Date.now()-3300000;}
  if(request===weatherRequest&&node.textContent!==weatherSummary)node.textContent=weatherSummary;
}
setInterval(()=>{if(cinemaState&&!document.hidden)renderWeather();},60000);
function pick(title,key,choices){showPanel(title,choices.map(([name,value])=>[name,async()=>{if(await settingsChoice(key,value)){panel.close();scheduleAmbient();}}]));}
function settingSwitch(label,key){return [label,async event=>{
  const button=event.currentTarget;if(button.dataset.pending)return;
  const before=button.getAttribute('aria-checked')==='true';button.dataset.pending='true';button.setAttribute('aria-checked',String(!before));
  if(!await settingsChoice(key,!before))button.setAttribute('aria-checked',String(before));
  delete button.dataset.pending;
},{key,checked:!!cinemaState.settings[key]}];}
function appearanceGroup(group){
 const s=cinemaState.settings;
 const entries=group==='layout'?[['Two app rows','two_rows'],['Peek next row','peek_row'],['App names','show_names'],['Glass dock','home_plate']]:group==='clock'?[['Large clock','large_clock'],['Clock on right','clock_right'],['Spotify pill','music_strip'],['Weather','weather_enabled']]:[['High contrast focus','high_contrast'],['Reduced motion','reduced_motion'],['Navigation sounds','navigation_sounds']];
 const rows=entries.map(([label,key])=>settingSwitch(label,key));
 if(group==='motion')rows.push(['Wallpaper motion · '+s.wallpaper_motion,()=>pick('Wallpaper motion','wallpaper_motion',[['Still','off'],['After 10 seconds idle','idle'],['Always move on Home','always']])],['Effects · '+s.effects,()=>pick('Effects','effects',[['Full','full'],['Automatic','auto'],['Lite','lite']])]);
 showPanel(group==='layout'?'App layout':group==='clock'?'Clock & widgets':'Motion & focus',[['‹ Appearance',()=>settingsCategory('appearance')],...rows]);panel.classList.add('settings-page');
}
function wallpaperMenu(){pick('Wallpaper','wallpaper_selection',[['Misty coast','misty'],['Dunes','dunes'],['Moon lake','moon_lake'],['Aurora','aurora'],['Golden coast','golden_coast'],['Clouds','clouds'],['Aerial films · animated','aerial'],['My video loops','video'],['My photos · animated','photos'],['My photos · daily','photo_daily'],['Scene rotation','rotation'],['Time of day','automatic'],['Follow app colors','follow_app'],['Daily photograph · TV','daily']]);}
function screensaverMenu(){showPanel('Screensaver',[['‹ Scenes',()=>settingsCategory('scenes')],
  ['Preview now',()=>{panel.close();startAmbient();}],
  ['Mode · '+cinemaState.settings.screensaver_mode,()=>pick('Screensaver mode','screensaver_mode',[['Clock','clock'],['Animated photographs','photos'],['Aerial films','aerial'],['My videos','video'],['TV system screensaver','system']])],
  ['Start after · '+(cinemaState.settings.screensaver_minutes||'never'),()=>pick('Idle timer','screensaver_minutes',[['Never',0],['2 minutes',2],['5 minutes',5],['10 minutes',10],['20 minutes',20]])],
  settingSwitch('Clock overlay','screensaver_clock'),
  settingSwitch('Back starts screensaver','back_screensaver')
]);panel.classList.add('settings-page');}
function pairPhone(){
  showPanel('Connect your phone',[
    ['Open the phone editor',()=>{window.open('/phone/','_blank','noopener');}],
    ['About this preview',()=>notify('This QR opens the local editor on this computer. On TV, pairing uses its Wi-Fi address and an expiring private token.')]
  ]);
  const image=document.createElement('img');image.src='/qr.png';image.alt='QR code for the localhost editor';image.className='qr';actions.prepend(image);
  const hint=document.createElement('p');hint.className='pair-hint';hint.textContent='Local editor: localhost:8765/phone/ · TV pairing uses the TV’s network address.';actions.append(hint);
}
function appMenu(app){pauseScene(true);showPanel(app.name,[
  ['Move left',()=>cinemaEdit({op:'move',id:app.id,delta:-1})],['Move right',()=>cinemaEdit({op:'move',id:app.id,delta:1})],
  [cinemaState.dock.includes(app.id)?'Remove from dock':'Pin to dock',async()=>{let items=cinemaState.dock.filter(id=>id!==app.id);if(!cinemaState.dock.includes(app.id)){if(items.length>=5)items.pop();items.push(app.id);}await cinemaEdit({op:'dock',items});panel.close();}],
  ['Hide app',async()=>{await cinemaEdit({op:'hide',id:app.id,hidden:true});panel.close();}],
  ['Rename, folder or custom icon',()=>window.open('/phone/','_blank','noopener')],
  ['Open when TV starts',async()=>{await cinemaEdit({op:'boot',id:app.id});notify('Start target saved for this preview');}]
]);}
const settingsGlyphs={appearance:'<rect x="12" y="15" width="76" height="70" rx="10"/><circle cx="65" cy="35" r="7"/><path d="M15 72L37 47L55 65L66 55L87 77"/>',scenes:'<rect x="10" y="18" width="80" height="64" rx="12"/><path d="M42 35L65 50L42 65Z"/>',apps:'<rect x="15" y="15" width="28" height="28" rx="6"/><rect x="57" y="15" width="28" height="28" rx="6"/><rect x="15" y="57" width="28" height="28" rx="6"/><rect x="57" y="57" width="28" height="28" rx="6"/>',connections:'<rect x="30" y="8" width="40" height="84" rx="9"/><path d="M44 80H56M12 38Q50 8 88 38"/>',sound:'<path d="M12 40H30L50 23V77L30 60H12Z M65 32Q84 50 65 68M76 20Q103 50 76 80"/>',system:'<circle cx="50" cy="50" r="20"/><path d="M50 10V23M50 77V90M10 50H23M77 50H90M22 22L31 31M69 69L78 78M22 78L31 69M69 31L78 22"/>'};
function sectionPanel(title,entries){showPanel(title,[['‹ Settings',cinemaSettings],...entries]);panel.classList.add('settings-page');}
function cinemaSettings(){pauseScene(true);const sections=[['appearance','Appearance','Wallpaper, layout & clock'],['scenes','Scenes','Browse, favorites & playback'],['apps','Apps & inputs','Your dock, devices & hidden apps'],['connections','Connections','Phone remote, Wi-Fi & music'],['sound','Sound & picture','Volume, mute & brightness'],['system','System','Privacy, backups & updates']];showPanel('Settings',sections.map(([key,label])=>[label,()=>settingsCategory(key)]));panel.classList.add('settings-hub');const buttons=[...actions.querySelectorAll('button')];sections.forEach(([key,label,description],index)=>{const button=buttons[index];button.className='hub-card';const icon=document.createElementNS('http://www.w3.org/2000/svg','svg');icon.setAttribute('viewBox','0 0 100 100');icon.setAttribute('aria-hidden','true');icon.innerHTML=settingsGlyphs[key];const words=document.createElement('span'),title=document.createElement('strong'),subtitle=document.createElement('small');title.textContent=label;subtitle.textContent=description;words.append(title,subtitle);button.replaceChildren(icon,words);});requestAnimationFrame(()=>glideTo(document.activeElement,true));}
function settingsCategory(category){const s=cinemaState.settings;
 if(category==='appearance')sectionPanel('Appearance',[['Wallpaper',wallpaperMenu],['App layout',()=>appearanceGroup('layout')],['Clock & widgets',()=>appearanceGroup('clock')],['Motion & focus',()=>appearanceGroup('motion')],['Weather · choose on your phone',()=>window.open('/phone/#details','_blank','noopener')]]);
 else if(category==='scenes')sectionPanel('Scenes',[['Browse aerial films',()=>showSceneBrowser('all')],['Favorites',()=>showSceneBrowser('favorites')],['Screensaver & quality',screensaverMenu],settingSwitch('Shuffle','scene_shuffle'),settingSwitch('Play favorites only','scene_favorites_only')]);
 else if(category==='apps')sectionPanel('Apps & inputs',[['Arrange apps & connected devices',()=>window.open('/phone/#apps','_blank','noopener')],['Hidden apps',()=>sectionPanel('Hidden apps',cinemaState.apps.filter(app=>app.hidden).map(app=>['Show '+app.name,()=>cinemaEdit({op:'hide',id:app.id,hidden:false})]))]]);
 else if(category==='connections')sectionPanel('Connections',[['Connect phone · remote & editor',pairPhone],['Open phone remote',()=>window.open('/phone/#remote','_blank','noopener')],['Wi-Fi & network',()=>notify('The Android build opens TV network settings.')],['Music control access',()=>notify('Enable Android media-session access on the TV for supported players.')]]);
 else if(category==='sound')sectionPanel('Sound & picture',[['Volume −',()=>previewRemote('volume_down')],['Volume +',()=>previewRemote('volume_up')],['Mute / unmute',()=>previewRemote('mute')],['Picture & brightness',()=>notify('The Android build opens panel controls and TV picture settings.')]]);
 else sectionPanel('System',[['Privacy & PIN',()=>notify('PIN and pairing controls are available in the Android build.')],['Backup, weather & updates',()=>window.open('/phone/#details','_blank','noopener')],['Android TV settings',()=>notify('The Android build opens the TV settings application.')]]);
}
function showSceneBrowser(category='all',page=0){const preferences=cinemaState.scenes||{favorites:[],excluded:[],excludedCategories:[]};const matches=aerialFilms.filter(film=>category==='all'||film.category===category||category==='favorites'&&preferences.favorites.includes(film.id));const first=page*9,shown=matches.slice(first,first+9);const entries=[['‹ Scenes',()=>settingsCategory('scenes')],['Categories · '+category,()=>sectionPanel('Scene categories',['all','favorites','landscapes','cities','space','underwater'].map(filter=>[filter,()=>showSceneBrowser(filter)]))]];
 if(category!=='all'&&category!=='favorites')entries.push([(preferences.excludedCategories.includes(category)?'Include':'Exclude')+' this category',async()=>{await cinemaEdit({op:'scenePreference',kind:'category',id:category,enabled:!preferences.excludedCategories.includes(category)});showSceneBrowser(category,page);}]);
 showPanel('Aerial films',entries);panel.classList.add('scene-browser');const grid=document.createElement('div');grid.className='tv-film-grid';shown.forEach(film=>{const button=document.createElement('button');button.className='tv-film';button.setAttribute('aria-label','Open '+film.label);const image=document.createElement('img');image.src=film.thumbnail;image.alt='';image.addEventListener('error',()=>{const fallback=document.createElement('div');fallback.className='thumbnail-unavailable';fallback.textContent='Preview unavailable';image.replaceWith(fallback);});image.loading='lazy';const label=document.createElement('span');label.textContent=(preferences.favorites.includes(film.id)?'★ ':'')+film.label;const info=document.createElement('small');info.textContent=film.category+(preferences.excluded.includes(film.id)||preferences.excludedCategories.includes(film.category)?' · Excluded':'');button.append(image,label,info);button.addEventListener('click',()=>sceneDetails(film,category,page));grid.append(button);});actions.append(grid);if(!matches.length){const empty=document.createElement('p');empty.textContent='No favorites yet. Open a film and choose Favorite.';actions.append(empty);}for(const [name,target] of [['Previous page',page-1],['Next page',page+1]])if(target>=0&&target*9<matches.length){const button=document.createElement('button');button.textContent=name;button.addEventListener('click',()=>showSceneBrowser(category,target));actions.append(button);}}
function sceneDetails(film,category,page){const p=cinemaState.scenes;showPanel(film.label,[['‹ Back to scenes',()=>showSceneBrowser(category,page)],['Use this scene on Home',async()=>{if(await cinemaEdit({op:'sceneSelect',id:film.id})){panel.close();playScene('aerial');}}],[(p.favorites.includes(film.id)?'★ Remove favorite':'☆ Add favorite'),async()=>{await cinemaEdit({op:'scenePreference',kind:'favorite',id:film.id,enabled:!p.favorites.includes(film.id)});sceneDetails(film,category,page);}],[p.excluded.includes(film.id)?'Include in playback':'Exclude from playback',async()=>{await cinemaEdit({op:'scenePreference',kind:'exclude',id:film.id,enabled:!p.excluded.includes(film.id)});sceneDetails(film,category,page);}]]);const image=document.createElement('img');image.src=film.thumbnail;image.alt=film.label;image.addEventListener('error',()=>image.remove());image.className='tv-film-detail';actions.prepend(image);}
document.addEventListener('keydown',event=>{
 const button=event.target.closest?.('.setting-switch');
 if(!button||!['ArrowLeft','ArrowRight'].includes(event.key))return;
 event.preventDefault();event.stopImmediatePropagation();
 const enabled=event.key==='ArrowRight';if(!event.repeat&&(button.getAttribute('aria-checked')==='true')!==enabled)button.click();
},true);
document.querySelector('#settings').removeEventListener('click',settings);document.querySelector('#settings').addEventListener('click',cinemaSettings);
function continuousWallpaperMotion(){const s=cinemaState?.settings;return !!s&&['aerial','video'].includes(s.wallpaper_selection)&&s.wallpaper_motion!=='off'&&!s.reduced_motion;}
function scheduleAmbient(){clearTimeout(motionTimer);clearTimeout(saverTimer);if(!cinemaState||saver||document.hidden)return;const s=cinemaState.settings;if(s.wallpaper_motion!=='off'&&!s.reduced_motion)motionTimer=setTimeout(()=>{if(!panel.open&&(!tv.classList.contains('browsing')||continuousWallpaperMotion()))playScene(s.wallpaper_selection);},s.wallpaper_motion==='always'||continuousWallpaperMotion()?500:10000);if(s.screensaver_minutes>0&&s.screensaver_mode!=='system')saverTimer=setTimeout(()=>{if(!panel.open)startAmbient();},s.screensaver_minutes*60000);}
function scenePhotos(){const photos=cinemaState.media.filter(item=>!item.video).map(item=>'/phone/media/'+item.id);return photos.length?photos:['/assets/misty.jpg','/assets/dunes.jpg','/assets/moon_lake.jpg','/assets/golden_coast.jpg'];}
function playScene(source){
  clearTimeout(sceneTimer);sceneLayer.hidden=false;
  if(source==='aerial'||source==='video'){
    let files;
    if(source==='aerial'){const p=cinemaState.scenes||{favorites:[],excluded:[],excludedCategories:[]};const allowed=CinemaScenes.eligible(aerialFilms,p,cinemaState.settings);aerialQueue.configure(allowed.map(film=>film.id),cinemaState.settings.scene_shuffle!==false);if(p.selected&&p.selected+'/'+(p.revision||0)!==requestedFilm){requestedFilm=p.selected+'/'+(p.revision||0);aerialQueue.select(p.selected);}let id=aerialQueue.current()||aerialQueue.step();const film=allowed.find(film=>film.id===id);files=film?[{url:film.url,label:film.label}]:[];}
    else files=cinemaState.media.filter(item=>item.video).map(item=>({url:'/phone/media/'+item.id,label:'My video'}));
    if(!files.length){notify(source==='video'?'Add a video through the phone editor to use this scene.':(aerialFilms.length?'No enabled scenes · showing photographs':'Loading aerial catalogue…'));playScene('photos');return;}
    const item=files[source==='aerial'?0:sceneIndex%files.length];sceneImage.hidden=true;sceneVideo.hidden=false;
    if(lastMediaSource!==item.url){sceneVideo.src=item.url;lastMediaSource=item.url;}
    sceneVideo.play().catch(()=>{notify('Video unavailable in this browser · showing photographs');playScene('photos');});ambientCaption.textContent=saver?item.label:'';
  }else if(source==='photos'||saver){
    sceneVideo.pause();sceneVideo.hidden=true;sceneImage.hidden=false;sceneImage.src=scenePhotos()[sceneIndex%scenePhotos().length];sceneImage.style.animationPlayState='running';sceneImage.classList.remove('drifting');void sceneImage.offsetWidth;if(!cinemaState.settings.reduced_motion)sceneImage.classList.add('drifting');ambientCaption.textContent='';sceneTimer=setTimeout(()=>{sceneIndex++;playScene('photos');},18000);
  }
}
function pauseScene(keepFrame=false){clearTimeout(sceneTimer);sceneVideo.pause();sceneImage.style.animationPlayState='paused';if(!keepFrame){sceneLayer.hidden=true;sceneVideo.removeAttribute('src');sceneVideo.load();lastMediaSource='';}}
sceneVideo.addEventListener('ended',()=>{const source=saver?cinemaState.settings.screensaver_mode:cinemaState.settings.wallpaper_selection;if(source==='aerial')aerialQueue.step(1);else sceneIndex++;playScene(source);});
sceneVideo.addEventListener('error',()=>{if(!sceneVideo.hidden){sceneVideo.hidden=true;notify('Video source unavailable · showing photographs');playScene('photos');}});
function startAmbient(){if(!cinemaState)return;clearTimeout(motionTimer);clearTimeout(saverTimer);saver=true;tv.classList.add('ambient');const s=cinemaState.settings;ambientTime.hidden=!s.screensaver_clock&&s.screensaver_mode!=='clock';ambientTime.classList.toggle('big',s.screensaver_mode==='clock');playScene(s.screensaver_mode==='clock'||s.screensaver_mode==='system'?'photos':s.screensaver_mode);updateAmbientTime();}
function stopAmbient(){saver=false;tv.classList.remove('ambient');ambientCaption.textContent='';home();if(continuousWallpaperMotion())playScene(cinemaState.settings.wallpaper_selection);else pauseScene(true);scheduleAmbient();}
function updateAmbientTime(){ambientTime.textContent=new Intl.DateTimeFormat(undefined,{hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date());if(saver){ambientTime.style.transform=`translate(${-Math.random()*80}px,${Math.random()*80}px)`;}}
setInterval(updateAmbientTime,60000);
document.addEventListener('keydown',event=>{
 if(saver){event.preventDefault();event.stopImmediatePropagation();if(event.key==='ArrowLeft'||event.key==='ArrowRight'){if(cinemaState.settings.screensaver_mode==='aerial')aerialQueue.step(event.key==='ArrowLeft'?-1:1);else sceneIndex=Math.max(0,sceneIndex+(event.key==='ArrowLeft'?-1:1));playScene(cinemaState.settings.screensaver_mode);}else stopAmbient();return;}
 if(event.key==='Escape'&&!panel.open&&!tv.classList.contains('browsing')&&cinemaState?.settings.back_screensaver){event.preventDefault();event.stopImmediatePropagation();startAmbient();return;}
 if((cinemaState?.settings.wallpaper_motion!=='always'&&!continuousWallpaperMotion())||panel.open)pauseScene(true);scheduleAmbient();
 if(cinemaState?.settings.navigation_sounds&&event.key.startsWith('Arrow')){new Audio('/sounds/nav_move.wav').play().catch(()=>{});}
},true);
tv.addEventListener('pointerdown',event=>{if(saver){event.preventDefault();event.stopImmediatePropagation();stopAmbient();}else{if(!continuousWallpaperMotion()||panel.open)pauseScene(true);scheduleAmbient();}},true);
tv.addEventListener('focusin',event=>{if(cinemaState?.settings.wallpaper_selection==='follow_app'){const colors={nuvio:'#283046',spotify:'#173b32',smarttube:'#44282b',inputs:'#273b43',playstore:'#344636',netflix:'#49282c'};const id=event.target.dataset.app;if(id)tv.style.backgroundImage=`radial-gradient(ellipse at center,${colors[brandFor(id)]},#10171c)`;}});
panel.addEventListener('close',()=>{if(continuousWallpaperMotion())playScene(cinemaState.settings.wallpaper_selection);scheduleAmbient();});
document.addEventListener('visibilitychange',()=>{if(document.hidden)pauseScene(false);else scheduleAmbient();});
fetch('/aerials.json').then(response=>response.json()).then(data=>{aerialFilms=data.videos;}).catch(()=>{});
syncCinema().then(()=>{if(new URLSearchParams(location.search).has('screensaver'))startAmbient();});setInterval(()=>{if(!document.hidden)syncCinema();},250);

document.querySelector('#expand').addEventListener('click',()=>viewport.requestFullscreen().catch(()=>notify('Open this page in a full browser window for a larger preview.')));

// Port of the approved GlideFocus: one moving surface per group, never one highlight per tile.
const glideGroups = new Map();
function glideTo(target, instant = false) {
  const row = target?.closest('[data-row], #panel-actions');
  for (const [group, surface] of glideGroups) if (group !== row) surface.style.opacity = '0';
  if (!row || target.tagName !== 'BUTTON') return;
  let surface = glideGroups.get(row);
  if (!surface || !surface.isConnected) {
    surface = document.createElement('div'); surface.className = 'glide-selection'; surface.setAttribute('aria-hidden','true'); row.prepend(surface); glideGroups.set(row,surface); instant = true;
  }
  const bounds = row.getBoundingClientRect(), tileBounds = target.getBoundingClientRect();
  const scale = bounds.width / row.offsetWidth || 1;
  const poster = row.classList.contains('posters');
  const zoom = poster && !cinemaState?.settings.reduced_motion ? 1.045 : 1;
  const x = poster ? target.offsetLeft + target.offsetWidth*(1-zoom)/2-2 : (tileBounds.left - bounds.left) / scale + row.scrollLeft - row.clientLeft;
  const y = poster ? target.offsetTop + target.offsetHeight*(1-zoom)/2-2 : (tileBounds.top - bounds.top) / scale + row.scrollTop - row.clientTop;
  surface.style.transitionDuration = instant || cinemaState?.settings.reduced_motion ? '0ms' : '220ms';
  surface.style.width = `${target.offsetWidth*zoom+(poster?4:0)}px`; surface.style.height = `${target.offsetHeight*zoom+(poster?4:0)}px`;
  surface.style.transform = `translate(${x}px,${y}px)`; surface.style.opacity = '1';
}
document.addEventListener('focusin',event=>glideTo(event.target));
new ResizeObserver(()=>glideTo(document.activeElement,true)).observe(viewport);
const arrivalCover=document.createElement('div');arrivalCover.className='arrival-cover';tv.insertBefore(arrivalCover,tv.querySelector('.shade'));
let arrivalTimer;
async function replayArrival() {
  clearTimeout(arrivalTimer);tv.classList.add('arriving');
  await document.fonts.ready;
  const match=getComputedStyle(tv).backgroundImage.match(/url\(["']?([^"')]+)["']?\)/);
  if(match){const image=new Image();image.src=match[1];try{await image.decode();}catch{}}
  requestAnimationFrame(()=>requestAnimationFrame(()=>{
    arrivalTimer=setTimeout(()=>{tv.classList.remove('arriving');glideTo(document.activeElement,true);},cinemaState?.settings.reduced_motion?0:350);
  }));
}
document.querySelector('#replay').addEventListener('click',replayArrival);
replayArrival();

let demoVolume=50;
function previewRemote(key){
 if(['volume_up','volume_down','mute'].includes(key)){demoVolume=key==='mute'?(demoVolume?0:50):Math.max(0,Math.min(100,demoVolume+(key==='volume_up'?5:-5)));notify('Preview volume · '+demoVolume+'%');return;}
 if(['play_pause','previous','next'].includes(key)){if(saver&&['previous','next'].includes(key)){if(cinemaState.settings.screensaver_mode==='aerial')aerialQueue.step(key==='previous'?-1:1);else sceneIndex=Math.max(0,sceneIndex+(key==='previous'?-1:1));playScene(cinemaState.settings.screensaver_mode);}else document.querySelector(key==='play_pause'?'#play':key==='previous'?'[aria-label="Previous sample track"]':'[aria-label="Next sample track"]')?.click();return;}
 if(key==='home'){if(saver)stopAmbient();if(panel.open)panel.close();home();return;}
 if(key==='settings'){if(saver)stopAmbient();cinemaSettings();return;}
 if(key==='menu'){const id=document.activeElement?.dataset.app;const app=cinemaState.apps.find(app=>app.id===id);if(app)appMenu(app);else cinemaSettings();return;}
 if(key==='ok'){document.activeElement?.click();return;}
 if(key==='back'&&panel.open){goPanelBack();return;}
 const names={up:'ArrowUp',down:'ArrowDown',left:'ArrowLeft',right:'ArrowRight',back:'Escape'};if(names[key])document.activeElement?.dispatchEvent(new KeyboardEvent('keydown',{key:names[key],bubbles:true,cancelable:true}));
}

function goPanelBack(){const back=panel.querySelector('.back-row');if(back)back.click();else panel.close();}
panel.addEventListener('cancel',event=>{if(panel.querySelector('.back-row')){event.preventDefault();goPanelBack();}});
