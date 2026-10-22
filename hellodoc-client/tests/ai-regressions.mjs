import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { parse, compileScript } from '@vue/compiler-sfc'
import { readFile, rm } from 'node:fs/promises'
import { JSDOM } from 'jsdom'
const dom = new JSDOM('<!doctype html><body></body>', { url: 'http://localhost' })
for (const key of ['window','document','navigator','Node','Element','HTMLElement','SVGElement','localStorage']) Object.defineProperty(globalThis,key,{value:dom.window[key],configurable:true})
globalThis.requestAnimationFrame=callback=>setTimeout(callback,0)
globalThis.getComputedStyle=dom.window.getComputedStyle.bind(dom.window)
localStorage.setItem('accessToken','test-only')
const output=new URL('../node_modules/.tmp/ai-regressions.mjs',import.meta.url)
const {dependencies}=JSON.parse(await readFile(new URL('../package.json',import.meta.url),'utf8'))
await build({
 stdin:{contents:`export {default as Drawer} from './src/components/ai/AiAssistantDrawer.vue'; export {aiCompletionStream} from './src/api/ai.ts'; export {renderMarkdownToHtml} from './src/utils/markdown.ts'; export {i18n} from './src/i18n/index.ts'`,resolveDir:new URL('..',import.meta.url).pathname},
 bundle:true,format:'esm',platform:'node',outfile:output.pathname,external:Object.keys(dependencies),
 plugins:[{name:'vue',setup(b){
  b.onResolve({filter:/\.css$/},()=>({path:'empty',namespace:'empty-css'}))
  b.onLoad({filter:/.*/,namespace:'empty-css'},()=>({contents:''}))
  b.onLoad({filter:/\/useAiModels\.ts$/},()=>({contents:`import {ref} from 'vue';export const useAiModels=()=>({activeModels:ref([]),currentModelId:ref('test'),selectedModel:ref({name:'test'}),fetchActiveModels(){},selectModel(){}})`}))
  b.onLoad({filter:/\.vue$/},async({path})=>{const{descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'ai-test',inlineTemplate:true}).content,loader:'ts'}})
 }}],
})
const{Drawer,aiCompletionStream,renderMarkdownToHtml,i18n}=await import(output.href)
const{createApp,h,reactive,nextTick}=await import('vue')
const tick=()=>new Promise(resolve=>setTimeout(resolve,5))
const sse=text=>`event: chunk\ndata: ${JSON.stringify(text)}\n\nevent: done\ndata: ok\n\n`
const response=text=>new Response(text,{headers:{'Content-Type':'text/event-stream'}})
async function mount(){
 const props=reactive({visible:false,docId:1,documentContext:'doc A'})
 const root=document.createElement('div');document.body.append(root)
 const app=createApp({render:()=>h(Drawer,{...props,'onUpdate:visible':v=>props.visible=v})})
 app.use(i18n).mount(root);props.visible=true;await nextTick()
 return{props,root,close(){app.unmount();root.remove()}}
}
async function send(root,text){
 const input=root.querySelector('textarea');input.value=text;input.dispatchEvent(new window.Event('input',{bubbles:true}));await nextTick()
 input.dispatchEvent(new window.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));await tick()
}
test('AI HTML strips active content while preserving KaTeX',()=>{
 const html=renderMarkdownToHtml('<img src="x" onerror="alert(1)"><script>alert(1)</script><a href="javascript:alert(1)">link</a>\n\n$x^2$')
 assert.doesNotMatch(html,/onerror|<script|javascript:/i);assert.match(html,/katex/)
})
test('follow-up requests include completed history',async()=>{
 const requests=[];globalThis.fetch=async(_url,o)=>{requests.push(JSON.parse(o.body));return response(sse('reply'))}
 const c=await mount();try{await send(c.root,'remember ABC');await send(c.root,'what was it?');assert.deepEqual(requests[1].history,[{role:'user',content:'remember ABC'},{role:'assistant',content:'reply'}])}finally{c.close()}
})
function stalledFetch(capture){globalThis.fetch=(_url,o)=>{capture(o.signal);return new Promise((_resolve,reject)=>o.signal?.addEventListener('abort',()=>reject(o.signal.reason)))}}
test('document switches abort requests and clear history',async()=>{
 let signal;stalledFetch(s=>signal=s);const c=await mount()
 try{await send(c.root,'doc A question');c.props.docId=2;c.props.documentContext='doc B';await tick();assert.equal(signal?.aborted,true);assert.doesNotMatch(c.root.textContent,/doc A question/);assert.equal(c.root.querySelector('textarea').disabled,false)}finally{c.close()}
})
test('Stop aborts generation and restores input',async()=>{
 let signal;stalledFetch(s=>signal=s);const c=await mount()
 try{await send(c.root,'question');const stop=[...c.root.querySelectorAll('button')].find(b=>b.textContent.includes('停止生成'));assert.ok(stop);stop.click();await tick();assert.equal(signal.aborted,true);assert.equal(c.root.querySelector('textarea').disabled,false)}finally{c.close()}
})
test('timeouts abort unresponsive requests',async()=>{
 globalThis.fetch=(_url,o)=>new Promise((_resolve,reject)=>{const timer=setTimeout(()=>reject(new Error('request was not aborted')),40);o.signal?.addEventListener('abort',()=>{clearTimeout(timer);reject(o.signal.reason)})});await assert.rejects(aiCompletionStream({context:'',prompt:'q'},{onChunk(){}},{timeoutMs:10}),/超时|timeout/i)
})
test('premature EOF reports an incomplete answer',async()=>{
 globalThis.fetch=async()=>response('event: chunk\ndata: "partial"\n\n');await assert.rejects(aiCompletionStream({context:'',prompt:'q'},{onChunk(){}}),/中断|完整|ended/i)
})
test('closing the drawer aborts its request',async()=>{
 let signal;stalledFetch(s=>signal=s);const c=await mount()
 try{await send(c.root,'question');c.props.visible=false;await tick();assert.equal(signal.aborted,true)}finally{c.close()}
})
test('unmounting the drawer aborts its request',async()=>{
 let signal;stalledFetch(s=>signal=s);const c=await mount();await send(c.root,'question');c.close();assert.equal(signal.aborted,true)
})
test('stopped replies are excluded from subsequent model history',async()=>{
 stalledFetch(()=>{});const c=await mount()
 try{await send(c.root,'interrupted');[...c.root.querySelectorAll('button')].find(b=>b.textContent.includes('停止生成')).click();await tick()
 let sent;globalThis.fetch=async(_url,o)=>{sent=JSON.parse(o.body);return response(sse('complete'))};await send(c.root,'new question');assert.deepEqual(sent.history,[])
 }finally{c.close()}
})
test('done events release a response body without waiting for EOF',async()=>{
 let cancelled=false;globalThis.fetch=async()=>new Response(new ReadableStream({start(c){c.enqueue(new TextEncoder().encode(sse('done')))},cancel(){cancelled=true}}))
 await aiCompletionStream({context:'',prompt:'q'},{onChunk(){}});assert.equal(cancelled,true)
})
test('conversation history stays within the server message limit',async()=>{
 const requests=[];globalThis.fetch=async(_url,o)=>{requests.push(JSON.parse(o.body));return response(sse('reply'))};const c=await mount()
 try{for(let i=0;i<12;i++)await send(c.root,'question '+i);assert.equal(requests[11].history.length,20);assert.equal(requests[11].history[0].content,'question 1')
 }finally{c.close()}
})
await rm(output,{force:true})
