# MCP Reconnection and Folder Support Implementation Plan

Goal: Recover expired sessions, create documents in authorized folders or KB root, and discover accessible KBs and children without breaking existing item clients.

1. Test and implement one shared session reconnection, triggered only by an explicit session-not-found response; preserve identical write arguments and do not replay other failures.
2. Test and implement parentId plus legacy itemId, root support, cross-KB selection and permission checks while retaining original request fingerprints for old requests.
3. Test and implement paginated knowledge-base and folder browsing with filtering before pagination, including direct document permissions.
4. Update bridge schemas and guide; verify real PostgreSQL, MCP bridge concurrency and API regression tests.
