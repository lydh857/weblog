package com.blog.infra.lucene;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Lucene 索引管理器
 * 封装 IndexWriter 和 NRT IndexSearcher 的获取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LuceneIndexManager {

    private final IndexWriter indexWriter;
    private volatile DirectoryReader currentReader;

    /**
     * 获取 NRT IndexSearcher（近实时搜索）
     */
    public synchronized IndexSearcher getSearcher() throws IOException {
        if (currentReader == null) {
            currentReader = DirectoryReader.open(indexWriter);
        } else {
            DirectoryReader newReader = DirectoryReader.openIfChanged(currentReader, indexWriter);
            if (newReader != null) {
                currentReader.close();
                currentReader = newReader;
            }
        }
        return new IndexSearcher(currentReader);
    }

    /**
     * 提交索引变更
     */
    public void commit() throws IOException {
        indexWriter.commit();
        log.debug("Lucene 索引已提交");
    }

    /**
     * 获取索引文档数
     */
    public int getDocCount() {
        return indexWriter.getDocStats().numDocs;
    }
}
