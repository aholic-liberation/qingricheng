#!/usr/bin/env python3
import json, time, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG='/tmp/qrc-mock-llm.log'
def log(line):
    with open(LOG,'a') as f: f.write(line+'\n')

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_POST(self):
        n=int(self.headers.get('content-length','0'))
        raw=self.rfile.read(n)
        try: body=json.loads(raw)
        except Exception: body={}
        msgs=body.get('messages',[])
        def summarize(content):
            if isinstance(content, list):
                parts=[]
                for part in content:
                    if not isinstance(part, dict): continue
                    if part.get('type')=='text': parts.append(str(part.get('text','')))
                    elif part.get('type')=='image_url':
                        url=((part.get('image_url') or {}).get('url') or '')
                        parts.append('[image_url bytes=%d prefix=%s]' % (len(url), url[:30]))
                    else: parts.append('[%s]' % part.get('type'))
                return ' | '.join(parts)
            return str(content or '')
        last_raw=msgs[-1].get('content','') if msgs else ''
        last_text=summarize(last_raw)
        log('REQ ' + json.dumps({'path':self.path,'model':body.get('model'),'tools':len(body.get('tools',[])),'roles':[m.get('role') for m in msgs],'last':last_text[:1200],'has_image':'image_url' in last_text},ensure_ascii=False))
        has_tool=any(m.get('role')=='tool' for m in msgs)
        last_user=next((summarize(m.get('content','')) for m in reversed(msgs) if m.get('role')=='user'),'')
        wants_search='search' in last_user.lower() or '搜' in last_user
        wants_caldav='caldav' in last_user.lower() or '日历账号' in last_user
        wants_write='writecaldav' in last_user.lower()
        wants_sync='synccal' in last_user.lower()
        wants_profile='profiletest' in last_user.lower()
        tool_msgs=[m for m in msgs if m.get('role')=='tool']
        self.send_response(200)
        self.send_header('Content-Type','text/event-stream; charset=utf-8')
        self.send_header('Cache-Control','no-cache')
        self.end_headers()
        def send(obj):
            self.wfile.write(('data: '+json.dumps(obj,ensure_ascii=False)+'\n\n').encode()); self.wfile.flush()
        if wants_profile:
            if not tool_msgs:
                tc={'index':0,'id':'call_profile_1','type':'function','function':{'name':'set_profile','arguments':json.dumps({'label':'年级','value':'大三'},ensure_ascii=False)}}
                send({'choices':[{'delta':{'tool_calls':[tc]},'finish_reason':None}]})
                time.sleep(0.15)
                send({'choices':[{'delta':{},'finish_reason':'tool_calls'}]})
            else:
                text='关于我的资料已写入。'
                for ch in [text[i:i+6] for i in range(0,len(text),6)]:
                    send({'choices':[{'delta':{'content':ch},'finish_reason':None}]})
                    time.sleep(0.06)
                send({'choices':[{'delta':{},'finish_reason':'stop'}]})
                self.wfile.write(b'data: [DONE]\n\n'); self.wfile.flush()
                return
        elif wants_sync:
            if not tool_msgs:
                tc={'index':0,'id':'call_sync_1','type':'function','function':{'name':'sync_calendar','arguments':json.dumps({'start':'2026-10-20','end':'2026-11-05'},ensure_ascii=False)}}
                send({'choices':[{'delta':{'tool_calls':[tc]},'finish_reason':None}]})
                time.sleep(0.15)
                send({'choices':[{'delta':{},'finish_reason':'tool_calls'}]})
            else:
                text='双向同步测试完成。'
                for ch in [text[i:i+6] for i in range(0,len(text),6)]:
                    send({'choices':[{'delta':{'content':ch},'finish_reason':None}]})
                    time.sleep(0.06)
                send({'choices':[{'delta':{},'finish_reason':'stop'}]})
                self.wfile.write(b'data: [DONE]\n\n'); self.wfile.flush()
                return
        elif wants_write:
            last_tool={}
            if tool_msgs:
                try: last_tool=json.loads(tool_msgs[-1].get('content','{}'))
                except Exception: pass
            if len(tool_msgs)==0:
                args={'title':'双向测试日程','date':'2026-10-27','start':'15:00','end':'16:00','location':'测试地点','description':'create from api'}
                name='create_event'; call_id='call_write_1'
            elif len(tool_msgs)==1:
                args={'id':last_tool.get('id',''),'title':'双向测试日程（已改）','date':'2026-10-27','start':'16:00','end':'17:00'}
                name='update_event'; call_id='call_write_2'
            elif len(tool_msgs)==2:
                args={'id':last_tool.get('id','')}
                name='delete_event'; call_id='call_write_3'
            else:
                text='双向测试完成：创建、修改、删除都已执行。'
                for ch in [text[i:i+6] for i in range(0,len(text),6)]:
                    send({'choices':[{'delta':{'content':ch},'finish_reason':None}]})
                    time.sleep(0.06)
                send({'choices':[{'delta':{},'finish_reason':'stop'}]})
                self.wfile.write(b'data: [DONE]\n\n'); self.wfile.flush()
                return
            tc={'index':0,'id':call_id,'type':'function','function':{'name':name,'arguments':json.dumps(args,ensure_ascii=False)}}
            send({'choices':[{'delta':{'tool_calls':[tc]},'finish_reason':None}]})
            time.sleep(0.15)
            send({'choices':[{'delta':{},'finish_reason':'tool_calls'}]})
        elif not has_tool and wants_caldav:
            calls=[{'index':0,'id':'call_caldav_1','type':'function','function':{'name':'configure_caldav','arguments':json.dumps({'url':'http://10.0.2.2:19091/dav/test/primary/','username':'test-user','password':'mock-pass','timezone':'Asia/Shanghai'},ensure_ascii=False)}}]
            for c in calls:
                send({'choices':[{'delta':{'tool_calls':[c]},'finish_reason':None}]})
                time.sleep(0.15)
            send({'choices':[{'delta':{},'finish_reason':'tool_calls'}]})
        elif not has_tool and wants_search:
            calls=[{'index':0,'id':'call_search_1','type':'function','function':{'name':'web_search','arguments':json.dumps({'query':'DeepSeek V4.1 Flash','max_results':3},ensure_ascii=False)}}]
            for c in calls:
                send({'choices':[{'delta':{'tool_calls':[c]},'finish_reason':None}]})
                time.sleep(0.15)
            send({'choices':[{'delta':{},'finish_reason':'tool_calls'}]})
        elif not has_tool:
            # Two tool calls: create an event and a sticky.
            calls=[
                {'index':0,'id':'call_event_1','type':'function','function':{'name':'create_event','arguments':json.dumps({'title':'和张老师开会','date':'2026-10-26','start':'15:00','end':'16:00','location':'线上','description':'讨论项目进度'},ensure_ascii=False)}},
                {'index':1,'id':'call_sticky_1','type':'function','function':{'name':'create_sticky','arguments':json.dumps({'text':'记得带会议资料','color':'peach'},ensure_ascii=False)}},
            ]
            for c in calls:
                send({'choices':[{'delta':{'tool_calls':[c]},'finish_reason':None}]})
                time.sleep(0.15)
            send({'choices':[{'delta':{},'finish_reason':'tool_calls'}]})
        elif has_tool and wants_caldav:
            text='CalDAV 已配置并测试通过。'
            for ch in [text[i:i+6] for i in range(0,len(text),6)]:
                send({'choices':[{'delta':{'content':ch},'finish_reason':None}]})
                time.sleep(0.08)
            send({'choices':[{'delta':{},'finish_reason':'stop'}]})
        elif wants_search:
            text='搜索完成。'
            for m in msgs:
                if m.get('role')=='tool':
                    try:
                        data=json.loads(m.get('content','{}'))
                        rs=data.get('results') or []
                        if rs:
                            text='搜索完成：' + rs[0].get('title','') + '\n' + rs[0].get('url','') + '\n' + (rs[0].get('snippet','') or '')[:160]
                    except Exception: pass
            for ch in [text[i:i+6] for i in range(0,len(text),6)]:
                send({'choices':[{'delta':{'content':ch},'finish_reason':None}]})
                time.sleep(0.08)
            send({'choices':[{'delta':{},'finish_reason':'stop'}]})
        else:
            text='已经帮你安排好了：明天下午 3 点和张老师开会，便签也记下了。'
            for ch in [text[i:i+6] for i in range(0,len(text),6)]:
                send({'choices':[{'delta':{'content':ch},'finish_reason':None}]})
                time.sleep(0.08)
            send({'choices':[{'delta':{},'finish_reason':'stop'}]})
        self.wfile.write(b'data: [DONE]\n\n'); self.wfile.flush()

if __name__=='__main__':
    open(LOG,'w').close()
    ThreadingHTTPServer(('0.0.0.0',19090),H).serve_forever()
