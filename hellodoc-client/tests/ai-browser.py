"""Run against npm run dev:client: python3 hellodoc-client/tests/ai-browser.py.
Uses Chrome + Python Playwright and mocked model responses; never calls a real model.
"""
import json
import os
import re
import urllib.request
from playwright.sync_api import sync_playwright, expect

origin = os.environ.get('HELLODOC_TEST_URL', 'http://localhost:3000')
source = urllib.request.urlopen(origin + '/src/components/ai/AiAssistantDrawer.vue').read().decode()
vue = re.search(r'from "([^\"]*/vue.js[^\"]*)"', source).group(1)
html = '''<!doctype html><html><head><meta charset="utf-8"></head><body><div id="app"></div>
<script type="module">
import {createApp,h,reactive,nextTick} from "VUE_URL";
import Drawer from '/src/components/ai/AiAssistantDrawer.vue';
import {i18n} from '/src/i18n/index.ts';
import {aiCompletionStream} from '/src/api/ai.ts';
import '/src/style.css';
localStorage.setItem('accessToken','test-only');
const props=reactive({visible:false,docId:1,documentContext:'doc A',isReadOnly:false});
window.test={props,insertions:[],abortCount:0,stream:aiCompletionStream};
const fetchOriginal=window.fetch;
window.fetch=(url,options)=>{if(options?.signal)options.signal.addEventListener('abort',()=>window.test.abortCount++);return fetchOriginal(url,options)};
createApp({render:()=>h(Drawer,{...props,'onUpdate:visible':v=>props.visible=v,'onInsert-to-doc':(text,docId)=>window.test.insertions.push({text,docId})})}).use(i18n).mount('#app');
await nextTick();props.visible=true;
</script></body></html>'''.replace('VUE_URL', vue)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, channel='chrome')
    page = browser.new_page(viewport={'width': 1100, 'height': 900})
    errors, requests, held = [], [], []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('**/__ai_browser_test', lambda route: route.fulfill(content_type='text/html', body=html))
    page.route('**/api/ai/models/active', lambda route: route.fulfill(json={'code': 200, 'data': [{'id': 'test', 'name': 'Local test', 'isDefault': True}]}))

    def stream(route):
        payload = route.request.post_data_json
        requests.append(payload)
        if payload['prompt'].startswith(('stall', 'timeout')):
            held.append(route)
            return
        text = '<img src="/missing-test-image" onerror="window.__unsafeAiHtml=true">ABC123\n\n$x^2$'
        body = 'event:chunk\ndata:' + json.dumps(text) + '\n\nevent:done\ndata:ok\n\n'
        route.fulfill(content_type='text/event-stream', body=body)

    page.route('**/api/ai/completion/stream', stream)
    page.goto(origin + '/__ai_browser_test')
    prompt = page.get_by_placeholder('输入问题或指令，Enter 发送，Shift+Enter 换行...')

    def send(text):
        prompt.fill(text)
        page.get_by_role('button', name='发送', exact=True).click()

    send('remember ABC123')
    expect(page.get_by_role('button', name='插入文档', exact=True)).to_be_visible()
    assert page.evaluate('window.__unsafeAiHtml === true') is False
    assert page.locator('.ai-markdown-content [onerror]').count() == 0
    expect(page.locator('.ai-markdown-content .katex')).to_have_count(1)
    print('PASS safe HTML rendering and KaTeX', flush=True)

    send('what was the code?')
    expect(page.get_by_role('button', name='插入文档', exact=True)).to_have_count(2)
    assert requests[1]['history'][0] == {'role': 'user', 'content': 'remember ABC123'}
    assert requests[1]['history'][1]['role'] == 'assistant'
    print('PASS multi-turn request history', flush=True)

    page.evaluate("window.test.props.docId=2;window.test.props.documentContext='doc B'")
    expect(page.locator('.ai-markdown-content')).to_have_count(0)
    expect(page.get_by_role('button', name='插入文档', exact=True)).to_have_count(0)
    print('PASS document switch clears previous answers', flush=True)

    send('stall stop test')
    expect(page.get_by_role('button', name='停止生成', exact=True)).to_be_visible()
    baseline = page.evaluate('window.test.abortCount')
    page.get_by_role('button', name='停止生成', exact=True).click()
    expect(prompt).to_be_enabled()
    assert page.evaluate('window.test.abortCount') > baseline
    print('PASS Stop cancels the browser request', flush=True)

    send('stall close test')
    expect(page.get_by_role('button', name='停止生成', exact=True)).to_be_visible()
    baseline = page.evaluate('window.test.abortCount')
    page.locator('aside > div').first.locator('button').last.click()
    page.wait_for_function('(n)=>window.test.abortCount>n', arg=baseline)
    page.evaluate('window.test.props.visible=true')
    expect(prompt).to_be_enabled()
    print('PASS closing the drawer cancels generation', flush=True)

    send('stall doc switch test')
    expect(page.get_by_role('button', name='停止生成', exact=True)).to_be_visible()
    baseline = page.evaluate('window.test.abortCount')
    page.evaluate("window.test.props.docId=3;window.test.props.documentContext='doc C'")
    expect(prompt).to_be_enabled()
    expect(page.locator('.ai-markdown-content')).to_have_count(0)
    assert page.evaluate('window.test.abortCount') > baseline
    print('PASS document switch cancels generation', flush=True)

    timeout = page.evaluate('''async()=>{try{await window.test.stream({context:'',prompt:'timeout check'},{onChunk(){}},{timeoutMs:30});return 'no timeout'}catch(e){return e.name}}''')
    assert timeout == 'TimeoutError', timeout
    print('PASS request timeout', flush=True)

    send('completed after interruption')
    expect(page.get_by_role('button', name='插入文档', exact=True)).to_be_visible()
    assert requests[-1]['history'] == []
    page.evaluate('window.test.props.isReadOnly=true')
    expect(page.get_by_role('button', name='插入文档', exact=True)).to_be_disabled()
    page.evaluate('window.test.props.isReadOnly=false')
    page.get_by_role('button', name='插入文档', exact=True).click()
    assert page.evaluate('window.test.insertions[0].docId') == 3
    print('PASS insertion respects readonly state and document identity', flush=True)
    assert not errors, errors
    print('PASS no page script errors', flush=True)
    for route in held:
        try:
            route.abort()
        except Exception:
            pass
    browser.close()
