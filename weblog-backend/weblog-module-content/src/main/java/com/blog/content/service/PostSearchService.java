package com.blog.content.service;

import com.blog.content.dto.SearchHitMeta;
import com.blog.content.dto.SearchResult;
import com.blog.content.mapper.PostMapper;
import com.blog.infra.lucene.LuceneIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章全文检索服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSearchService {

    private final LuceneIndexManager indexManager;
    private final Analyzer analyzer;
    private final PostMapper postMapper;

    /**
     * 全文搜索（标题+内容+摘要）
     *
     * @param keyword    搜索关键词
     * @param pageNum    页码（从1开始）
     * @param pageSize   每页大小
     * @return 搜索结果（含高亮）
     */
    public SearchResult search(String keyword, int pageNum, int pageSize) {
        SearchResult result = new SearchResult();
        result.setTotal(0);
        result.setHits(new ArrayList<>());

        if (keyword == null || keyword.isBlank()) {
            return result;
        }

        // 过滤 Lucene 特殊字符
        String safeKeyword = escapeLuceneSpecialChars(keyword.trim());
        if (safeKeyword.isEmpty()) {
            return result;
        }

        try {
            // 多字段查询（标题权重更高）
            String[] fields = {"title", "content", "summary"};
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.OR);
            Query query = parser.parse(safeKeyword);

            // 标题 boost
            BooleanQuery.Builder boosted = new BooleanQuery.Builder();
            boosted.add(new BoostQuery(
                    new MultiFieldQueryParser(new String[]{"title"}, analyzer).parse(safeKeyword), 3.0f),
                    BooleanClause.Occur.SHOULD);
            boosted.add(query, BooleanClause.Occur.SHOULD);
            Query finalQuery = boosted.build();

            IndexSearcher searcher = indexManager.getSearcher();
            int start = (pageNum - 1) * pageSize;
            int maxHits = Math.min(start + pageSize, 1000);
            if (maxHits < 1) {
                maxHits = 1;
            }
            TopDocs topDocs = searcher.search(finalQuery, maxHits);
            result.setTotal(topDocs.totalHits.value);

            // 高亮器
            QueryScorer scorer = new QueryScorer(finalQuery);
            Highlighter highlighter = new Highlighter(
                    new SimpleHTMLFormatter("<em>", "</em>"), scorer);
            highlighter.setTextFragmenter(new SimpleFragmenter(150));

            // 分页
            int end = Math.min(start + pageSize, (int) topDocs.totalHits.value);
            List<SearchResult.SearchHit> hits = new ArrayList<>();

            for (int i = start; i < end && i < topDocs.scoreDocs.length; i++) {
                Document doc = searcher.storedFields().document(topDocs.scoreDocs[i].doc);
                SearchResult.SearchHit hit = new SearchResult.SearchHit();
                hit.setId(Long.parseLong(doc.get("id")));
                hit.setSlug(doc.get("slug"));
                hit.setTitle(doc.get("title"));
                hit.setSummary(doc.get("summary"));

                String catId = doc.get("categoryId");
                if (catId != null) hit.setCategoryId(Long.parseLong(catId));
                String authId = doc.get("authorId");
                if (authId != null) hit.setAuthorId(Long.parseLong(authId));

                // 高亮标题
                String hlTitle = highlighter.getBestFragment(analyzer, "title", doc.get("title"));
                hit.setHighlightTitle(hlTitle != null ? hlTitle : doc.get("title"));

                // 高亮内容片段
                String contentText = doc.get("content");
                if (contentText != null && !contentText.isEmpty()) {
                    String hlContent = highlighter.getBestFragment(analyzer, "content", contentText);
                    hit.setHighlightContent(hlContent != null ? hlContent : truncate(contentText, 150));
                }

                hits.add(hit);
            }

            enrichSearchHitMeta(hits);
            result.setHits(hits);
        } catch (ParseException e) {
            log.warn("搜索关键词解析失败: {}", keyword, e);
        } catch (IOException | InvalidTokenOffsetsException e) {
            log.error("搜索执行失败", e);
        }

        return result;
    }

    /**
     * 过滤 Lucene 特殊字符，防止查询注入
     */
    private String escapeLuceneSpecialChars(String input) {
        return QueryParser.escape(input);
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private void enrichSearchHitMeta(List<SearchResult.SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }

        List<Long> ids = hits.stream()
                .map(SearchResult.SearchHit::getId)
                .filter(id -> id != null && id > 0)
                .toList();

        if (ids.isEmpty()) {
            return;
        }

        List<SearchHitMeta> metaList = postMapper.selectSearchHitMetaByIds(ids);
        if (metaList == null || metaList.isEmpty()) {
            return;
        }

        Map<Long, SearchHitMeta> metaById = metaList.stream()
                .filter(meta -> meta.getId() != null)
                .collect(Collectors.toMap(SearchHitMeta::getId, Function.identity(), (first, second) -> first));

        for (SearchResult.SearchHit hit : hits) {
            if (hit.getId() == null) {
                continue;
            }

            SearchHitMeta meta = metaById.get(hit.getId());
            if (meta == null) {
                continue;
            }

            hit.setCategoryName(meta.getCategoryName());
            hit.setSubCategoryName(meta.getSubCategoryName());
            hit.setAuthorNickname(meta.getAuthorNickname());
            hit.setViewCount(meta.getViewCount());
            hit.setLikeCount(meta.getLikeCount());
            hit.setCollectCount(meta.getCollectCount());
            hit.setCommentCount(meta.getCommentCount());
            hit.setCreateTime(meta.getCreateTime());
        }
    }
}
