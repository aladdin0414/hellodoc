package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbDocSearch;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbDocumentContent;
import com.nopkg.hellodoc.repositories.KbDocSearchRepository;
import com.nopkg.hellodoc.repositories.KbDocumentContentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.web.dto.search.SearchResultVO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.scheduling.annotation.Async;
import jakarta.annotation.PostConstruct;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final DataSource dataSource;
    private final KbDocSearchRepository docSearchRepository;
    private final KbDocumentRepository documentRepository;
    private final KbDocumentContentRepository contentRepository;
    private final PermissionChecker permissionChecker;

    @PersistenceContext
    private EntityManager entityManager;

    @Async
    @Transactional
    public void updateIndex(Long docId) {
        updateIndexInternal(docId);
    }

    @Transactional
    public void updateIndexSynchronously(Long docId) {
        updateIndexInternal(docId);
    }

    private void updateIndexInternal(Long docId) {
        if (docId == null) {
            return;
        }
        Optional<KbDocument> opt = documentRepository.findById(docId);
        if (opt.isEmpty()) {
            docSearchRepository.deleteByDoc_Id(docId);
            return;
        }
        KbDocument doc = opt.get();
        if (doc.getDeletedAt() != null) {
            docSearchRepository.deleteByDoc_Id(docId);
            return;
        }

        String title = normalize(doc.getName());
        String content = Boolean.TRUE.equals(doc.getIsEncrypted()) ? "" : normalize(contentRepository.findById(docId).map(KbDocumentContent::getContent).orElse(""));
        OffsetDateTime now = OffsetDateTime.now();

        if (isPostgres()) {
            entityManager.createNativeQuery(
                    "INSERT INTO kb_doc_search (doc_id, kb_id, title_tsv, content_tsv, content_plain, source_updated_at, updated_at) " +
                            "VALUES (:docId, :kbId, to_tsvector('simple', :title), to_tsvector('simple', :content), :content, :sourceUpdatedAt, :updatedAt) "
                            +
                            "ON CONFLICT (doc_id) DO UPDATE SET " +
                            "kb_id = EXCLUDED.kb_id, " +
                            "title_tsv = EXCLUDED.title_tsv, " +
                            "content_tsv = EXCLUDED.content_tsv, " +
                            "content_plain = EXCLUDED.content_plain, " +
                            "source_updated_at = EXCLUDED.source_updated_at, " +
                            "updated_at = EXCLUDED.updated_at")
                    .setParameter("docId", docId)
                    .setParameter("kbId", doc.getKb().getId())
                    .setParameter("title", title)
                    .setParameter("content", content)
                    .setParameter("sourceUpdatedAt", doc.getUpdatedAt() == null ? OffsetDateTime.parse("1970-01-01T00:00:00Z") : doc.getUpdatedAt())
                    .setParameter("updatedAt", now)
                    .executeUpdate();
            return;
        }

        KbDocSearch index = docSearchRepository.findByDoc_Id(docId).orElseGet(KbDocSearch::new);
        index.setDoc(doc);
        index.setKb(doc.getKb());
        index.setUpdatedAt(now);
        docSearchRepository.save(index);
    }

    @Transactional
    public void deleteIndex(Long docId) {
        if (docId == null) {
            return;
        }
        docSearchRepository.deleteByDoc_Id(docId);
    }

    @Transactional
    public void rebuildAllIndex() {
        List<KbDocument> docs = documentRepository.findAll().stream()
                .filter(d -> d.getDeletedAt() == null)
                .toList();
        for (KbDocument doc : docs) {
            updateIndex(doc.getId());
        }
    }

    @Transactional
    public void rebuildIndexForRestoredDoc(Long docId) {
        if (docId == null) {
            return;
        }
        documentRepository.findById(docId).ifPresent(doc -> {
            if (doc.getDeletedAt() == null) {
                updateIndex(docId);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<SearchResultVO> search(Long kbId, String query, int limit, boolean publishedOnly) {
        String q = normalize(query);
        int safeLimit = normalizeLimit(limit);
        if (!StringUtils.hasText(q) || kbId == null) {
            return List.of();
        }

        // PostgreSQL 全文搜索 + LIKE 回退（解决中文分词不完整问题）
        if (isPostgres()) {
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT doc_id, ts_rank(title_tsv, to_tsquery('simple', :tsq)) + ts_rank(content_tsv, to_tsquery('simple', :tsq)) * 0.5 AS rank "
                            +
                            "FROM kb_doc_search " +
                            "WHERE kb_id = :kbId AND (title_tsv @@ to_tsquery('simple', :tsq) OR content_tsv @@ to_tsquery('simple', :tsq)) "
                            +
                            "ORDER BY rank DESC LIMIT :limit")
                    .setParameter("kbId", kbId)
                    .setParameter("tsq", buildTsQuery(q))
                    .setParameter("limit", safeLimit)
                    .getResultList();
            List<SearchResultVO> tsResults = buildResultsFromRankRows(rows, q);

            // LIKE 回退：simple 分词器对中文子串匹配不完整，补充文本匹配
            List<KbDocument> allDocs = documentRepository.findByKbIdAndDeletedAtIsNull(kbId);
            if (publishedOnly) {
                allDocs = allDocs.stream()
                        .filter(d -> d.getStatus() == com.nopkg.hellodoc.enums.DocStatus.PUBLISHED)
                        .toList();
            }
            List<SearchResultVO> likeResults = buildResultsFromDocs(allDocs, q, safeLimit, null);

            // 合并去重，全文搜索结果优先
            java.util.LinkedHashMap<Long, SearchResultVO> merged = new java.util.LinkedHashMap<>();
            for (SearchResultVO r : tsResults) {
                merged.put(r.getDocId(), r);
            }
            for (SearchResultVO r : likeResults) {
                merged.putIfAbsent(r.getDocId(), r);
            }

            // 公开搜索需过滤未发布文档
            if (publishedOnly) {
                java.util.Set<Long> publishedDocIds = allDocs.stream()
                        .map(KbDocument::getId)
                        .collect(Collectors.toSet());
                return merged.values().stream()
                        .filter(r -> publishedDocIds.contains(r.getDocId()))
                        .limit(safeLimit).toList();
            }
            return merged.values().stream().limit(safeLimit).toList();
        }

        List<KbDocument> docs = documentRepository.findByKbIdAndDeletedAtIsNull(kbId);
        if (publishedOnly) {
            docs = docs.stream()
                    .filter(d -> d.getStatus() == com.nopkg.hellodoc.enums.DocStatus.PUBLISHED)
                    .toList();
        }
        return buildResultsFromDocs(docs, q, safeLimit, null);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<SearchResultVO> searchAll(Long userId, String query, int limit) {
        String q = normalize(query);
        int safeLimit = normalizeLimit(limit);
        if (!StringUtils.hasText(q)) {
            return List.of();
        }

        if (isPostgres()) {
            return searchAllPostgres(userId, q, safeLimit);
        }

        List<KbDocument> docs = documentRepository.findAll().stream()
                .filter(d -> d.getDeletedAt() == null && !Boolean.TRUE.equals(d.getIsEncrypted()))
                .filter(d -> permissionChecker.hasDocRole(userId, d.getId(), com.nopkg.hellodoc.enums.DocRole.VIEWER))
                .toList();
        return buildResultsFromDocs(docs, q, safeLimit, null);
    }

    private List<SearchResultVO> searchAllPostgres(Long userId, String query, int limit) {
        String pattern = "%" + query.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String tsQuery = buildTsQuery(query);
        boolean hasTs = StringUtils.hasText(tsQuery);
        String fresh = "(s.source_updated_at IS NOT NULL AND s.content_plain IS NOT NULL AND "
                + "(d.updated_at IS NULL OR s.source_updated_at >= d.updated_at))";
        String body = "(CASE WHEN " + fresh + " THEN s.content_plain "
                + "WHEN c.content IS NULL THEN '' ELSE convert_from(lo_get(c.content), 'UTF8') END)";
        String titleMatch = "(lower(d.name) LIKE :pattern ESCAPE '!')";
        String bodyMatch = "((" + fresh + " AND lower(s.content_plain) LIKE :pattern ESCAPE '!') OR "
                + "(NOT " + fresh + " AND lower(" + body + ") LIKE :pattern ESCAPE '!'))";
        String titleTs = "(CASE WHEN " + fresh + " THEN coalesce(s.title_tsv, ''::tsvector) "
                + "ELSE to_tsvector('simple', coalesce(d.name, '')) END)";
        String bodyTs = "(CASE WHEN " + fresh + " THEN coalesce(s.content_tsv, ''::tsvector) "
                + "ELSE to_tsvector('simple', " + body + ") END)";
        String fullText = hasTs ? "(" + titleTs + " @@ to_tsquery('simple', :tsq) OR "
                + bodyTs + " @@ to_tsquery('simple', :tsq))" : "false";
        String rank = "(CASE WHEN " + titleMatch + " THEN 100.0 ELSE 0 END + CASE WHEN "
                + bodyMatch + " THEN 10.0 ELSE 0 END";
        if (hasTs) rank += " + 2.0 * ts_rank(" + titleTs + ", to_tsquery('simple', :tsq))"
                + " + ts_rank(" + bodyTs + ", to_tsquery('simple', :tsq))";
        rank += ")";
        String eligible = "d.deleted_at IS NULL AND coalesce(d.is_encrypted, false) = false";
        String indexedFullText = hasTs ? "(s.title_tsv @@ to_tsquery('simple', :tsq) OR s.content_tsv @@ to_tsquery('simple', :tsq))" : "false";
        String joins = " LEFT JOIN kb_doc_search s ON s.doc_id = d.id LEFT JOIN kb_document_content c ON c.doc_id = d.id ";
        String sql = "WITH candidate_ids AS ("
                + "SELECT d.id FROM kb_document d WHERE " + eligible + " AND " + titleMatch
                + " UNION SELECT d.id FROM kb_doc_search s JOIN kb_document d ON d.id = s.doc_id WHERE " + eligible
                + " AND " + fresh + " AND (lower(s.content_plain) LIKE :pattern ESCAPE '!' OR " + indexedFullText + ")"
                + " UNION SELECT d.id FROM kb_document d " + joins + "WHERE " + eligible + " AND NOT " + fresh
                + " AND (" + bodyMatch + " OR " + fullText + ")"
                + ") SELECT d.id, " + rank + " AS rank FROM candidate_ids candidate JOIN kb_document d ON d.id = candidate.id "
                + joins + " ORDER BY rank DESC, d.id ASC";
        int batchSize = Math.min(512, Math.max(64, limit * 4));
        int offset = 0;
        List<SearchResultVO> results = new ArrayList<>();
        while (results.size() < limit) {
            var statement = entityManager.createNativeQuery(sql).setParameter("pattern", pattern);
            if (hasTs) statement.setParameter("tsq", tsQuery);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = statement.setFirstResult(offset).setMaxResults(batchSize).getResultList();
            if (rows.isEmpty()) break;
            for (Object[] row : rows) {
                Long id = toLong(row[0]);
                if (id == null || !permissionChecker.hasDocRole(userId, id, com.nopkg.hellodoc.enums.DocRole.VIEWER)) continue;
                documentRepository.findById(id).filter(doc -> doc.getDeletedAt() == null && !Boolean.TRUE.equals(doc.getIsEncrypted()))
                        .ifPresent(doc -> results.add(toResult(doc, query, toDouble(row[1]))));
                if (results.size() == limit) break;
            }
            if (rows.size() < batchSize || offset > Integer.MAX_VALUE - rows.size()) break;
            offset += rows.size();
        }
        return results;
    }

    public String highlightContent(String content, String query) {
        String src = content == null ? "" : content;
        List<String> tokens = splitTokens(normalize(query));
        String result = src;
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            Pattern p = Pattern.compile(Pattern.quote(token), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            result = p.matcher(result).replaceAll("<mark>" + token + "</mark>");
        }
        return result;
    }

    private List<SearchResultVO> buildResultsFromRankRows(List<Object[]> rows, String query) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> docIds = new ArrayList<>();
        Map<Long, Double> scores = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            Long docId = toLong(row[0]);
            Double score = toDouble(row[1]);
            if (docId == null) {
                continue;
            }
            docIds.add(docId);
            scores.put(docId, score);
        }
        if (docIds.isEmpty()) {
            return List.of();
        }
        Map<Long, KbDocument> docs = documentRepository.findAllById(docIds).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(KbDocument::getId, d -> d));

        List<SearchResultVO> results = new ArrayList<>();
        for (Long docId : docIds) {
            KbDocument doc = docs.get(docId);
            if (doc == null) {
                continue;
            }
            results.add(toResult(doc, query, scores.getOrDefault(docId, 0.0)));
        }
        return results;
    }

    private List<SearchResultVO> buildResultsFromDocs(List<KbDocument> docs, String query, int limit, Long userId) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        String qLower = query.toLowerCase(Locale.ROOT);
        List<ScoredDoc> scored = new ArrayList<>();
        for (KbDocument doc : docs) {
            if (doc == null) {
                continue;
            }
            String title = normalize(doc.getName());
            String content = normalize(
                    contentRepository.findById(doc.getId()).map(KbDocumentContent::getContent).orElse(""));

            int titleHits = countOccurrences(title.toLowerCase(Locale.ROOT), qLower);
            int contentHits = countOccurrences(content.toLowerCase(Locale.ROOT), qLower);
            if (titleHits == 0 && contentHits == 0) {
                continue;
            }
            double score = titleHits * 2.0 + contentHits * 1.0;
            scored.add(new ScoredDoc(doc, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredDoc::score).reversed());
        return scored.stream()
                .limit(limit)
                .map(s -> toResult(s.doc(), query, s.score()))
                .toList();
    }

    private SearchResultVO toResult(KbDocument doc, String query, Double score) {
        SearchResultVO vo = new SearchResultVO();
        vo.setDocId(doc.getId());
        vo.setDocName(doc.getName());
        vo.setDocType(doc.getType() == null ? null : doc.getType().getValue());
        vo.setStatus(doc.getStatus() == null ? null : doc.getStatus().getValue());
        vo.setKbId(doc.getKb() != null ? doc.getKb().getId() : null);
        vo.setKbTitle(doc.getKb() != null ? doc.getKb().getTitle() : "");
        vo.setScore(score != null ? score : 0.0);

        String content = normalize(
                contentRepository.findById(doc.getId()).map(KbDocumentContent::getContent).orElse(""));

        String snippet = buildSnippet(content, query, 100);
        // 确保 snippet 始终有值,如果为空则使用文档开头
        if (!StringUtils.hasText(snippet) && StringUtils.hasText(content)) {
            snippet = truncate(content, 100);
        }
        vo.setSnippet(snippet);
        vo.setHighlightedTitle(highlightContent(normalize(doc.getName()), query));
        vo.setHighlightedSnippet(highlightContent(snippet, query));
        return vo;
    }

    private String buildSnippet(String content, String query, int maxLen) {
        String src = content == null ? "" : content;
        if (!StringUtils.hasText(src)) {
            return "";
        }
        String q = normalize(query);
        if (!StringUtils.hasText(q)) {
            return truncate(src, maxLen);
        }

        // 尝试查找完整查询词
        int idx = indexOfIgnoreCase(src, q);

        // 如果找不到完整查询词,尝试查找各个分词
        if (idx < 0) {
            List<String> tokens = splitTokens(q);
            for (String token : tokens) {
                if (StringUtils.hasText(token)) {
                    idx = indexOfIgnoreCase(src, token);
                    if (idx >= 0) {
                        q = token; // 使用找到的分词
                        break;
                    }
                }
            }
        }

        // 如果分词也没找到，尝试逐个字符匹配（针对中文等紧凑语言）
        if (idx < 0 && q.length() > 1) {
            for (int i = 0; i < q.length(); i++) {
                String charStr = String.valueOf(q.charAt(i));
                if (StringUtils.hasText(charStr) && !Character.isWhitespace(q.charAt(i))) {
                    int charIdx = indexOfIgnoreCase(src, charStr);
                    if (charIdx >= 0) {
                        idx = charIdx;
                        q = charStr;
                        break;
                    }
                }
            }
        }

        // 如果还是找不到,返回文档开头
        if (idx < 0) {
            return truncate(src, maxLen);
        }

        // 提取关键词周围的上下文
        // contextBefore 动态计算：取 maxLen 的 1/4 作为前置上下文，但不超过 12 个字符，确保关键词在前端可见区域
        int targetContextBefore = maxLen / 4;
        int contextBefore = Math.min(12, targetContextBefore);
        int contextAfter = Math.max(0, maxLen - contextBefore - q.length());
        int start = Math.max(0, idx - contextBefore);
        int end = Math.min(src.length(), idx + q.length() + Math.max(0, contextAfter));
        return truncate(src.substring(start, end), maxLen);
    }

    private int indexOfIgnoreCase(String src, String needle) {
        return src.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private int countOccurrences(String text, String q) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(q)) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while (true) {
            int found = text.indexOf(q, idx);
            if (found < 0) {
                break;
            }
            count++;
            idx = found + q.length();
        }
        return count;
    }

    private String buildTsQuery(String query) {
        List<String> tokens = splitTokens(query);
        if (tokens.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String rawToken : tokens) {
            String sanitized = sanitizeTsToken(rawToken);
            if (StringUtils.hasText(sanitized)) {
                String[] subTokens = sanitized.split("\\s+");
                for (String sub : subTokens) {
                    if (StringUtils.hasText(sub)) {
                        parts.add(sub + ":*");
                    }
                }
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        return String.join(" & ", parts);
    }

    private List<String> splitTokens(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] raw = trimmed.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String r : raw) {
            String t = sanitizeToken(r);
            if (StringUtils.hasText(t)) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private String sanitizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        String t = token.trim();
        if (t.isEmpty()) {
            return "";
        }
        t = t.replace('\u0000', ' ');
        return t;
    }

    private String sanitizeTsToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        String t = token.replace("'", "''");
        t = t.replaceAll("[&|!():*\\\\]", " ");
        return t.trim();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLen));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private String normalize(String s) {
        return s == null ? "" : s;
    }

    private Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private Double toDouble(Object v) {
        if (v == null) {
            return 0.0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private boolean isPostgresCached;

    @PostConstruct
    public void init() {
        try (var c = dataSource.getConnection()) {
            String name = c.getMetaData().getDatabaseProductName();
            this.isPostgresCached = name != null && name.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (Exception e) {
            this.isPostgresCached = false;
        }
    }

    private boolean isPostgres() {
        return isPostgresCached;
    }

    private record ScoredDoc(KbDocument doc, double score) {
    }
}
