export const isVideoUrl = (href: string) => {
    const raw = (href || '').trim()
    if (!raw) return false
    try {
        const u = new URL(raw, window.location.href)
        const path = (u.pathname || '').toLowerCase()
        return /\.(mp4|webm|ogg|mov|m4v)$/.test(path)
    } catch {
        const stripped = raw.split(/[?#]/)[0]?.toLowerCase() || ''
        return /\.(mp4|webm|ogg|mov|m4v)$/.test(stripped)
    }
}

export const enhanceVideoLinksInElement = (root: HTMLElement) => {
    const links = Array.from(root.querySelectorAll('a[href]')) as HTMLAnchorElement[]
    const videoLinks = links.filter(a => !a.closest('.ow-media-embed') && isVideoUrl(a.getAttribute('href') || ''))
    if (videoLinks.length === 0) return

    for (const a of videoLinks) {
        const href = a.getAttribute('href') || ''
        const wrap = document.createElement('div')
        wrap.className = 'ow-media-embed'

        const video = document.createElement('video')
        video.controls = true
        video.preload = 'metadata'
        video.setAttribute('playsinline', '')
        video.src = href
        wrap.appendChild(video)

        const link = document.createElement('a')
        link.href = href
        link.target = '_blank'
        link.rel = 'noopener noreferrer'
        link.textContent = (a.textContent || '').trim() || href
        wrap.appendChild(link)

        a.replaceWith(wrap)
    }
}
