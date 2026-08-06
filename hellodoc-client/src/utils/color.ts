/**
 * 将 HEX 颜色转为 RGB 颜色对象
 */
export const hexToRgb = (hex: string): { r: number; g: number; b: number } | null => {
    const shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i
    const fullHex = hex.replace(shorthandRegex, (_m, r, g, b) => {
        return r + r + g + g + b + b
    })
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(fullHex)
    return result ? {
        r: parseInt(result[1]!, 16),
        g: parseInt(result[2]!, 16),
        b: parseInt(result[3]!, 16)
    } : null
}

/**
 * 判断 HEX 颜色是否属于深色
 */
export const isColorDark = (color?: string): boolean => {
    if (!color) return false
    const rgb = hexToRgb(color)
    if (!rgb) return false
    const yiq = ((rgb.r * 299) + (rgb.g * 587) + (rgb.b * 114)) / 1000
    return yiq < 128
}

/**
 * 根据图标颜色和当前主题生成图标前景色 Style
 */
export const getIconStyle = (color: string | undefined, isDark: boolean) => {
    const baseColor = color || '#3b82f6'
    if (isDark && isColorDark(baseColor)) {
        return { color: '#e5e7eb' }
    }
    return { color: baseColor }
}

/**
 * 根据图标颜色和当前主题生成图标背景色 Style
 */
export const getIconBgStyle = (color: string | undefined, isDark: boolean) => {
    const baseColor = color || '#3b82f6'
    if (isDark && isColorDark(baseColor)) {
        return { backgroundColor: 'rgba(255, 255, 255, 0.1)' }
    }
    return { backgroundColor: `${baseColor}20` }
}
