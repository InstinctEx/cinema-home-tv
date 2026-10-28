const tv = document.querySelector('#tv');
const viewport = document.querySelector('#viewport');
new ResizeObserver(() => { const scale = Math.min(viewport.clientWidth / 1280, viewport.clientHeight / 720); tv.style.transform = `scale(${scale})`; tv.style.left = `${(viewport.clientWidth - 1280 * scale) / 2}px`; tv.style.top = `${(viewport.clientHeight - 720 * scale) / 2}px`; }).observe(viewport);
function updateClock() {
  document.querySelector('#clock').textContent = new Intl.DateTimeFormat(undefined, {hour: '2-digit', minute: '2-digit', hour12: false}).format(new Date());
}
updateClock();
setInterval(updateClock, 1000);

// shortcut: browser SVGs approximate BrandMarkDrawable, compare native rendering on the TV before shipping visual changes.
const marks = {
  nuvio: '<defs><linearGradient id="nuvio" x2="1" y2="1"><stop stop-color="#50d4e6"/><stop offset="1" stop-color="#b52be2"/></linearGradient></defs><path d="M22 10Q12 4 12 17V83Q12 96 23 89L84 57Q97 50 84 43Z" fill="url(#nuvio)"/><path d="M33 30V71L70 50Z" fill="#152032"/><path d="M42 40V61L60 50Z" fill="white"/>',
  smarttube: '<rect x="4" y="18" width="92" height="64" rx="13" fill="#e82a2d"/><text x="50" y="73" text-anchor="middle" fill="white" font-family="Outfit" font-weight="600" font-size="66">S</text>',
  spotify: '<circle cx="50" cy="50" r="43" fill="#1ed760"/><g fill="none" stroke="#07150d" stroke-linecap="round"><path d="M24 37C41 30 63 34 76 43" stroke-width="7"/><path d="M27 51C41 44 63 47 73 56" stroke-width="6"/><path d="M30 65C41 58 63 60 70 69" stroke-width="5"/></g>',
  inputs: '<g fill="none" stroke="#f4f7fa" stroke-width="5" stroke-linejoin="round"><path d="M12 25H88V60L70 78H30L12 60Z"/><path d="M29 45H71"/></g>',
  playstore: '<path d="M16 12L59 50L16 88Z" fill="#52c774"/><path d="M16 12L80 42L59 50Z" fill="#44b4f1"/><path d="M59 50L80 42L94 50L80 58Z" fill="#f4c44e"/><path d="M16 88L59 50L80 58Z" fill="#e97175"/>',
  netflix: '<path d="M24 8H42V92H24ZM59 8H77V92H59Z" fill="#b20710"/><path d="M24 8H44L77 92H57Z" fill="#e50914"/>'
};
// Match the dock's drawMonochrome paths, including transparent cutouts, rather than recoloring brand logos.
const themedMarks = {
  nuvio: '<path fill="#eaeae2" fill-rule="evenodd" d="M22 10Q12 4 12 17V83Q12 96 23 89L84 57Q97 50 84 43ZM35 30V71L70 50Z"/>',
  smarttube: '<path fill="#eaeae2" fill-rule="evenodd" d="M21 18H79Q96 18 96 35V65Q96 82 79 82H21Q4 82 4 65V35Q4 18 21 18ZM41 34V67L68 50Z"/>',
  spotify: '<defs><mask id="spotify-cutouts"><circle cx="50" cy="50" r="43" fill="white"/><g fill="none" stroke="black" stroke-linecap="round"><path d="M24 37C41 30 63 34 76 43" stroke-width="7"/><path d="M27 51C41 44 63 47 73 56" stroke-width="6"/><path d="M30 65C41 58 63 60 70 69" stroke-width="5"/></g></mask></defs><circle cx="50" cy="50" r="43" fill="#eaeae2" mask="url(#spotify-cutouts)"/>',
  inputs: '<g fill="none" stroke="#eaeae2" stroke-width="4" stroke-linejoin="round"><path d="M12 25H88V60L70 78H30L12 60Z"/><path d="M29 45H71"/></g>',
  playstore: '<defs><mask id="playstore-cutouts"><path d="M18 10L92 50L18 90Z" fill="white"/><path d="M18 10L64 50L18 90M18 50H92" fill="none" stroke="black" stroke-width="2"/></mask></defs><path d="M18 10L92 50L18 90Z" fill="#eaeae2" mask="url(#playstore-cutouts)"/>'
};
function tile(label, brand, monochrome = false) {
  const button = document.createElement('button');
  button.className = 'tile';
  button.innerHTML = `<svg viewBox="0 0 100 100" aria-hidden="true">${monochrome ? themedMarks[brand] : marks[brand]}</svg><span></span>`;
  button.querySelector('span').textContent = label;
  button.addEventListener('click', () => notify(`${label} would open on your TV. This is a local design preview.`));
  return button;
}
const dock = document.querySelector('#dock');
for (const [label, brand] of [['Nuvio','nuvio'], ['SmartTube beta','smarttube'], ['Spotify','spotify'], ['Inputs','inputs'], ['Play Store','playstore']]) dock.append(tile(label, brand, true));
document.querySelector('#other-apps').append(tile('Netflix', 'netflix'));
let noticeTimer;
function notify(message) {
  const notice = document.querySelector('#notice');
  notice.textContent = message;
  notice.classList.add('visible');
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => notice.classList.remove('visible'), 4000);
}
document.querySelectorAll('[data-message]').forEach(button => button.addEventListener('click', () => notify(button.dataset.message)));
document.querySelector('#samples').addEventListener('change', event => {
  document.querySelectorAll('.sample').forEach(element => { element.hidden = !event.target.checked; });
  home();
});
document.querySelector('#play').addEventListener('click', event => {
  const button = event.currentTarget;
  const playing = button.getAttribute('aria-pressed') !== 'true';
  button.setAttribute('aria-pressed', String(playing));
  button.setAttribute('aria-label', playing ? 'Pause sample playback' : 'Play sample playback');
  button.textContent = playing ? 'Ⅱ' : '▶';
});
const panel = document.querySelector('#panel');
const actions = document.querySelector('#panel-actions');
function showPanel(title, entries) {
  panel.classList.remove('settings-hub','scene-browser','settings-page');
  document.querySelector('#panel-title').textContent = title;
  actions.replaceChildren();
  for (const [label, action, toggle] of entries) {
    const button = document.createElement('button');
    button.textContent = label;
    button.setAttribute('aria-label',label);
    if(label.startsWith('‹'))button.classList.add('back-row');
    if(toggle){
      button.classList.add('setting-switch');button.dataset.setting=toggle.key;
      button.setAttribute('role','switch');button.setAttribute('aria-label',label);
      button.setAttribute('aria-checked',String(toggle.checked));
      const track=document.createElement('span');track.className='switch-track';track.setAttribute('aria-hidden','true');button.append(track);
    }
    button.addEventListener('click', action);
    actions.append(button);
  }
  if (!panel.open) panel.showModal();
  (actions.querySelector('button:not(.back-row)')||actions.querySelector('button')).focus();
  requestAnimationFrame(()=>{if(typeof glideTo==='function')glideTo(document.activeElement,true);});
}
function settings() {
  showPanel('Settings', [
    ['Wallpaper', () => showPanel('Wallpaper', [['Misty coast','misty'], ['Dunes','dunes'], ['Moon Lake','moon_lake']].map(([label, key]) => [label, () => {
      tv.style.backgroundImage = `url('/assets/${key}.jpg')`;
      panel.close();
      home();
    }]))],
    ['Choose dock apps', () => notify('On TV, select up to five apps; reselect an app to move it last.')],
    ['Spotify control access', () => notify('On TV, this opens Android notification listener permissions.')],
    ['TV settings', () => notify('On TV, this opens Android settings.')]
  ]);
}
document.querySelector('#settings').addEventListener('click', settings);
document.querySelector('#close-panel').addEventListener('click', () => panel.close());
panel.addEventListener('close', () => document.querySelector('#settings').focus());
function home() {
  tv.classList.remove('browsing');
  dock.querySelector('button').focus({preventScroll:true});
}
tv.addEventListener('focusin', event => {
  const row = event.target.closest('[data-row]');
  if (row) tv.classList.toggle('browsing', Number(row.dataset.row) > 2);
});
document.addEventListener('keydown', event => {
  if (!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Escape'].includes(event.key)) return;
  if (!tv.contains(document.activeElement)) return;
  if (panel.open) {
    if (event.key === 'Escape') return; // Native dialog handles Escape and restores focus.
    event.preventDefault();
    const buttons = [...panel.querySelectorAll('button')];
    const grid=document.activeElement.closest('.tv-film-grid');
    const columns=grid?getComputedStyle(grid).gridTemplateColumns.split(' ').length:panel.classList.contains('settings-hub')?2:1;
    const step = ['ArrowUp','ArrowDown'].includes(event.key) ? columns : 1;
    const delta = ['ArrowLeft','ArrowUp'].includes(event.key) ? -step : step;
    buttons[Math.max(0, Math.min(buttons.length - 1, buttons.indexOf(document.activeElement) + delta))].focus();
    return;
  }
  event.preventDefault();
  if (event.key === 'Escape') { home(); return; }
  const row = document.activeElement.closest('[data-row]');
  if (!row) return;
  const buttons = [...row.querySelectorAll('button')];
  const index = buttons.indexOf(document.activeElement);
  if (['ArrowLeft','ArrowRight'].includes(event.key)) {
    buttons[Math.max(0, Math.min(buttons.length - 1, index + (event.key === 'ArrowRight' ? 1 : -1)))].focus({preventScroll:true});
  } else {
    const rows = [...tv.querySelectorAll('[data-row]')].filter(item => !item.closest('[hidden]'));
    const target = rows[rows.indexOf(row) + (event.key === 'ArrowDown' ? 1 : -1)];
    if (target) {
      const targets = [...target.querySelectorAll('button')];
      targets[Math.min(index, targets.length - 1)].focus({preventScroll:true});
    }
  }
});
home();
