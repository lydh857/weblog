package com.blog.content.dto;

import lombok.Data;

import java.util.List;

/**
 * 专题目录树节点（前后端传输用）
 */
@Data
public class CatalogNode {

    /** 节点 ID，新建节点时为 null */
    private Long id;

    /** 关联文章 ID，null 表示纯目录节点 */
    private Long articleId;

    /** 节点标题 */
    private String title;

    /** 层级深度，从 1 开始 */
    private Integer level;

    /** 父节点 ID，0 表示顶级 */
    private Long parentId;

    /** 同级排序值 */
    private Integer sort;

    /** 子节点列表（树形结构） */
    private List<CatalogNode> children;
}
