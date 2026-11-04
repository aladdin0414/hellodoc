export function scopeTool(tool) {
  if (tool.name === 'create_document' || tool.name === 'create_folder' || tool.name === 'list_folder_children' || tool.name === 'get_operation' || tool.name === 'reorder_folder_children') {
    return { ...tool, inputSchema: { ...tool.inputSchema,
      required: (tool.inputSchema.required ?? []).filter(name => name !== 'knowledgeBaseId') } };
  }
  const { knowledgeBaseId, ...properties } = tool.inputSchema.properties ?? {};
  return { ...tool, inputSchema: { ...tool.inputSchema, properties,
    required: (tool.inputSchema.required ?? []).filter(name => name !== 'knowledgeBaseId') } };
}
export function scopeArguments(args, knowledgeBaseId) {
  return { ...args, knowledgeBaseId };
}

const scopedTools = new Set(['list_items', 'create_item', 'create_document', 'get_operation']);
export const exposedTools = new Set([...scopedTools, 'search_documents', 'read_document', 'update_document', 'move_document', 'create_folder', 'move_folder', 'list_folder_children', 'list_knowledge_bases', 'reorder_folder_children']);
export function prepareToolArguments(name, args, knowledgeBaseId) {
  if (name === 'create_document' || name === 'create_folder' || name === 'list_folder_children' || name === 'get_operation' || name === 'reorder_folder_children') {
    return { ...args, knowledgeBaseId: args.knowledgeBaseId ?? knowledgeBaseId };
  }
  return scopedTools.has(name) ? scopeArguments(args, knowledgeBaseId) : args;
}
export function addSourceUrls(result, base) {
  if (result.isError) return result;
  for (const content of result.content ?? []) {
    if (content.type !== 'text') continue;
    try {
      const data = JSON.parse(content.text);
      const add = item => {
        if (item && typeof item.path === 'string' && /^\/kb\/\d+\/\d+$/.test(item.path)) {
          item.url = new URL(item.path, base).href;
        }
      };
      add(data);
      if (Array.isArray(data.results)) data.results.forEach(add);
      if (Array.isArray(data.children)) data.children.forEach(add);
      content.text = JSON.stringify(data);
    } catch { /* Preserve non-JSON messages. */ }
  }
  return result;
}
