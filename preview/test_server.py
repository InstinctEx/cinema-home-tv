import copy
import json
import tempfile
import threading
import unittest
from unittest.mock import patch, MagicMock
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import urlopen, Request
from http.server import ThreadingHTTPServer
import server as preview

class PreviewTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=ThreadingHTTPServer(('127.0.0.1',0),preview.PreviewHandler)
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        cls.url=f'http://127.0.0.1:{cls.server.server_port}'
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.thread.join()
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory();self.previous_data=preview.DATA;self.previous_state=preview.STATE
        preview.DATA=Path(self.directory.name);preview.STATE=copy.deepcopy(preview.INITIAL)
    def tearDown(self):
        preview.DATA=self.previous_data;preview.STATE=self.previous_state;self.directory.cleanup()
    def post(self,command,origin=None):
        headers={'Content-Type':'application/json'}
        if origin:headers['Origin']=origin
        return urlopen(Request(self.url+'/phone/api/edit',data=json.dumps(command).encode(),headers=headers))
    def test_weather_fetches_selected_city_and_caches_forecast(self):
        preview.WEATHER_CACHE.clear()
        with self.post({'op':'weather','name':'Lamia','latitude':'38.9','longitude':'22.43'}):pass
        response=MagicMock();response.__enter__.return_value.read.return_value=json.dumps({'current':{'temperature_2m':21.4,'weather_code':0}}).encode()
        with patch('urllib.request.urlopen',return_value=response) as forecast:
            self.assertEqual(preview.current_weather(),{'summary':'Lamia · 21° · Clear','available':True})
            self.assertEqual(preview.current_weather()['available'],True)
            forecast.assert_called_once()
            self.assertIn('latitude=38.9',forecast.call_args.args[0])
        preview.STATE['settings']['weather_enabled']=False
        self.assertEqual(preview.current_weather()['summary'],'')
        preview.WEATHER_CACHE.clear()

    def test_weather_failure_keeps_new_city_without_old_forecast(self):
        preview.WEATHER_CACHE.clear()
        preview.WEATHER_CACHE[('Old','1','2')]=(0,{'summary':'Old · 20°','available':True})
        with self.post({'op':'weather','name':'Athens','latitude':'37.98','longitude':'23.73'}):pass
        with patch('urllib.request.urlopen',side_effect=OSError('Offline')):
            self.assertEqual(preview.current_weather(),{'summary':'Athens · Weather unavailable','available':False})
        preview.WEATHER_CACHE.clear()

    def test_all_preview_files_exist_and_are_served(self):
        for route,path in preview.FILES.items():
            with self.subTest(route=route),urlopen(self.url+route) as response:
                self.assertEqual(response.read(),path.read_bytes());self.assertEqual(response.headers['X-Content-Type-Options'],'nosniff')
    def test_repository_and_traversal_are_not_served(self):
        for route in ['/README.md','/launcher/local.properties','/../README.md','/%2e%2e/README.md','/phone/media/../../README.md']:
            with self.subTest(route=route),self.assertRaises(HTTPError) as error:urlopen(self.url+route)
            self.assertEqual(error.exception.code,404);error.exception.close()
    def test_editor_updates_shared_state_and_persists(self):
        with self.post({'op':'settings','settings':{'wallpaper_selection':'aerial','wallpaper_motion':'always'}}):pass
        with self.post({'op':'rename','id':'com.nuvio.tv','name':'Cinema'}):pass
        with urlopen(self.url+'/phone/api/state') as response:state=json.load(response)
        self.assertEqual(state['settings']['wallpaper_selection'],'aerial');self.assertEqual(state['apps'][0]['name'],'Cinema')
        self.assertEqual(json.loads((preview.DATA/'state.json').read_text())['apps'][0]['name'],'Cinema')
    def test_bad_settings_are_atomic(self):
        with self.assertRaises(HTTPError) as error:self.post({'op':'settings','settings':{'large_clock':True,'wallpaper_motion':'invalid'}})
        error.exception.close();self.assertFalse(preview.STATE['settings']['large_clock'])
    def test_cross_origin_write_rejected(self):
        with self.assertRaises(HTTPError) as error:self.post({'op':'settings','settings':{'large_clock':True}},'https://foreign.example')
        self.assertEqual(error.exception.code,403);error.exception.close();self.assertFalse(preview.STATE['settings']['large_clock'])
    def test_hidden_apps_leave_dock_and_can_be_recovered(self):
        with self.post({'op':'hide','id':'com.nuvio.tv','hidden':True}):pass
        self.assertNotIn('com.nuvio.tv',preview.STATE['dock'])
        with self.post({'op':'hide','id':'com.nuvio.tv','hidden':False}):pass
        self.assertFalse(preview.STATE['apps'][0]['hidden'])
    def test_ranges_and_invalid_uploads(self):
        with urlopen(Request(self.url+'/assets/misty.jpg',headers={'Range':'bytes=0-15'})) as response:
            self.assertEqual(response.status,206);self.assertEqual(len(response.read()),16)
        with self.assertRaises(HTTPError) as error:urlopen(Request(self.url+'/phone/api/upload/photo',data=b'<html>not an image</html>',headers={'Content-Type':'application/octet-stream'}))
        error.exception.close();self.assertEqual(preview.STATE['media'],[]);self.assertEqual(list(preview.DATA.glob('*.jpg')),[])
    def test_duplicates_and_hidden_dock_entries_rejected(self):
        with self.assertRaises(HTTPError) as error:self.post({'op':'dock','items':['com.nuvio.tv','com.nuvio.tv']})
        error.exception.close();self.assertEqual(len(preview.STATE['dock']),5)
    def test_personal_media_upload_use_and_delete(self):
        data=preview.FILES['/assets/misty.jpg'].read_bytes()
        with urlopen(Request(self.url+'/phone/api/upload/photo',data=data,headers={'Content-Type':'application/octet-stream'})) as response:key=json.load(response)['id']
        with self.post({'op':'wallpaperPhoto','media':key}):pass
        self.assertEqual(preview.STATE['personalWallpaper'],key)
        with self.post({'op':'deleteMedia','media':key}):pass
        self.assertEqual(preview.STATE['storage'],0);self.assertFalse(preview.media_file(key).exists())

    def test_scene_catalogue_has_real_previews_and_filters_persist(self):
        with urlopen(self.url+'/phone/api/scenes') as response:videos=json.load(response)['videos']
        self.assertEqual(len(videos),114);self.assertTrue(all(item['thumbnail'].startswith('https://sylvan.apple.com/') for item in videos))
        first=videos[0]
        with self.post({'op':'scenePreference','kind':'favorite','id':first['id'],'enabled':True}):pass
        with self.post({'op':'scenePreference','kind':'category','id':first['category'],'enabled':True}):pass
        self.assertIn(first['id'],preview.STATE['scenes']['favorites'])
        with self.assertRaises(HTTPError) as error:self.post({'op':'sceneSelect','id':first['id']})
        error.exception.close()
        with self.post({'op':'scenePreference','kind':'category','id':first['category'],'enabled':False}):pass
        with self.post({'op':'sceneSelect','id':first['id']}):pass
        self.assertEqual(preview.STATE['scenes']['selected'],first['id']);self.assertEqual(preview.STATE['settings']['wallpaper_selection'],'aerial')
    def test_unknown_scene_and_remote_commands_are_rejected(self):
        for command in [{'op':'scenePreference','kind':'favorite','id':'unknown','enabled':True},{'op':'remote','key':'run-shell'},{'op':'scenePreference','kind':'category','id':'cities','enabled':'yes'}]:
            with self.assertRaises(HTTPError) as error:self.post(command)
            error.exception.close()
        self.assertEqual(preview.STATE['scenes']['favorites'],[]);self.assertNotIn('remoteEvent',preview.STATE)
    def test_remote_events_have_monotonic_sequence_without_changing_layout(self):
        original=copy.deepcopy(preview.STATE['dock'])
        with self.post({'op':'remote','key':'right'}):pass
        self.assertEqual(preview.STATE['remoteEvent'],{'seq':1,'key':'right'})
        with self.post({'op':'remote','key':'ok'}):pass
        self.assertEqual(preview.STATE['remoteEvent']['seq'],2);self.assertEqual([e['key'] for e in preview.STATE['remoteEvents']],['right','ok']);self.assertEqual(preview.STATE['dock'],original)

if __name__=='__main__':unittest.main()
