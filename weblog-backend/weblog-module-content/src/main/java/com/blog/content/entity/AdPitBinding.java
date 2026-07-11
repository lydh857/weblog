package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告申请坑位占用
 */
@Data
@TableName("t_ad_pit_binding")
public class AdPitBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户申请广告ID */
    private Long applyAdId;

    /** 管理员坑位广告ID */
    private Long pitAdId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplyAdId() {
        return applyAdId;
    }

    public void setApplyAdId(Long applyAdId) {
        this.applyAdId = applyAdId;
    }

    public Long getPitAdId() {
        return pitAdId;
    }

    public void setPitAdId(Long pitAdId) {
        this.pitAdId = pitAdId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
