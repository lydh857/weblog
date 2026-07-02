package com.blog.content.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 保存专题目录树请求（整体替换）
 */
@Data
public class SaveCatalogsReqVO {

    @NotNull(message = "目录树不能为 null")
    private List<CatalogNode> catalogs;
}
