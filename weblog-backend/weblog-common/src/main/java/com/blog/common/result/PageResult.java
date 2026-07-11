package com.blog.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private long current;
    private long size;
    private long total;
    private long pages;
    private List<T> records;

    public static <T> PageResult<T> of(long current, long size, long total, List<T> records) {
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(current, size, total, pages, records);
    }

    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(current, size, 0, 0, Collections.emptyList());
    }
}
