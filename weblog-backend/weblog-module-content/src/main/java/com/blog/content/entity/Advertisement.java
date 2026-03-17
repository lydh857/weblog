package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.blog.common.entity.BaseEntity;
import com.blog.infra.security.xss.XssRawDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 广告实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_advertisement")
public class Advertisement extends BaseEntity {

    private String title;

    /** 广告类型: image/code */
    private String type;

    /** 广告内容（图片URL或HTML代码） */
    @JsonDeserialize(using = XssRawDeserializer.class)
    private String content;

    /** 广告信息（展示在图片上，未设置则不显示） */
    private String adInfo;

    /** 拟态广告文案（用于文章列表拟态卡） */
    private String mimicContent;

    /** 跳转链接 */
    private String linkUrl;

    /** 广告位置: top/middle/bottom/sidebar */
    private String position;

    /** 列表拟态卡片插入位置（在第 N 篇文章后插入） */
    private Integer insertAfter;

    /** 是否允许用户关闭 */
    private Boolean closable;

    /** 是否自动轮播（同位置多广告时） */
    private Boolean autoRotate;

    /** 轮播间隔（秒） */
    private Integer rotateIntervalSec;

    /** 广告申请者用户ID */
    private Long advertiserId;

    /** 状态: pending/approved/rejected/active/expired */
    private String status;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Integer clickCount;
    private Integer weight;

    /** 审核备注（如拒绝原因），来自系统配置映射，不落库 */
    @TableField(exist = false)
    private String reviewReason;

    /** 是否开启申请坑位（来自系统配置，不落库） */
    @TableField(exist = false)
    private Boolean pitEnabled;

    /** 申请关联的目标坑位广告ID（来自系统配置映射，不落库） */
    @TableField(exist = false)
    private Long pitAdId;

    /** 坑位序号（按广告位内排序得到，不落库） */
    @TableField(exist = false)
    private Integer pitIndex;

    /** 申请关联坑位广告标题（仅展示，不落库） */
    @TableField(exist = false)
    private String pitTitle;

    /** 申请用户邮箱（仅管理端展示，不落库） */
    @TableField(exist = false)
    private String advertiserEmail;

    /** 申请用户昵称（仅管理端展示，不落库） */
    @TableField(exist = false)
    private String advertiserNickname;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAdInfo() {
        return adInfo;
    }

    public void setAdInfo(String adInfo) {
        this.adInfo = adInfo;
    }

    public String getMimicContent() {
        return mimicContent;
    }

    public void setMimicContent(String mimicContent) {
        this.mimicContent = mimicContent;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Integer getInsertAfter() {
        return insertAfter;
    }

    public void setInsertAfter(Integer insertAfter) {
        this.insertAfter = insertAfter;
    }

    public Boolean getClosable() {
        return closable;
    }

    public void setClosable(Boolean closable) {
        this.closable = closable;
    }

    public Boolean getAutoRotate() {
        return autoRotate;
    }

    public void setAutoRotate(Boolean autoRotate) {
        this.autoRotate = autoRotate;
    }

    public Integer getRotateIntervalSec() {
        return rotateIntervalSec;
    }

    public void setRotateIntervalSec(Integer rotateIntervalSec) {
        this.rotateIntervalSec = rotateIntervalSec;
    }

    public Long getAdvertiserId() {
        return advertiserId;
    }

    public void setAdvertiserId(Long advertiserId) {
        this.advertiserId = advertiserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getClickCount() {
        return clickCount;
    }

    public void setClickCount(Integer clickCount) {
        this.clickCount = clickCount;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public Boolean getPitEnabled() {
        return pitEnabled;
    }

    public void setPitEnabled(Boolean pitEnabled) {
        this.pitEnabled = pitEnabled;
    }

    public Long getPitAdId() {
        return pitAdId;
    }

    public void setPitAdId(Long pitAdId) {
        this.pitAdId = pitAdId;
    }

    public Integer getPitIndex() {
        return pitIndex;
    }

    public void setPitIndex(Integer pitIndex) {
        this.pitIndex = pitIndex;
    }

    public String getPitTitle() {
        return pitTitle;
    }

    public void setPitTitle(String pitTitle) {
        this.pitTitle = pitTitle;
    }

    public String getAdvertiserEmail() {
        return advertiserEmail;
    }

    public void setAdvertiserEmail(String advertiserEmail) {
        this.advertiserEmail = advertiserEmail;
    }

    public String getAdvertiserNickname() {
        return advertiserNickname;
    }

    public void setAdvertiserNickname(String advertiserNickname) {
        this.advertiserNickname = advertiserNickname;
    }
}
