/**
 * 清除 Markdown 文本中的 [[toc]] 标签
 */
export const stripMarkdownToc = (content: string): string => {
    return (content || '')
        .split('\n')
        .filter(line => !/^\s*(\[\[toc\]\]|\[toc\]|@\[\s*toc\s*\])\s*$/i.test(line))
        .join('\n')
}

/**
 * 中文与全角标点 Markdown 语法增强处理（参照 MyReader AI书童实现）
 *
 * 背景与原因：
 * 1. 根据 CommonMark 规范（Section 4.5），强调定界符（delimiter run）在判定 left-flanking 与 right-flanking 时，
 *    若定界符左侧为 Unicode 标点（如全角括号 `）`、全角引号 `”`），而右侧为汉字等常规字符时，
 *    该定界符严格不满足 right-flanking 条件，反而会被判定为新的开启标记，导致成对匹配失败，
 *    原样暴露出原始的 `**...**` 字符。
 * 2. 部分大模型输出加粗语法时内部带有多余空格（例如 `** 文本 **`），同样会导致 CommonMark 无法将其正确解析为粗体。
 * 3. 大模型容易泄漏 stop tokens（如 `<|im_end|>` 等）或残留 `<think>` 思考标签。
 * 4. ATX 标题若 `#` 后无空格（如 `#标题`），CommonMark 不会将其识别为标题。
 *
 * 本函数在保留代码块原样的前提下，对 Markdown 进行标准化修复与清洗：
 * 1. 剥离模型底层控制符与思考标签；
 * 2. 保护多行代码块与行内代码；
 * 3. 规范化 ATX 标题语法（确保 `#` 后保留空格）；
 * 4. 清理加粗内部首尾空格，并对紧贴中文文字或标点的边界安全注入微小空格；
 * 5. 还原代码块。
 */
export const formatChineseMarkdown = (content: string): string => {
    if (!content) return ''

    // 0. 剥离思考标签及大模型泄漏的底层控制标记
    let text = content
        .replace(/<think>[\s\S]*?(?:<\/think>|$)/gi, '')
        .replace(/<thought>[\s\S]*?(?:<\/thought>|$)/gi, '')
        .replace(/<thinking>[\s\S]*?(?:<\/thinking>|$)/gi, '')
        .replace(/<\|im_end\|>|<\|endoftext\|>|<\|im_start\|>|<\|end\|>/g, '')

    // 1. 保护代码块 (多行代码块和行内代码)
    const codeBlocks: string[] = []
    text = text.replace(/(```[\s\S]*?```|`[^`\n]+`)/g, (match) => {
        codeBlocks.push(match)
        return `__MD_CODE_BLOCK_${codeBlocks.length - 1}__`
    })

    // 2. 规范化 ATX 标题：确保 `#` 和标题文字之间有且仅有一个空格（如 `###标题` -> `### 标题`）
    text = text.replace(/(^|\n)(#{1,6})([^\s#\n])/g, '$1$2 $3')

    // 3. 针对中文标点/字符与 Markdown 加粗符号紧贴导致 CommonMark flanking 判定失效的问题
    // 同时自动 trim 加粗内部首尾的多余空格（例如 ** 文本 ** 或 **文本 **）
    text = text.replace(/(?<!\*)\*\*\s*([^*\n]+?)\s*\*\*(?!\*)/g, (match, inner, offset, fullStr) => {
        const trimmedInner = inner.trim()
        if (!trimmedInner) return match

        const prevChar = offset > 0 ? fullStr[offset - 1] : ''
        const nextChar = offset + match.length < fullStr.length ? fullStr[offset + match.length] : ''

        const needLeadingSpace = Boolean(prevChar && !/\s/.test(prevChar))
        const needTrailingSpace = Boolean(nextChar && !/\s/.test(nextChar))

        return `${needLeadingSpace ? ' ' : ''}**${trimmedInner}**${needTrailingSpace ? ' ' : ''}`
    })

    // 4. 还原代码块
    text = text.replace(/__MD_CODE_BLOCK_(\d+)__/g, (_, idx) => codeBlocks[Number(idx)] ?? '')

    return text
}

