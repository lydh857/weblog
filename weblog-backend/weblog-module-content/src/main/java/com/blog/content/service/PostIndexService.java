package com.blog.content.service;

import com.blog.infra.lucene.LuceneIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 文章索引服务
 * 负责将文章添加到 Lucene 索引、从索引中删除、重建索引等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostIndexService {

    private final LuceneIndexManager indexManager;
    private final IndexWriter indexWriter;

    /**
     * 批量索引队列
     */
    private final BlockingQueue<IndexDocument> batchQueue = new ArrayBlockingQueue<>(1000);

    /**
     * 索引文档封装
     */
    private static class IndexDocument {
        final Long id;
        final String slug;
        final String title;
        final String content;
        final String summary;
        final Long categoryId;
        final Long authorId;

        IndexDocument(Long id, String slug, String title, String content, String summary, Long categoryId, Long authorId) {
            this.id = id;
            this.slug = slug;
            this.title = title;
            this.content = content;
            this.summary = summary;
            this.categoryId = categoryId;
            this.authorId = authorId;
        }
    }

    /**
     * 添加文章到索引
     */
    public void indexPost(Long id, String slug, String title, String content, String summary, Long categoryId, Long authorId) {
        try {
            Document doc = createDocument(id, slug, title, content, summary, categoryId, authorId);
            indexWriter.updateDocument(new Term("id", String.valueOf(id)), doc);
            log.debug("文章索引成功: id={}", id);
        } catch (IOException e) {
            log.error("文章索引失败: id={}", id, e);
            throw new RuntimeException("文章索引失败", e);
        }
    }

    /**
     * 添加文章到索引（简化参数版本）
     */
    public void indexPost(Long id, String slug, String title, String content, String summary) {
        indexPost(id, slug, title, content, summary, null, null);
    }

    /**
     * 从索引中删除文章
     */
    public void deleteIndex(Long id) {
        try {
            indexWriter.deleteDocuments(new Term("id", String.valueOf(id)));
            log.debug("文章索引删除成功: id={}", id);
        } catch (IOException e) {
            log.error("文章索引删除失败: id={}", id, e);
            throw new RuntimeException("文章索引删除失败", e);
        }
    }

    /**
     * 删除所有索引
     */
    public void deleteAll() {
        try {
            indexWriter.deleteAll();
            log.info("所有文章索引已删除");
        } catch (IOException e) {
            log.error("删除所有索引失败", e);
            throw new RuntimeException("删除所有索引失败", e);
        }
    }

    /**
     * 将文章添加到批量队列
     */
    public void indexPostBatch(Long id, String slug, String title, String content, String summary) {
        indexPostBatch(id, slug, title, content, summary, null, null);
    }

    /**
     * 将文章添加到批量队列（完整参数）
     */
    public void indexPostBatch(Long id, String slug, String title, String content, String summary, Long categoryId, Long authorId) {
        try {
            batchQueue.offer(new IndexDocument(id, slug, title, content, summary, categoryId, authorId), 100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("批量索引队列已满");
        }
    }

    /**
     * 提交批量索引
     */
    public void commitBatch() {
        List<IndexDocument> docs = new ArrayList<>();
        batchQueue.drainTo(docs);

        if (docs.isEmpty()) {
            return;
        }

        try {
            for (IndexDocument doc : docs) {
                Document luceneDoc = createDocument(doc.id, doc.slug, doc.title, doc.content, doc.summary, doc.categoryId, doc.authorId);
                indexWriter.addDocument(luceneDoc);
            }
            indexWriter.commit();
            log.info("批量索引提交成功: {} 条", docs.size());
        } catch (IOException e) {
            log.error("批量索引提交失败", e);
            throw new RuntimeException("批量索引提交失败", e);
        }
    }

    /**
     * 创建 Lucene 文档
     */
    private Document createDocument(Long id, String slug, String title, String content, String summary, Long categoryId, Long authorId) {
        Document doc = new Document();
        doc.add(new StringField("id", String.valueOf(id), Field.Store.YES));
        doc.add(new StringField("slug", slug != null ? slug : "", Field.Store.YES));
        doc.add(new TextField("title", title != null ? title : "", Field.Store.YES));
        doc.add(new TextField("content", content != null ? content : "", Field.Store.YES));
        doc.add(new TextField("summary", summary != null ? summary : "", Field.Store.YES));

        if (categoryId != null) {
            doc.add(new StringField("categoryId", String.valueOf(categoryId), Field.Store.YES));
        }
        if (authorId != null) {
            doc.add(new StringField("authorId", String.valueOf(authorId), Field.Store.YES));
        }

        return doc;
    }
}
