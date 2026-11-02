export function scopeTool(tool) {
  const { knowledgeBaseId, ...properties } = tool.inputSchema.properties ?? {};
  return { ...tool, inputSchema: { ...tool.inputSchema, properties,
    required: (tool.inputSchema.required ?? []).filter(name => name !== 'knowledgeBaseId') } };
}
export function scopeArguments(args, knowledgeBaseId) {
  return { ...args, knowledgeBaseId };
}
