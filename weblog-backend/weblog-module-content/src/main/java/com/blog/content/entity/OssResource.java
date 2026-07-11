package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OSS 资源管理实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_oss_resource")
public class OssResource extends BaseEntity {

    /** 原始文件名 */
    private String fileName;

    /** OSS 对象 Key */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型（jpg/png/webp 等） */
    private String fileType;

    /** MIME 类型 */
    private String mimeType;

    /** CDN 访问 URL */
    private String url;

    /** 上传者 ID */
    private Long uploaderId;

    /** 用途：post / avatar / ad / other / content / cover / ad_apply */
    private String usageType;
}
