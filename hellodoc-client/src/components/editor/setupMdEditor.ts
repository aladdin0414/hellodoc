import { config as mdEditorConfig } from 'md-editor-v3'
import type { CodeMirrorExtension } from 'md-editor-v3'
import mermaid from 'mermaid/dist/mermaid.core.mjs'

mdEditorConfig({
  editorExtensions: {
    mermaid: {
      instance: mermaid
    }
  },
  codeMirrorExtensions: (extensions: Array<CodeMirrorExtension>) => {
    // Disable default linkShortener to keep long URLs visible in editor content.
    return extensions.filter(ext => ext.type !== 'linkShortener')
  }
})
