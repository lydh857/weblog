package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 不带逻辑删除的实体基类（适用于分类、标签等轻量元数据）
 * - 主键自增
 * - 自动填充 create_time / update_time
 * - 物理删除
 */
@Data
public abstract class BaseEntityNoDelete implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
