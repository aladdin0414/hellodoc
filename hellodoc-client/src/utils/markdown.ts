/**
 * 清除 Markdown 文本中的 [[toc]] 标签
 */
export const stripMarkdownToc = (content: string): string => {
    return (content || '')
        .split('\n')
        .filter(line => !/^\s*(\[\[toc\]\]|\[toc\]|@\[\s*toc\s*\])\s*$/i.test(line))
        .join('\n')
}
