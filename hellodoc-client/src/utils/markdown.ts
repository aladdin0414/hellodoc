import { marked } from 'marked'
import katex from 'katex'

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
 * LaTeX 公式自愈与容错修复
 *
 * 常见大模型缺陷场景：
 * 1. 宏命令与后随英文字母缺失空格粘连（如 \cdotij、\quadji、\lambdaq、\lambdaw、\timesx、\partialy）；
 *    在 LaTeX 中，\cdot、\quad、\lambda 是无参控制词，若紧贴字母会被识别为未定义的宏名（如 \cdotij），
 *    导致 KaTeX 无法解析并在界面报红。
 * 2. 连续多个反斜杠宏命令粘连（如 \quad\cdot 没有空格是合法的，但 \quadji 粘连字母非法）。
 */
export const healLatexFormula = (formula: string): string => {
    if (!formula) return ''
    let healed = formula

    // 修复无参宏命令与后续变量字母粘连的情况（如 \cdotij -> \cdot ij, \quadji -> \quad ji, \lambdaq -> \lambda q）
    healed = healed.replace(
        /\\(cdot|quad|qquad|lambda|alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|mu|nu|xi|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega|Gamma|Delta|Theta|Lambda|Xi|Pi|Sigma|Upsilon|Phi|Psi|Omega|partial|nabla|times|div|pm|mp|approx|neq|le|ge|leq|geq|in|notin|subset|supset|cap|cup|to|leftarrow|rightarrow|Rightarrow|Leftarrow|infty|forall|exists|sin|cos|tan|log|ln|det|dim|ker|lim|max|min|arg|hom)([a-zA-Z])/g,
        '\\$1 $2'
    )

    return healed
}

/**
 * 中文与全角标点 Markdown 语法增强处理
 *
 * 背景与解决痛点：
 * 1. 大模型输出列表加粗时，常出现 `***标题**:` 缺失空格的情况，CommonMark 无法识别为列表项；
 * 2. 大模型输出中常出现 Unicode 圆点 `•` 或 `·` 作为列表，需统一转换为 Markdown 列表语法；
 * 3. ATX 标题若 `#` 后无空格（如 `#标题`），需自动补充空格；
 * 4. 紧贴中文文字或标点的加粗边界定界符注入微小空格，确保 100% 成功解析为 `<strong>`；
 * 5. 严格保护代码块与数学公式，避免其内部的 `_`、`*`、`\` 被误当作 Markdown 样式解析；
 * 6. 占位符禁止使用下划线（如 __SLOT__），防止与 Markdown 加粗定界符混淆产生格式错乱。
 */
