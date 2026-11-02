#!/usr/bin/env python3
import json, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
LOG='/tmp/qrc-mock-caldav.log'
def log(s):
    with open(LOG,'a') as f: f.write(s+'\n')
def ics(uid, date, start, end, summary, location=''):
    return '\r\n'.join([
        'BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Mock//CalDAV//CN','CALSCALE:GREGORIAN',
        'BEGIN:VEVENT',f'UID:{uid}','DTSTAMP:20261025T080000Z',
        f'DTSTART;TZID=Asia/Shanghai:{date}T{start}00',
        f'DTEND;TZID=Asia/Shanghai:{date}T{end}00',
        f'SUMMARY:{summary}',f'LOCATION:{location}','END:VEVENT','END:VCALENDAR',''
    ])
class H(BaseHTTPRequestHandler):
    def log_message(self,*a): pass
    def _reply(self, code, body=b'', headers=None):
        self.send_response(code)
        for k,v in (headers or {}).items(): self.send_header(k,v)
        if body: self.send_header('Content-Length',str(len(body)))
        self.end_headers()
        if body: self.wfile.write(body)
    def do_PROPFIND(self):
        log('PROPFIND '+self.path)
        body='''<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/</d:href><d:propstat><d:prop><d:displayname>Mock 日历</d:displayname><d:resourcetype><d:collection/><cal:calendar xmlns:cal="urn:ietf:params:xml:ns:caldav"/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>'''.encode()
        self._reply(207, body, {'Content-Type':'application/xml; charset=utf-8'})
    def do_REPORT(self):
        n=int(self.headers.get('content-length','0')); req=self.rfile.read(n)
        log('REPORT '+self.path+' '+req.decode('utf-8','ignore')[:200])
        e=ics('mock-1','20261027','1000','1100','模拟同步日程','会议室 A')
        body=('<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">'
              '<d:response><d:href>/dav/mock-1.ics</d:href><d:propstat><d:prop>'
              '<d:getetag>"etag-1"</d:getetag>'
              '<cal:calendar-data>'+e.replace('&','&amp;').replace('<','&lt;')+'</cal:calendar-data>'
              '</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>'
              '</d:multistatus>').encode()
        self._reply(207, body, {'Content-Type':'application/xml; charset=utf-8'})
    def do_PUT(self):
        n=int(self.headers.get('content-length','0')); body=self.rfile.read(n)
        log('PUT '+self.path+' if-match='+self.headers.get('if-match','')+' if-none='+self.headers.get('if-none-match',''))
        log(body.decode('utf-8','ignore')[:500])
        self._reply(201, b'', {'ETag':'"etag-created"','Content-Type':'text/calendar'})
    def do_DELETE(self):
        log('DELETE '+self.path+' if-match='+self.headers.get('if-match',''))
        self._reply(204)
    def do_GET(self):
        log('GET '+self.path)
        self._reply(200, ics('mock-1','20261027','1000','1100','模拟同步日程','会议室 A').encode(), {'Content-Type':'text/calendar'})
if __name__=='__main__':
    open(LOG,'w').close()
    ThreadingHTTPServer(('0.0.0.0',19091),H).serve_forever()
