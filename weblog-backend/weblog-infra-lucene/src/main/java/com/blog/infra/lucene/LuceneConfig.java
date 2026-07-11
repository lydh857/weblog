package com.blog.infra.lucene;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Lucene 配置
 * 使用 MMapDirectory + SmartChineseAnalyzer（中文分词）
 */
@Slf4j
@Configuration
public class LuceneConfig {

    @Autowired
    private LuceneProperties luceneProperties;

    @Bean(destroyMethod = "close")
    public Directory luceneDirectory() throws IOException {
        Path indexPath = Paths.get(luceneProperties.getIndexDir());
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath);
        }
        log.info("Lucene 索引目录: {}", indexPath.toAbsolutePath());
        return MMapDirectory.open(indexPath);
    }

    @Bean
    public Analyzer luceneAnalyzer() {
        return new SmartChineseAnalyzer();
    }

    @Bean(destroyMethod = "close")
    public IndexWriter indexWriter(Directory directory, Analyzer analyzer) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        config.setRAMBufferSizeMB(luceneProperties.getRamBufferSizeMb());
        // 合并策略优化
        config.setUseCompoundFile(true);
        return new IndexWriter(directory, config);
    }
}
