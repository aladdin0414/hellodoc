import { exportDocument } from '../api/kb'

export const downloadDocumentZip = async (kbId: number, docId: number, documentName: string) => {
    const response = await exportDocument(kbId, docId)
    const blob = new Blob([response], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${documentName}.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
}
