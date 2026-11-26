import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))

test('KnowledgeBaseView 仅在开启留言板且存在文档时挂载评论区', async () => {
    const viewSource = await readFile(join(root, 'src/components/KnowledgeBaseView.vue'), 'utf8')
    assert.match(viewSource, /<div\s+v-if="isGuestbookEnabled && currentDoc\?\.id"[^>]*>\s*<KbCommentSection/)
})

test('KbCommentSection 仅在开启留言板且已登录时请求评论接口', async () => {
    const commentSectionSource = await readFile(
        join(root, 'src/components/knowledge-base/KbCommentSection.vue'),
        'utf8'
    )
    assert.match(commentSectionSource, /if\s*\(!props\.docId \|\| !props\.isGuestbookEnabled \|\| !props\.isLoggedIn\)\s*return/)
    assert.match(commentSectionSource, /if\s*\(newDocId && guestbookEnabled && loggedIn\)/)
})

test('request 拦截器在公开浏览页遇到 403 不强制跳转登录', async () => {
    const requestSource = await readFile(join(root, 'src/utils/request.ts'), 'utf8')
    assert.match(requestSource, /if\s*\(res\.code === 1005 \|\| res\.code === 1006 \|\| res\.code === 401\)/)
    assert.match(requestSource, /const isPublicViewPage = window\.location\.pathname === '\/view' \|\| window\.location\.pathname\.startsWith\('\/view\/'\)/)
    assert.match(requestSource, /if\s*\(response\?\.status === 403 && isPublicViewPage\)\s*\{\s*return Promise\.reject\(error\)\s*\}/)
})

test('KnowledgeBaseView 移动端顶部栏目录按钮在标题左侧且右侧为更多菜单', async () => {
    const viewSource = await readFile(join(root, 'src/components/KnowledgeBaseView.vue'), 'utf8')
    const headerMatch = viewSource.match(/<header v-if="isMobile && !isStandalone"[\s\S]*?<\/header>/)
    assert.ok(headerMatch, '应存在移动端顶部栏')
    const header = headerMatch[0]
    assert.doesNotMatch(header, /kb-icon-shell/, '移动端顶部栏不应保留知识库图标')
    assert.ok(
        header.indexOf('isMobileDrawerOpen = true') < header.indexOf('kbDetail?.title'),
        '展开目录按钮应位于标题左侧'
    )
    assert.match(header, /mobileMoreMenuOpen/)
    assert.match(header, /router\.push\('\/'\)/)
    assert.match(header, /toggleTheme\(\)/)
    assert.match(header, /openShareModal/)
    assert.match(header, /handleExportCurrentDoc\(\)/)
    assert.match(viewSource, /<div v-if="!isMobile" class="h-12 px-3 border-t/)
    assert.match(viewSource, /<div v-if="!isMobile && !isStandalone && currentDoc" class="absolute top-4 right-6 z-30" ref="moreMenuRef">/)
    assert.doesNotMatch(viewSource, /<button\s+v-else\s+@click="isMobileDrawerOpen = false"/)
})
