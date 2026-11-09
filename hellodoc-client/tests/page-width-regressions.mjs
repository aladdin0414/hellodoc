import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { parse, compileScript } from '@vue/compiler-sfc'
import { readFile, rm } from 'node:fs/promises'
import { JSDOM } from 'jsdom'
const dom = new JSDOM('<!doctype html><body></body>', {url:'http://localhost'})
for (const key of ['window','document','navigator','Node','Element','HTMLElement','SVGElement','localStorage']) Object.defineProperty(globalThis,key,{value:dom.window[key],configurable:true})
const output = new URL('../node_modules/.tmp/page-width-regressions.mjs',import.meta.url)
const {dependencies} = JSON.parse(await readFile(new URL('../package.json',import.meta.url),'utf8'))
await build({stdin:{contents:"export {default as Toolbar} from './src/components/editor/VisualEditorToolbar.vue'; export {i18n} from './src/i18n/index.ts'",resolveDir:new URL('..',import.meta.url).pathname},bundle:true,format:'esm',platform:'node',outfile:output.pathname,external:Object.keys(dependencies),plugins:[{name:'vue',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{if(!path.endsWith('/VisualEditorToolbar.vue'))return{contents:'export default {render(){return null}}'};const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'page-width-test',inlineTemplate:true}).content,loader:'ts'}})}}]})
const {Toolbar,i18n}=await import(output.href)
const {createApp,h,reactive,nextTick}=await import('vue')
test('page width button cycles through the three modes',async()=>{
 const props=reactive({editor:{isActive:()=>false},isPlainTextMode:false,isPreviewMode:false,pageMode:'portrait',isExportingWord:false,isExportingMarkdown:false,canExportMarkdown:false})
 const root=document.createElement('div');document.body.append(root)
 const app=createApp({render:()=>h(Toolbar,{...props,'onUpdate:pageMode':v=>props.pageMode=v})});app.use(i18n).mount(root)
 try{const button=root.querySelector('button[title="Toggle Page Mode"]');assert.ok(button);for(const mode of ['landscape','full','portrait']){button.click();await nextTick();assert.equal(props.pageMode,mode)}}finally{app.unmount();root.remove()}
})
test('native list format selector applies the chosen format',async()=>{
 const applied=[]; const node={type:{name:'orderedList'},attrs:{}}
 const editor={isActive:(name)=>name==='orderedList',state:{selection:{$from:{depth:1,node:()=>node,before:()=>0}},tr:{setNodeMarkup:(_pos,_type,attrs)=>{applied.push(attrs.listFormat);return {}}}},view:{dispatch(){}},commands:{focus(){}}}
 const root=document.createElement('div');document.body.append(root)
 const app=createApp({render:()=>h(Toolbar,{editor,isPlainTextMode:false,isPreviewMode:false,pageMode:'portrait',isExportingWord:false,isExportingMarkdown:false,canExportMarkdown:false})});app.use(i18n).mount(root)
 try{const select=root.querySelector('select[aria-label="List numbering format"]');assert.ok(select);assert.deepEqual([...select.options].slice(1).map(o=>o.value),['decimal','paren','circle','mixed','outline']);assert.equal(select.options[5].textContent,'1. / 1.1 / 1.1.1');select.value='mixed';select.dispatchEvent(new window.Event('change',{bubbles:true}));assert.deepEqual(applied,['mixed']);assert.equal(select.value,'')}finally{app.unmount();root.remove()}
})
await rm(output,{force:true})
