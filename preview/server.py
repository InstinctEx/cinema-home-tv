"""Loopback-only Cinema Home simulator and the same paired editor shipped in Android."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
import copy
import json
import mimetypes
import os
import threading
import uuid
import time
import math

ROOT = Path(__file__).resolve().parent
APP = ROOT.parent / 'launcher/app/src/main'
DATA = ROOT / '.local-data'
LOCK = threading.RLock()
FILES = {'/': ROOT/'index.html', '/style.css': ROOT/'style.css', '/app.js': ROOT/'app.js',
         '/features.js': ROOT/'features.js', '/sounds/nav_move.wav': APP/'res/raw/nav_move.wav', '/assets/outfit.ttf': APP/'res/font/outfit.ttf',
         '/aerials.json': APP/'assets/aerials.json', '/qr.png': ROOT/'qr.png',
         **{f'/assets/{name}.jpg': APP/f'assets/wallpapers/{name}.jpg' for name in ('misty','dunes','moon_lake','aurora','golden_coast','clouds','dusk_peaks')},
         **{f'/phone/{name}': APP/f'assets/phone/{name}' for name in ('index.html','phone.js','phone.css','icons.svg','scene-policy.js')}}
FILES.update({f'/phone/wallpapers/{name}.jpg':APP/f'assets/wallpapers/{name}.jpg' for name in ('misty','dunes','moon_lake','aurora','golden_coast','clouds','dusk_peaks')})
FILES['/phone/outfit.ttf'] = APP/'res/font/outfit.ttf'
FILES['/phone/'] = APP/'assets/phone/index.html'
DEFAULTS = dict(wallpaper_selection='misty',wallpaper_dim=10,wallpaper_interval=60,glass_style='frosted',media_app='nuvio',cinematic_artwork=True,shelf_enabled=True,navigation_sounds=False,idle_clock=True,music_strip=True,weather_enabled=False,reduced_motion=False,wallpaper_motion='idle',screensaver_mode='system',screensaver_minutes=5,screensaver_clock=True,back_screensaver=False,clock_right=False,large_clock=False,two_rows=False,peek_row=False,show_names=True,home_plate=True,high_contrast=False,effects='full',aerial_quality='1080p',scene_shuffle=True,scene_favorites_only=False)
CHOICES = dict(wallpaper_selection=['misty','dunes','moon_lake','aurora','golden_coast','clouds','dusk_peaks','automatic','rotation','photos','aerial','video','daily','photo_daily','follow_app'],wallpaper_motion=['off','idle','always'],screensaver_mode=['clock','photos','aerial','video','system'],effects=['auto','full','lite'],aerial_quality=['auto','1080p','4k','dolby_vision'],glass_style=['soft','frosted','solid'],media_app=['nuvio','stremio'])
APPS = [('com.nuvio.tv','Nuvio'),('org.smarttube.beta','SmartTube beta'),('com.spotify.tv.android','Spotify'),('com.gathin.tvhome.inputs','Inputs'),('com.android.vending','Play Store'),('com.netflix.ninja','Netflix'),('input:demo-hdmi-1','PlayStation · HDMI 1'),('input:demo-hdmi-2','Apple TV · HDMI 2')]
CATALOGUE = json.loads((APP/'assets/aerials.json').read_text())['videos']
REMOTE_KEYS = {'up','down','left','right','ok','back','home','menu','settings','volume_up','volume_down','mute','play_pause','previous','next'}
INITIAL = dict(name='Cinema Home',version='4.0',demo=True,settings=DEFAULTS,apps=[dict(id=key,name=name,hidden=False,input=key.startswith('input:'),icon='') for key,name in APPS],dock=[key for key,_ in APPS[:5]],folders={},media=[],storage=0,locked=False,pinSet=False,weather={},bootTarget='',diagnostics='Browser simulation · TV frame timings and hardware controls require the TV')
INITIAL.update(scenes=dict(favorites=[],excluded=[],excludedCategories=[],selected=''),remote=dict(homeVisible=True,accessibility=False,mediaAccess=True))
STATE = copy.deepcopy(INITIAL)
if (DATA/'state.json').is_file():
    try:
        STATE = json.loads((DATA/'state.json').read_text())
        STATE['settings'] = {**DEFAULTS, **STATE['settings']}
    except (ValueError, KeyError):
        STATE = copy.deepcopy(INITIAL)

STATE.setdefault('scenes',copy.deepcopy(INITIAL['scenes']))
STATE.setdefault('remote',copy.deepcopy(INITIAL['remote']))


def save():
    DATA.mkdir(exist_ok=True)
    temporary = DATA/'state.tmp'
    temporary.write_text(json.dumps(STATE))
    os.replace(temporary, DATA/'state.json')


def media_file(key):
    import re
    if not re.fullmatch(r'[a-f0-9-]{36}\.(jpg|mp4)', key):
        raise ValueError('Invalid media ID')
    return DATA/key


def validate_settings(values):
    if not isinstance(values,dict): raise ValueError('Invalid settings')
    for key,value in values.items():
        if key not in DEFAULTS: raise ValueError('Unknown setting')
        if type(value) is not type(DEFAULTS[key]): raise ValueError('Invalid setting type')
        if key in CHOICES and value not in CHOICES[key]: raise ValueError('Unknown setting value')
        if key == 'wallpaper_dim' and not 0 <= value <= 80: raise ValueError('Invalid dimming')
        if key == 'wallpaper_interval' and value not in (30,60,180): raise ValueError('Invalid rotation interval')
        if key == 'screensaver_minutes' and value not in (0,2,5,10,20): raise ValueError('Invalid idle delay')
    return values


def edit(command):
    op = command.get('op'); key = command.get('id')
    app = next((app for app in STATE['apps'] if app['id']==key),None)
    if op == 'remote':
        if command.get('key') not in REMOTE_KEYS:raise ValueError('Unknown remote button')
        STATE['remoteEvent']={'seq':STATE.get('remoteEvent',{}).get('seq',0)+1,'key':command['key']}
        STATE['remoteEvents']=(STATE.get('remoteEvents',[])+[STATE['remoteEvent']])[-64:]
        return
    if op == 'scenePreference':
        kind=command['kind'];enabled=command['enabled'];known={s['id'] for s in CATALOGUE};categories={s['category'] for s in CATALOGUE}
        if type(enabled) is not bool or kind not in ('favorite','exclude','category') or key not in (categories if kind=='category' else known):raise ValueError('Invalid scene preference')
        field={'favorite':'favorites','exclude':'excluded','category':'excludedCategories'}[kind];values=set(STATE['scenes'][field]);values.add(key) if enabled else values.discard(key);STATE['scenes'][field]=sorted(values)
    elif op == 'sceneSelect':
        eligible=[s['id'] for s in CATALOGUE if s['id'] not in STATE['scenes']['excluded'] and s['category'] not in STATE['scenes']['excludedCategories'] and (not STATE['settings']['scene_favorites_only'] or s['id'] in STATE['scenes']['favorites'])]
        if key not in eligible:raise ValueError('Include this scene in playback first')
        STATE['scenes']['selected']=key;STATE['scenes']['revision']=STATE['scenes'].get('revision',0)+1;STATE['settings']['wallpaper_selection']='aerial'
    elif op == 'settings': STATE['settings'].update(validate_settings(command['settings']))
    elif op == 'dock':
        items=command['items']
        if not isinstance(items,list) or len(items)>5 or len(set(items))!=len(items) or any(not any(a['id']==i and not a['hidden'] for a in STATE['apps']) for i in items): raise ValueError('Choose up to five visible apps')
        STATE['dock']=items
    elif op == 'weather':
        lat=float(command['latitude']);lon=float(command['longitude']);name=command['name'].strip()
        if not name or len(name)>40 or not -90<=lat<=90 or not -180<=lon<=180:raise ValueError('Invalid weather location')
        STATE['weather']={k:command[k] for k in ('name','latitude','longitude')};STATE['settings']['weather_enabled']=True
    elif op == 'release':
        url=command['url']
        if url and not url.startswith('https://'):raise ValueError('Release URL must use HTTPS')
        STATE['releaseManifest']=url
    elif op in ('wallpaperPhoto','deleteMedia'):
        item=next((m for m in STATE['media'] if m['id']==command['media']),None)
        if not item:raise ValueError('Media unavailable')
        if op=='wallpaperPhoto':
            if item['video']:raise ValueError('Choose a photo')
            STATE['personalWallpaper']=item['id'];STATE['settings']['wallpaper_selection']='photos'
        else:
            media_file(item['id']).unlink(missing_ok=True);STATE['media'].remove(item);STATE['storage']-=item['bytes']
            for a in STATE['apps']:
                if a['icon']==item['id']:a['icon']=''
            if STATE.get('personalWallpaper')==item['id']:STATE.pop('personalWallpaper')
    elif op=='boot' and not key:STATE['bootTarget']=''
    elif app:
        if op in ('rename','folder'):
            name=command['name'].strip()
            if len(name)>40 or (op=='rename' and not name) or any(ord(c)<32 for c in name):raise ValueError('Use a name of 1–40 characters')
            if op=='rename':app['name']=name
            else:
                STATE['folders']={n:[i for i in values if i!=key] for n,values in STATE['folders'].items()}
                STATE['folders']={n:values for n,values in STATE['folders'].items() if values}
                if name:STATE['folders'].setdefault(name,[]).append(key)
        elif op=='hide':
            if type(command['hidden']) is not bool:raise ValueError('Invalid visibility')
            app['hidden']=command['hidden']
            if app['hidden']:STATE['dock']=[i for i in STATE['dock'] if i!=key]
        elif op=='boot':STATE['bootTarget']=key
        elif op=='device':
            if not app['input'] or command['device'] not in ('inputs','appletv','playstation','switch','xbox','computer','speaker'):raise ValueError('Unknown device icon')
            app['device']=command['device']
        elif op=='icon':
            icon=command['media']
            if icon and not any(m['id']==icon and not m['video'] for m in STATE['media']):raise ValueError('Choose an uploaded photo')
            app['icon']=icon
        elif op=='move':
            group=STATE['dock'] if key in STATE['dock'] else [a['id'] for a in STATE['apps']]
            old=group.index(key);new=max(0,min(len(group)-1,old+int(command['delta'])));group.insert(new,group.pop(old))
            if key not in STATE['dock']:STATE['apps'].sort(key=lambda a:group.index(a['id']))
        else:raise ValueError('Unknown action')
    else:raise ValueError('Unknown action or app')
    save()


WEATHER_CACHE = {}

def current_weather():
    from urllib.request import urlopen
    from urllib.parse import urlencode
    with LOCK:
        location = dict(STATE.get('weather', {}))
        enabled = STATE['settings']['weather_enabled']
    name = location.get('name', '')
    if not enabled or not name:
        return {'summary': '', 'available': False}
    key = tuple(location.get(k, '') for k in ('name', 'latitude', 'longitude'))
    cached = WEATHER_CACHE.get(key)
    if cached and time.time()-cached[0] < 3600:
        return cached[1]
    try:
        params = urlencode({'latitude':float(location['latitude']), 'longitude':float(location['longitude']),
                            'current':'temperature_2m,weather_code', 'timezone':'auto'})
        with urlopen('https://api.open-meteo.com/v1/forecast?'+params, timeout=8) as response:
            raw = response.read(100001)
        if len(raw)>100000: raise ValueError('Forecast too large')
        current = json.loads(raw)['current']
        temperature = float(current['temperature_2m']); code = int(current['weather_code'])
        if not math.isfinite(temperature) or code < 0 or code > 99: raise ValueError('Invalid forecast')
        condition = next(label for limit,label in [(0,'Clear'),(3,'Cloudy'),(48,'Fog'),(67,'Rain'),(77,'Snow'),(82,'Showers'),(86,'Snow showers'),(99,'Thunderstorms')] if code<=limit)
        result = {'summary':f'{name} · {math.floor(temperature+0.5)}° · {condition}', 'available':True}
        WEATHER_CACHE.clear(); WEATHER_CACHE[key] = (time.time(), result)
        return result
    except Exception:
        if cached and time.time()-cached[0] < 21600: return cached[1]
        return {'summary':name+' · Weather unavailable', 'available':False}


class PreviewHandler(BaseHTTPRequestHandler):
    def json(self,value,status=200):
        data=json.dumps(value).encode();self.send_response(status);self.headers_for('application/json',len(data));self.end_headers();self.wfile.write(data)
    def headers_for(self,kind,size):
        self.send_header('Content-Type',kind);self.send_header('Content-Length',str(size));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.send_header('Referrer-Policy','no-referrer')
    def do_GET(self):
        route=urlsplit(self.path).path
        if route.startswith('/phone/api/cities/'):
            from urllib.parse import unquote, quote
            from urllib.request import urlopen
            city=unquote(route.removeprefix('/phone/api/cities/')).strip()
            if not city or len(city)>40:self.json({'error':'Use a city name of 1–40 characters'},400);return
            try:
                with urlopen('https://geocoding-api.open-meteo.com/v1/search?name='+quote(city)+'&count=8&language=en&format=json',timeout=8) as response:
                    data=response.read(150001)
                    if len(data)>150000:raise ValueError('Response too large')
                    self.json(json.loads(data))
            except Exception:self.json({'error':'City search unavailable. You can enter coordinates below.'},502)
            return
        if route=='/phone/api/weather':self.json(current_weather());return
        if route=='/phone/api/scenes':self.json({'videos':CATALOGUE});return
        if route=='/phone/api/state':
            with LOCK:self.json(STATE)
            return
        if route.startswith('/phone/media/'):
            try:path=media_file(route.removeprefix('/phone/media/'))
            except ValueError:self.send_error(404);return
        else:path=FILES.get(route)
        if path is None or not path.is_file():self.send_error(404);return
        size=path.stat().st_size;start=0;end=size-1
        if self.headers.get('Range'):
            import re
            match=re.fullmatch(r'bytes=(\d+)-(\d*)',self.headers['Range'])
            if not match:self.send_error(416);return
            start=int(match[1]);end=min(end,int(match[2])) if match[2] else end
            if start>end:self.send_error(416);return
        self.send_response(206 if self.headers.get('Range') else 200);self.headers_for(mimetypes.guess_type(path)[0] or 'application/octet-stream',end-start+1);self.send_header('Accept-Ranges','bytes')
        if self.headers.get('Range'):self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
        self.end_headers()
        with path.open('rb') as data:
            data.seek(start);remaining=end-start+1
            while remaining>0:
                chunk=data.read(min(65536,remaining))
                if not chunk:break
                self.wfile.write(chunk);remaining-=len(chunk)
    def do_POST(self):
        route=urlsplit(self.path).path
        origin=self.headers.get('Origin')
        if origin and urlsplit(origin).netloc!=self.headers.get('Host'):self.json({'error':'Different origin rejected'},403);return
        if self.headers.get('Transfer-Encoding'):self.json({'error':'Chunked uploads unsupported'},400);return
        try:
            length=int(self.headers.get('Content-Length','-1'))
            with LOCK:
                if route=='/phone/api/edit':
                    if not 0<length<=65536:raise ValueError('Request too large')
                    edit(json.loads(self.rfile.read(length)));self.json({'saved':True})
                elif route in ('/phone/api/upload/photo','/phone/api/upload/video'):
                    video=route.endswith('video');limit=(150 if video else 12)*1024*1024
                    if not 0<length<=limit or STATE['storage']+length>400*1024*1024:raise ValueError('Upload or storage limit exceeded')
                    DATA.mkdir(exist_ok=True);key=str(uuid.uuid4())+('.mp4' if video else '.jpg');path=media_file(key);remaining=length
                    try:
                        with path.open('wb') as output:
                            while remaining:
                                chunk=self.rfile.read(min(65536,remaining))
                                if not chunk:raise ValueError('Incomplete upload')
                                output.write(chunk);remaining-=len(chunk)
                        with path.open('rb') as content:magic=content.read(16)
                        if video and magic[4:8]!=b'ftyp':raise ValueError('Browser demo accepts MP4/MOV video')
                        if not video and not (magic.startswith(b'\xff\xd8') or magic.startswith(b'\x89PNG\r\n\x1a\n') or magic.startswith(b'RIFF') and magic[8:12]==b'WEBP'):raise ValueError('Choose a JPEG, PNG or WebP image')
                        STATE['media'].append(dict(id=key,video=video,bytes=length));STATE['storage']+=length;save();self.json({'id':key})
                    except Exception:path.unlink(missing_ok=True);raise
                else:self.json({'error':'Unknown endpoint'},404)
        except (ValueError,KeyError,TypeError) as failure:self.json({'error':str(failure)},400)


if __name__=='__main__':
    print('Cinema Home: http://localhost:8765 · editor: http://localhost:8765/phone/',flush=True)
    ThreadingHTTPServer(('127.0.0.1',8765),PreviewHandler).serve_forever()