export const formatChineseMarkdown = (content: string): string => {
    if (!content) return ''

    // 0. 剥离思考标签及大模型泄漏的底层控制标记
    let text = content
        .replace(/<think>[\s\S]*?(?:<\/think>|$)/gi, '')
        .replace(/<thought>[\s\S]*?(?:<\/thought>|$)/gi, '')
        .replace(/<thinking>[\s\S]*?(?:<\/thinking>|$)/gi, '')
        .replace(/<\|im_end\|>|<\|endoftext\|>|<\|im_start\|>|<\|end\|>/g, '')

    // 1. 保护代码块 (使用 HTML 注释作为占位符，绝不包含 Markdown 敏感字符)
    const codeBlocks: string[] = []
    text = text.replace(/(```[\s\S]*?```|`[^`\n]+`)/g, (match) => {
        codeBlocks.push(match)
        return `<!--MD_CODE_BLOCK_SLOT_${codeBlocks.length - 1}-->`
    })

    // 2. 将 LaTeX 标准定界符规范化为通用 Markdown 语法
    // DeepSeek、OpenAI 等模型常输出 \[ ... \]（块级）与 \( ... \)（行内）
    // 若不转换，CommonMark 会将反斜杠作为转义符吞掉，暴露出字面的 [ ... ] 与 ( ... )
    text = text.replace(/\\\[([\s\S]+?)\\\]/g, (_, formula) => `\n\n$$\n${formula.trim()}\n$$\n\n`)
    text = text.replace(/\\\(([\s\S]+?)\\\)/g, (_, formula) => `$${formula.trim()}$`)

    // 3. 保护数学公式，防止后续排版正则误伤公式内的 *、_、\ 等符号
    const mathSlots: string[] = []
    text = text.replace(/(\$\$[\s\S]+?\$\$|(?<![\$\\])\$[^\$\n]+?\$(?!\$))/g, (match) => {
        mathSlots.push(match)
        return `<!--MD_MATH_SLOT_${mathSlots.length - 1}-->`
    })

    // 4. 规范化行首无序列表 + 加粗：如 `***标题**:` 或 `***标题***` 转换为 `* **标题**:`
    text = text.replace(/(^|\n)[ \t]*\*{3,}[ \t]*([^*\n]+?)[ \t]*\*{2,3}/g, '$1* **$2**')

    // 5. 将行首 Unicode 圆点（如 `•` 或 `·`）规范化为 Markdown 无序列表符号 `* `
    text = text.replace(/(^|\n)[ \t]*[•·][ \t]*/g, '$1* ')

    // 6. 规范化行首单个无序列表符号紧贴文字漏掉空格的情况（如 `*相关性` -> `* 相关性`）
    text = text.replace(/(^|\n)[ \t]*([*+\-])([^\s*+\-\n])/g, '$1$2 $3')

    // 7. 规范化行首有序列表序号紧贴文字漏掉空格的情况（如 `1.核心` -> `1. 核心`）
    text = text.replace(/(^|\n)[ \t]*(\d+\.)([^\s\d\.\n])/g, '$1$2 $3')

    // 8. 规范化行首引用符号紧贴文字漏掉空格的情况（如 `>引用` -> `> 引用`）
    text = text.replace(/(^|\n)[ \t]*(>+)([^\s>\n])/g, '$1$2 $3')

    // 9. 规范化 ATX 标题：确保 `#` 和标题文字之间有且仅有一个空格（如 `###标题` -> `### 标题`）
    text = text.replace(/(^|\n)(#{1,6})([^\s#\n])/g, '$1$2 $3')

    // 10. 针对中文标点/字符与 Markdown 加粗符号紧贴导致 CommonMark flanking 判定失效的问题
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

    // 11. 还原数学公式与代码块
    text = text.replace(/<!--MD_MATH_SLOT_(\d+)-->/g, (_, idx) => mathSlots[Number(idx)] ?? '')
    text = text.replace(/<!--MD_CODE_BLOCK_SLOT_(\d+)-->/g, (_, idx) => codeBlocks[Number(idx)] ?? '')

    return text
}

// 初始化 marked 默认配置
marked.setOptions({
    gfm: true,
    breaks: true
})

/**
 * 完整解析 Markdown 文本为 HTML，同时安全且优雅地渲染 LaTeX 数学公式 (KaTeX)
 */
export const renderMarkdownToHtml = (rawContent: string): string => {
    if (!rawContent) return ''

    // 1. 先进行中文、排版及 LaTeX 定界符标准化修复
    const formatted = formatChineseMarkdown(rawContent)

    // 2. 提取并预先渲染 LaTeX 数学公式
    // 【关键设计】必须使用标准的独立 HTML 标签作为插槽占位，严禁使用下划线（如 __KATEX_SLOT__）
    // 否则在 CommonMark/Marked 规则下双下划线会被当作加粗语法解析，导致插槽名被当作文字泄漏且大范围误加粗。
    const mathHtmlList: string[] = []

    // 2.1 提取块级公式 $$ ... $$
    let text = formatted.replace(/\$\$([\s\S]+?)\$\$/g, (_, formula) => {
        const healedFormula = healLatexFormula(formula.trim())
        try {
            const html = katex.renderToString(healedFormula, {
                displayMode: true,
                throwOnError: false
            })
            mathHtmlList.push(`<div class="katex-display-wrapper overflow-x-auto my-2 py-1 text-center">${html}</div>`)
        } catch {
            mathHtmlList.push(`<pre class="katex-error text-xs text-red-500 my-1">$$${healedFormula}$$</pre>`)
        }
        return `\n\n<div class="katex-block-placeholder" data-index="${mathHtmlList.length - 1}"></div>\n\n`
    })

    // 2.2 提取行内公式 $ ... $
    text = text.replace(/(?<![\$\\])\$([^\$\n]+?)\$(?!\$)/g, (match, formula) => {
        const trimmed = formula.trim()
        // 排除纯数字金额 如 $100, $3.50
        if (/^\d+(\.\d+)?$/.test(trimmed)) {
            return match
        }
        const healedFormula = healLatexFormula(trimmed)
        try {
            const html = katex.renderToString(healedFormula, {
                displayMode: false,
                throwOnError: false
            })
            mathHtmlList.push(html)
            return `<span class="katex-inline-placeholder" data-index="${mathHtmlList.length - 1}"></span>`
        } catch {
            return match
        }
    })

    // 3. 执行 marked 解析
    let html = marked.parse(text, { async: false, breaks: true, gfm: true }) as string

    // 4. 将数学公式 HTML 插槽原样还原
    html = html.replace(/<div class="katex-block-placeholder" data-index="(\d+)"><\/div>/g, (_, idx) => mathHtmlList[Number(idx)] ?? '')
    html = html.replace(/<span class="katex-inline-placeholder" data-index="(\d+)"><\/span>/g, (_, idx) => mathHtmlList[Number(idx)] ?? '')

    return html
}



