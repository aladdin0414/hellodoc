import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile } from 'node:fs/promises'
import postcss from 'postcss'
import selectorParser from 'postcss-selector-parser'

function specificity(selector) {
    const root = selectorParser().astSync(selector)
    function score(container) {
        let value = 0
        container.each(node => {
            if (node.type === 'id') value += 10000
            else if (['class', 'attribute'].includes(node.type)) value += 100
            else if (node.type === 'tag' || (node.type === 'pseudo' && node.value.startsWith('::'))) value++
            else if (node.type === 'pseudo' && node.value === ':is') value += Math.max(...node.nodes.map(score))
            else if (node.type === 'pseudo' && node.value !== ':where') value += 100
        })
        return value
    }
    return Math.max(...root.nodes.map(score))
}

test('list format marker rules override the nested list default', async () => {
    const editor = postcss.parse(await readFile(new URL('../src/components/editor/editor-styles.css', import.meta.url), 'utf8'))
    const global = postcss.parse(await readFile(new URL('../src/style.css', import.meta.url), 'utf8'))
    let defaultSelector
    editor.walkRules(rule => { if (rule.selector.includes('ol ol > li::marker')) defaultSelector = rule.selector })
    assert.ok(defaultSelector)
    for (const format of ['decimal', 'paren', 'circle', 'mixed', 'outline']) {
        let selector
        global.walkRules(rule => {
            for (const part of rule.selectors) {
                if (part.includes(`data-list-format="${format}"`) && part.includes(' ol > li::marker')) selector = part
            }
        })
        assert.ok(selector, format)
        assert.ok(specificity(selector) > specificity(defaultSelector), `${format} must override ${defaultSelector}`)
    }
})
