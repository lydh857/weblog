package com.blog.infra.lucene;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Lucene 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "lucene")
public class LuceneProperties {

    /** 索引存储目录 */
    private String indexDir = "./lucene-index";

    /** 最大搜索结果数 */
    private int maxResults = 100;

    /** RAM Buffer 大小（MB），控制内存使用 */
    private double ramBufferSizeMb = 64.0;

    /** 最大合并线程数 */
    private int maxMergeThreads = 2;

    /** 每段最大文档数（控制段合并频率） */
    private int maxMergedSegmentMb = 256;
}
