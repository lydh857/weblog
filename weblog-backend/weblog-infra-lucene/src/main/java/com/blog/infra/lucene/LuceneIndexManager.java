package com.blog.infra.lucene;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Lucene 索引管理器
 * 基于 SearcherManager 实现近实时（NRT）线程安全的并发搜索与索引生命周期管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LuceneIndexManager {

    private final IndexWriter indexWriter;
    private SearcherManager searcherManager;

    @PostConstruct
    public void init() {
        try {
            this.searcherManager = new SearcherManager(indexWriter, new SearcherFactory());
            log.info("Lucene SearcherManager 初始化完成");
        } catch (IOException e) {
            log.error("初始化 Lucene SearcherManager 失败", e);
            throw new IllegalStateException("无法初始化 Lucene SearcherManager", e);
        }
    }

    /**
     * 获取近实时 IndexSearcher（增加引用计数，必须配合 releaseSearcher 释放）
     */
    public IndexSearcher acquireSearcher() throws IOException {
        if (searcherManager == null) {
            throw new IllegalStateException("Lucene SearcherManager 尚未初始化");
        }
        return searcherManager.acquire();
    }

    /**
     * 释放 IndexSearcher（减少引用计数）
     */
    public void releaseSearcher(IndexSearcher searcher) {
        if (searcherManager != null && searcher != null) {
            try {
                searcherManager.release(searcher);
            } catch (IOException e) {
                log.warn("释放 Lucene IndexSearcher 失败", e);
            }
        }
    }

    /**
     * 近实时尝试刷新 Searcher（当索引有更新时触发）
     */
    public void maybeRefresh() {
        if (searcherManager != null) {
            try {
                searcherManager.maybeRefresh();
            } catch (IOException e) {
                log.warn("刷新 Lucene SearcherManager 失败", e);
            }
        }
    }

    /**
     * 兼容旧接口（注意：调用后必须配合 releaseSearcher 释放）
     */
    @Deprecated
    public IndexSearcher getSearcher() throws IOException {
        return acquireSearcher();
    }

    /**
     * 提交索引变更并尝试刷新
     */
    public void commit() throws IOException {
        indexWriter.commit();
        maybeRefresh();
        log.debug("Lucene 索引已提交并触发 Searcher 刷新");
    }

    /**
     * 获取索引文档数
     */
    public int getDocCount() {
        return indexWriter.getDocStats().numDocs;
    }

    @PreDestroy
    public void close() {
        try {
            if (searcherManager != null) {
                searcherManager.close();
                log.info("Lucene SearcherManager 已安全关闭");
            }
        } catch (IOException e) {
            log.warn("关闭 Lucene SearcherManager 异常", e);
        }
    }
}
