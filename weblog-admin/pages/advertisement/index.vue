<template>
  <div class="ad-page">
    <div class="page-header">
      <h2>广告管理</h2>
      <div v-if="contentTab === 'list'" class="filter-bar">
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 130px" @change="handleFilterChange">
          <el-option label="待审核" value="pending" />
          <el-option label="投放中" value="active" />
          <el-option label="已拒绝" value="rejected" />
          <el-option label="已过期" value="expired" />
        </el-select>
        <el-select v-model="filterPosition" placeholder="位置" clearable style="width: 130px" @change="handleFilterChange">
          <el-option label="首页左侧" value="home_left" />
          <el-option label="文章顶部" value="post_top" />
          <el-option label="文章底部" value="post_bottom" />
          <el-option label="文章列表拟态卡" value="post_list_card" />
        </el-select>
      </div>
      <div class="header-actions">
        <template v-if="selectedIds.length > 0">
          <span class="selection-count">已选 {{ selectedIds.length }} 条</span>
          <el-dropdown trigger="hover" @command="handleBatchCommand">
            <el-button type="primary" size="small">
              批量操作 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="active" :disabled="disableBatchActivate">上架</el-dropdown-item>
                <el-dropdown-item command="expired">下架</el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                  <span style="color: var(--el-color-danger)">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <el-tooltip content="控制用户端广告申请入口" placement="top">
          <div class="apply-switch">
            <span class="switch-label">申请入口</span>
            <el-switch v-model="applyEnabled" :loading="switchLoading" @change="handleApplySwitchChange" />
          </div>
        </el-tooltip>
        <el-button @click="showTrashBox = true"><el-icon><Delete /></el-icon> 回收站</el-button>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新建广告
        </el-button>
      </div>
    </div>

    <el-tabs v-model="contentTab" class="content-tabs">
      <el-tab-pane label="广告列表" name="list" />
      <el-tab-pane label="时效价格规则" name="rules" />
    </el-tabs>

    <div v-if="contentTab === 'rules'" class="price-rule-panel">
      <div class="price-rule-head">
        <div>
          <h3>广告位时效价格规则</h3>
          <p>按广告位置与投放天数设置申请价格。点击“保存规则”后会持久化到系统配置。</p>
        </div>
        <div class="price-rule-actions">
          <el-button size="small" @click="fillAllPresetRules">全部位置补齐推荐</el-button>
          <el-button size="small" @click="resetPriceRulesToDefault">恢复默认</el-button>
          <el-button size="small" type="primary" :loading="ruleSaving" @click="savePriceRules">保存规则</el-button>
        </div>
      </div>

      <el-tabs v-model="ruleActiveTab" class="price-rule-tabs">
        <el-tab-pane
          v-for="position in rulePositionOptions"
          :key="position.value"
          :name="position.value"
        >
          <template #label>
            <div class="price-rule-tab-label">
              <span>{{ position.label }}</span>
              <el-tag size="small" effect="plain">{{ getRuleCount(position.value) }}</el-tag>
            </div>
          </template>

          <div class="rule-toolbar">
            <div class="pit-selector">
              <span class="quick-label">坑位</span>
              <el-radio-group v-model="ruleActivePitIndex" size="small">
                <el-radio-button
                  v-for="pit in rulePitOptions"
                  :key="`${ruleActiveTab}-pit-${pit}`"
                  :label="pit"
                >
                  #{{ pit }}
                </el-radio-button>
              </el-radio-group>
            </div>

            <div class="quick-duration-wrap">
              <span class="quick-label">快捷时长</span>
              <el-button
                v-for="day in quickDurationOptions"
                :key="`${ruleActiveTab}-${ruleActivePitIndex}-${day}`"
                size="small"
                @click="addQuickDurationRule(day)"
              >
                +{{ day }}天
              </el-button>
              <el-button size="small" type="primary" plain @click="addPriceRule(ruleActiveTab, ruleActivePitIndex)">自定义</el-button>
            </div>

            <div class="rule-toolbar-actions">
              <el-button size="small" @click="mergePresetRulesForPosition(ruleActiveTab, ruleActivePitIndex)">补齐推荐价</el-button>
              <el-button size="small" @click="replaceActiveRulesWithPreset">重置当前坑位</el-button>
            </div>
          </div>

          <el-table :data="filteredPriceRules" size="small" border empty-text="当前广告位暂无规则，请先添加">
            <el-table-column label="投放时长(天)" width="180" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.durationDays" :min="1" :max="3650" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column label="价格(元)" width="220" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.price" :min="0" :max="999999" :precision="2" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column label="预览" min-width="180" align="center">
              <template #default="{ row }">
                <span class="price-preview">{{ row.durationDays }}天 / ¥{{ Number(row.price || 0).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="推荐" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="isPresetRule(row)" type="info" size="small" effect="plain">推荐</el-tag>
                <span v-else class="text-muted">自定义</span>
              </template>
            </el-table-column>
            <el-table-column label="校验" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="isRuleDuplicate(row)" type="danger" size="small">重复</el-tag>
                <el-tag v-else type="success" size="small" effect="plain">通过</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
              <template #default="{ row }">
                <el-button text type="danger" size="small" @click="removePriceRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="rule-footer">
            <span class="text-muted">当前坑位 #{{ ruleActivePitIndex }}</span>
            <span class="text-muted">共 {{ activeRuleStats.count }} 条规则</span>
            <span class="text-muted">最低价 ¥{{ activeRuleStats.minPrice.toFixed(2) }}</span>
            <span class="text-muted">最高价 ¥{{ activeRuleStats.maxPrice.toFixed(2) }}</span>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template v-if="contentTab === 'list'">
      <el-table :data="records" v-loading="loading" stripe height="560"
        @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="40" align="center" />
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column label="标题" prop="title" min-width="140" show-overflow-tooltip />
      <el-table-column label="类型" width="70" align="center">
        <template #default="{ row }">{{ row.type === 'image' ? '图片' : '代码' }}</template>
      </el-table-column>
      <el-table-column label="位置" width="132" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ positionDisplayLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="108" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.advertiserId" size="small" effect="plain" type="warning">用户申请</el-tag>
          <span v-else class="text-muted">管理员</span>
        </template>
      </el-table-column>
      <el-table-column label="坑位" width="84" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.pitEnabled" type="success" size="small" effect="plain">开放</el-tag>
          <span v-else class="text-muted">关闭</span>
        </template>
      </el-table-column>
      <el-table-column label="投放时间" min-width="240">
        <template #default="{ row }">
          <div v-if="!row.startTime && !row.endTime" class="time-permanent">永久</div>
          <div v-else class="time-cell">
            <span class="time-range-text">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) || '永久' }}</span>
            <span v-if="row.endTime" :class="['remaining-tag', remainingLevel(row.endTime)]">
              {{ remainingText(row.endTime) }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="权重" prop="weight" width="60" align="center" />
      <el-table-column label="点击" prop="clickCount" width="60" align="center" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openDetailDialog(row)">详情</el-button>
          <el-button v-if="row.status === 'active'" text type="info" size="small" @click="handleStatus(row, 'expired')">下架</el-button>
          <el-button v-if="row.status !== 'active' && row.status !== 'pending'" text type="success" size="small" @click="handleStatus(row, 'active')">上架</el-button>
          <el-button text type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
          background size="small"
          @current-change="loadData" @size-change="handleSizeChange" />
      </div>
    </template>

    <el-dialog v-model="detailVisible" title="广告申请详情" width="760px" destroy-on-close>
      <div v-if="detailRecord" class="detail-body">
        <div class="detail-grid">
          <div class="detail-item"><span>标题</span><strong>{{ detailRecord.title }}</strong></div>
          <div class="detail-item"><span>类型</span><strong>{{ detailRecord.type === 'image' ? '图片' : '代码' }}</strong></div>
          <div class="detail-item"><span>位置</span><strong>{{ posLabel(detailRecord.position) }}</strong></div>
          <div class="detail-item"><span>状态</span><strong>{{ statusLabel(detailRecord.status) }}</strong></div>
          <div class="detail-item"><span>开放坑位</span><strong>{{ detailRecord.pitEnabled ? '是' : '否' }}</strong></div>
          <div class="detail-item"><span>提交用户</span><strong>{{ detailRecord.advertiserId || '-' }}</strong></div>
          <div class="detail-item"><span>点击量</span><strong>{{ detailRecord.clickCount }}</strong></div>
          <div class="detail-item"><span>开始时间</span><strong>{{ fmt(detailRecord.startTime) || '-' }}</strong></div>
          <div class="detail-item"><span>结束时间</span><strong>{{ fmt(detailRecord.endTime) || '-' }}</strong></div>
          <div class="detail-item"><span>剩余时间</span><strong>{{ detailRecord.endTime ? remainingText(detailRecord.endTime) : '长期投放' }}</strong></div>
          <div class="detail-item"><span>提交时间</span><strong>{{ fmt(detailRecord.createTime) }}</strong></div>
          <div class="detail-item detail-item--full"><span>跳转链接</span><strong>{{ detailRecord.linkUrl || '-' }}</strong></div>
          <div class="detail-item detail-item--full"><span>广告信息</span><strong>{{ detailRecord.adInfo || '-' }}</strong></div>
          <div v-if="detailRecord.reviewReason" class="detail-item detail-item--full detail-reason-box">
            <span>拒绝原因</span>
            <strong>{{ detailRecord.reviewReason }}</strong>
          </div>
        </div>

        <div class="detail-preview">
          <template v-if="detailRecord.type === 'image'">
            <el-image :src="detailRecord.content" fit="cover" class="detail-preview-image" />
          </template>
          <template v-else>
            <pre class="detail-code-preview">{{ detailRecord.content }}</pre>
          </template>
        </div>

        <div v-if="detailRecord.advertiserId && detailRecord.status === 'pending'" class="detail-review-panel">
          <h4>审核操作</h4>
          <el-alert
            v-if="willAutoExtendOnApprove(detailRecord)"
            type="info"
            :closable="false"
            show-icon
            title="当前申请开始时间已过去，审核通过后系统会自动顺延并补足完整投放时长。"
          />
          <el-input
            v-model="detailRejectReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="拒绝时请填写原因（通过可不填）"
          />
          <div class="detail-review-actions">
            <el-button type="success" :loading="detailSubmitting" @click="submitDetailReview('active')">审核通过</el-button>
            <el-button type="danger" :loading="detailSubmitting" @click="submitDetailReview('rejected')">审核拒绝</el-button>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑广告' : '新建广告'" width="920px" destroy-on-close>
      <div class="dialog-scroll-body">
        <el-form :model="form" label-width="92px" class="ad-form-grid">
          <el-form-item label="标题" class="span-2"><el-input v-model="form.title" maxlength="100" clearable /></el-form-item>
          <el-form-item label="类型">
            <el-select v-model="form.type" style="width:100%">
              <el-option label="图片" value="image" /><el-option label="代码" value="code" />
            </el-select>
          </el-form-item>
          <el-form-item label="位置">
            <el-select v-model="form.position" style="width: 100%">
              <el-option label="首页左侧" value="home_left" />
              <el-option label="文章顶部" value="post_top" />
              <el-option label="文章底部" value="post_bottom" />
              <el-option label="文章列表拟态卡" value="post_list_card" :disabled="form.type === 'code'" />
            </el-select>
          </el-form-item>
          <el-form-item label="开放坑位">
            <el-switch v-model="form.pitEnabled" :disabled="editingAdvertiserId !== null" />
            <span class="inline-tip">开启后用户可申请投放并替换该广告位</span>
          </el-form-item>
          <el-form-item v-if="form.type === 'image'" label="广告图片" class="span-2">
            <div class="ad-upload-area">
                <div v-if="form.content" class="ad-preview" :class="`ad-preview--${form.position}`" :style="adPreviewStyle" @click="openImageCropper">
                  <el-image :src="form.content" fit="cover" class="ad-preview-img" />
                  <span class="ad-preview-badge">广告</span>
                  <button v-if="isCarouselPosition" class="ad-preview-close" type="button" aria-label="关闭预览">×</button>
                  <div
                    v-if="form.adInfo"
                    class="ad-preview-info"
                    :class="`ad-preview-info--${form.position}`"
                  >
                    {{ form.adInfo }}
                  </div>
                  <div class="ad-preview-overlay">裁剪 / 更换</div>
                </div>
              <div v-else class="ad-upload-placeholder" :style="adPreviewStyle" @click="triggerImageUpload">
                <el-icon :size="24"><Plus /></el-icon>
                <span>上传并裁剪图片（{{ currentCropRatioText }}）</span>
              </div>
              <input ref="imageInputRef" type="file" accept="image/*" style="display:none" @change="handleImageFileChange" />
              <div class="ad-upload-tip">建议比例：{{ currentCropRatioText }}</div>

              <div v-if="form.position === 'post_list_card' && showPreviewImage" class="mimic-live-preview">
                <div class="mimic-live-cover">
                  <el-image :src="form.content" fit="cover" class="mimic-live-cover-img" />
                  <div v-if="form.adInfo" class="mimic-live-ad-info">{{ form.adInfo }}</div>
                </div>
                <div class="mimic-live-body">
                  <div class="mimic-live-title">{{ form.title || '拟态广告标题' }}</div>
                  <div class="mimic-live-text">{{ form.mimicContent || '品牌推广' }}</div>
                </div>
              </div>
            </div>
          </el-form-item>
          <el-form-item v-else label="代码广告" class="span-2">
            <div class="code-editor-wrap">
              <div class="code-editor-toolbar">
                <div class="template-actions">
                  <span class="toolbar-label">样式模板</span>
                  <el-button
                    v-for="tpl in codeTemplateOptions"
                    :key="tpl.value"
                    size="small"
                    plain
                    @click="applyCodeTemplate(tpl.value)"
                  >
                    {{ tpl.label }}
                  </el-button>
                </div>
                <div class="editor-actions">
                  <el-button size="small" text @click="insertBaseTemplate">基础结构</el-button>
                  <el-button size="small" text :disabled="!form.content.trim()" @click="copyCodeContent">复制</el-button>
                  <el-button size="small" text type="danger" :disabled="!form.content.trim()" @click="clearCodeContent">清空</el-button>
                </div>
              </div>

              <div class="code-editor-main">
                <el-input
                  v-model="form.content"
                  type="textarea"
                  :rows="15"
                  resize="vertical"
                  class="code-input"
                  placeholder="输入 HTML/CSS 代码，右侧实时预览效果"
                />
                <div class="code-preview-panel">
                  <div class="code-preview-head">实时预览（与用户端一致 · {{ posLabel(codePreviewSlot) }}）</div>
                  <div class="code-preview-shell" :class="`slot-${codePreviewSlot}`">
                    <iframe class="code-preview-frame" :srcdoc="codePreviewDoc" sandbox="allow-forms allow-popups allow-same-origin" />
                  </div>
                </div>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="跳转链接" class="span-2"><el-input v-model="form.linkUrl" clearable /></el-form-item>
          <el-form-item label="广告信息" class="span-2">
            <el-input
              v-model="form.adInfo"
              maxlength="40"
              clearable
              placeholder="展示在广告图片上的简短说明（可不填）"
            />
          </el-form-item>
          <el-form-item v-if="form.type === 'image' && form.position === 'post_list_card'" label="拟态文案" class="span-2">
            <el-input
              v-model="form.mimicContent"
              type="textarea"
              :rows="2"
              maxlength="120"
              show-word-limit
              placeholder="拟态卡说明文案，不填则前台使用默认文案"
            />
          </el-form-item>
          <el-form-item v-if="form.type === 'image' && form.position === 'post_list_card'" label="广告位概况" class="span-2">
            <div class="mimic-slot-overview" v-loading="mimicSlotLoading">
              <div class="mimic-slot-tags">
                <el-tag size="small" effect="plain">文章总数 {{ mimicArticleTotal }}</el-tag>
                <el-tag size="small" effect="plain">可设置广告位 {{ mimicSlotLimit }}</el-tag>
                <el-tag size="small" effect="plain">已设置 {{ mimicActiveCount }}</el-tag>
                <el-tag size="small" effect="plain" type="success">可新增 {{ mimicAvailableCount }}</el-tag>
              </div>
              <p class="mimic-slot-tip">按每页 19 篇文章 + 1 条广告计算拟态广告位，上限 7 个。</p>
              <p class="mimic-slot-current">{{ mimicCurrentSlotText }}</p>
            </div>
          </el-form-item>
          <el-form-item v-if="form.type === 'image' && form.position === 'post_list_card'" label="插入位置">
            <el-input-number v-model="form.insertAfter" :min="1" :max="19" />
            <span class="inline-tip">限制 1~19，表示在第 N 篇文章后插入拟态广告卡</span>
          </el-form-item>
          <el-form-item v-if="isCarouselPosition" label="轮播间隔">
            <el-input-number v-model="form.rotateIntervalSec" :min="2" :max="30" />
            <span class="inline-tip">单位：秒</span>
          </el-form-item>
          <el-form-item label="投放时间" class="span-2">
            <div class="time-config">
              <el-checkbox v-model="form.permanent">永久投放</el-checkbox>
              <template v-if="!form.permanent">
                <div class="time-presets">
                  <el-button v-for="p in timePresets" :key="p.label" size="small" @click="applyPreset(p)">{{ p.label }}</el-button>
                </div>
                <div class="time-pickers">
                  <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始时间"
                    format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" style="width: 210px" />
                  <span class="time-sep">~</span>
                  <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束时间"
                    format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" style="width: 210px" />
                </div>
              </template>
            </div>
          </el-form-item>
          <el-form-item label="权重"><el-input-number v-model="form.weight" :min="0" :max="100" /></el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ImageCropper
      v-model="showImageCropper"
      :image-src="cropperImageSrc"
      :aspect-ratio="currentCropRatio"
      :show-ratio-options="false"
      output-type="image/webp"
      :max-output-width="1440"
      @crop="handleImageCropped"
    />

    <!-- 回收站弹窗 -->
    <el-dialog v-model="showTrashBox" title="回收站" width="900px" destroy-on-close>
      <div class="trash-toolbar">
        <el-input v-model="trashKeyword" placeholder="搜索标题" clearable style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="trash-actions">
          <el-button type="primary" size="small" :disabled="selectedTrashIds.length === 0" @click="handleBatchRestore">
            恢复{{ selectedTrashIds.length > 0 ? ` (${selectedTrashIds.length})` : '' }}
          </el-button>
          <el-button type="danger" size="small" :disabled="selectedTrashIds.length === 0" @click="handleBatchPermanentDelete">
            彻底删除{{ selectedTrashIds.length > 0 ? ` (${selectedTrashIds.length})` : '' }}
          </el-button>
          <el-button type="danger" size="small" plain @click="handleClearTrash" :disabled="trashRecords.length === 0">
            清空回收站
          </el-button>
        </div>
      </div>
      <div v-loading="trashLoading">
        <el-table :data="trashRecords" stripe max-height="400px" v-if="trashRecords.length" row-key="id"
          @selection-change="onTrashSelectionChange">
          <el-table-column type="selection" width="40" />
          <el-table-column label="#" width="55" align="center">
            <template #default="{ $index }">{{ (trashPagination.pageNum - 1) * trashPagination.pageSize + $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="标题" prop="title" min-width="160" show-overflow-tooltip />
          <el-table-column label="位置" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ posLabel(row.position) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="点击" prop="clickCount" width="60" align="center" />
          <el-table-column label="删除时间" width="160">
            <template #default="{ row }">{{ fmt(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="handleRestoreOne(row)">恢复</el-button>
              <el-button text type="danger" size="small" @click="handlePermanentDeleteOne(row)">彻底删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="回收站为空" />
      </div>
      <div class="trash-pagination" v-if="trashPagination.total > trashPagination.pageSize">
        <el-pagination v-model:current-page="trashPagination.pageNum" v-model:page-size="trashPagination.pageSize"
          :total="trashPagination.total" :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next" background size="small"
          @size-change="loadTrash" @current-change="loadTrash" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus, Delete, Search, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DOMPurify from 'dompurify'
import { advertisementApi, type AdvertisementVO, type AdPriceRuleVO } from '~/api/advertisement'
import { postApi } from '~/api/post'
import { uploadApi } from '~/api/upload'
import ImageCropper from '~/components/ImageCropper.vue'

const loading = ref(false)
const records = ref<AdvertisementVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const filterPosition = ref('')
const contentTab = ref('list')
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const selectedIds = ref<number[]>([])
const selectedRows = ref<AdvertisementVO[]>([])
const disableBatchActivate = computed(() => {
  return selectedRows.value.some(row => row.advertiserId && row.status === 'pending')
})
const applyEnabled = ref(false)
const switchLoading = ref(false)
const priceRules = ref<AdPriceRuleVO[]>([])
const ruleSaving = ref(false)
const ruleActiveTab = ref('home_left')
const ruleActivePitIndex = ref(1)
const rulePositionOptions = [
  { value: 'home_left', label: '首页左侧' },
  { value: 'post_top', label: '文章顶部' },
  { value: 'post_bottom', label: '文章底部' },
  { value: 'post_list_card', label: '文章列表拟态卡' },
]
const quickDurationOptions = [7, 30, 90, 180, 365]
const detailVisible = ref(false)
const detailRecord = ref<AdvertisementVO | null>(null)
const detailRejectReason = ref('')
const detailSubmitting = ref(false)
const editingAdvertiserId = ref<number | null>(null)
const mimicSlotLoading = ref(false)
const mimicArticleTotal = ref(0)
const mimicSlotLimit = ref(0)
const mimicActiveCount = ref(0)
const mimicActiveSlots = ref<Array<{ id: number; title: string }>>([])
const mimicAvailableCount = computed(() => Math.max(0, mimicSlotLimit.value - mimicActiveCount.value))
const mimicCurrentSlotText = computed(() => {
  if (!mimicActiveSlots.value.length) return '暂无已设置拟态广告位'
  return mimicActiveSlots.value
    .map((item, index) => `${index + 1}号位：${item.title}`)
    .join('；')
})
const MIMIC_POSTS_PER_PAGE = 19
const MIMIC_MAX_SLOT = 7
const MAX_PIT_COUNT = 7
const PIT_PRICE_STEP = 0.08
const MIN_PIT_PRICE_FACTOR = 0.52

const rulePresetsByPosition: Record<string, Array<{ durationDays: number; price: number }>> = {
  home_left: [
    { durationDays: 7, price: 299 },
    { durationDays: 30, price: 1099 },
    { durationDays: 90, price: 2999 },
    { durationDays: 180, price: 5399 },
  ],
  post_top: [
    { durationDays: 7, price: 239 },
    { durationDays: 30, price: 899 },
    { durationDays: 90, price: 2499 },
    { durationDays: 180, price: 4499 },
  ],
  post_bottom: [
    { durationDays: 7, price: 179 },
    { durationDays: 30, price: 699 },
    { durationDays: 90, price: 1899 },
    { durationDays: 180, price: 3499 },
  ],
  post_list_card: [
    { durationDays: 7, price: 129 },
    { durationDays: 30, price: 499 },
    { durationDays: 90, price: 1399 },
    { durationDays: 180, price: 2599 },
  ],
}

const rulePitOptions = computed(() => {
  const pitCount = ruleActiveTab.value === 'post_list_card'
    ? Math.max(1, Math.min(MIMIC_MAX_SLOT, mimicSlotLimit.value || MIMIC_MAX_SLOT))
    : MAX_PIT_COUNT
  return Array.from({ length: pitCount }, (_, index) => index + 1)
})

const positionOrderMap: Record<string, number> = {
  home_left: 1,
  post_top: 2,
  post_bottom: 3,
  post_list_card: 4,
}

const filteredPriceRules = computed(() => {
  return priceRules.value
    .filter(rule => rule.position === ruleActiveTab.value && normalizeRulePitIndex(rule.pitIndex) === ruleActivePitIndex.value)
    .slice()
    .sort((a, b) => a.durationDays - b.durationDays)
})

const activeRuleStats = computed(() => {
  if (filteredPriceRules.value.length === 0) {
    return {
      count: 0,
      minPrice: 0,
      maxPrice: 0,
    }
  }

  const prices = filteredPriceRules.value.map(rule => Number(rule.price || 0))
  return {
    count: filteredPriceRules.value.length,
    minPrice: Math.min(...prices),
    maxPrice: Math.max(...prices),
  }
})

const duplicateRuleKeys = computed(() => {
  const counts = new Map<string, number>()
  for (const row of priceRules.value) {
    const key = `${row.position}#${normalizeRulePitIndex(row.pitIndex)}#${row.durationDays}`
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  const duplicates = new Set<string>()
  counts.forEach((count, key) => {
    if (count > 1) duplicates.add(key)
  })
  return duplicates
})

const form = reactive({
  title: '', type: 'image', content: '', linkUrl: '', position: 'home_left',
  adInfo: '',
  mimicContent: '',
  weight: 1,
  insertAfter: 4,
  pitEnabled: false,
  closable: true,
  autoRotate: false,
  rotateIntervalSec: 6,
  permanent: true, startTime: '' as string, endTime: '' as string,
})

const cropRatioMap: Record<string, [number, number]> = {
  home_left: [5, 8],
  post_top: [16, 5],
  post_bottom: [16, 5],
  post_list_card: [16, 9],
}

const cropRatioTextMap: Record<string, string> = {
  home_left: '5:8',
  post_top: '16:5',
  post_bottom: '16:5',
  post_list_card: '16:9',
}

const imageInputRef = ref<HTMLInputElement>()
const pendingImageFile = ref<File | null>(null)
const showImageCropper = ref(false)
const cropperImageSrc = ref('')

const currentCropRatio = computed<[number, number]>(() => cropRatioMap[form.position] || [16, 9])
const currentCropRatioText = computed(() => cropRatioTextMap[form.position] || '16:9')
const showPreviewImage = computed(() => form.type === 'image' && Boolean(form.content))
const adPreviewStyle = computed(() => ({
  '--ad-preview-ratio': `${currentCropRatio.value[0]} / ${currentCropRatio.value[1]}`,
}))
const isCarouselPosition = computed(() => ['home_left', 'post_top', 'post_bottom'].includes(form.position))
const isCodeMimicPosition = computed(() => form.type === 'code' && form.position === 'post_list_card')
const codePreviewSlot = computed(() => {
  if (form.position === 'home_left' || form.position === 'post_top' || form.position === 'post_bottom') {
    return form.position
  }
  return 'post_top'
})

type CodeTemplateKey = 'button' | 'banner' | 'card'

const codeTemplateOptions: Array<{ label: string; value: CodeTemplateKey }> = [
  { label: '按钮', value: 'button' },
  { label: '横幅', value: 'banner' },
  { label: '卡片', value: 'card' },
]

const codePreviewSanitizeOptions = {
  ALLOWED_TAGS: ['a', 'img', 'span', 'div', 'p', 'strong', 'em'],
  ALLOWED_ATTR: ['href', 'src', 'alt', 'target', 'rel', 'class', 'style'],
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|\/(?!\/)|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
}

function getSlotForTemplate(position: string): 'home_left' | 'post_top' | 'post_bottom' {
  if (position === 'home_left' || position === 'post_top' || position === 'post_bottom') {
    return position
  }
  return 'post_top'
}

function getBaseTemplateBySlot(slot: 'home_left' | 'post_top' | 'post_bottom') {
  if (slot === 'home_left') {
    return `<div style="width:100%;height:100%;box-sizing:border-box;display:flex;align-items:flex-end;padding:14px;border-radius:12px;background:linear-gradient(120deg,#0f766e,#0ea5e9);"><a href="https://example.com" target="_blank" rel="noopener" style="display:inline-block;padding:8px 14px;border-radius:999px;background:#fff;color:#0f766e;font-weight:600;text-decoration:none;">立即查看</a></div>`
  }
  return `<div style="width:100%;height:100%;box-sizing:border-box;display:flex;align-items:center;justify-content:flex-end;padding:14px 18px;border-radius:12px;background:linear-gradient(120deg,#0f766e,#0ea5e9);"><a href="https://example.com" target="_blank" rel="noopener" style="display:inline-block;padding:8px 16px;border-radius:999px;background:#fff;color:#0f766e;font-weight:600;text-decoration:none;">立即查看</a></div>`
}

function getCodeTemplateBySlot(key: CodeTemplateKey, slot: 'home_left' | 'post_top' | 'post_bottom') {
  if (key === 'button') {
    return getBaseTemplateBySlot(slot)
  }

  if (key === 'banner') {
    if (slot === 'home_left') {
      return `<div style="width:100%;height:100%;box-sizing:border-box;display:flex;flex-direction:column;justify-content:space-between;padding:14px;border-radius:12px;background:linear-gradient(120deg,#0f766e,#0ea5e9);color:#fff;">
  <div>
    <div style="font-size:34px;font-weight:700;line-height:1.22;letter-spacing:.01em;">新品上线，首单立减</div>
    <div style="margin-top:10px;font-size:20px;line-height:1.45;opacity:.94;">点击查看活动详情</div>
  </div>
  <a href="https://example.com" target="_blank" rel="noopener" style="align-self:flex-start;padding:10px 16px;border-radius:999px;background:#fff;color:#0f766e;text-decoration:none;font-size:20px;font-weight:600;white-space:nowrap;">立即参与</a>
</div>`
    }

    return `<div style="width:100%;height:100%;box-sizing:border-box;display:flex;align-items:center;justify-content:space-between;gap:14px;padding:14px 18px;border-radius:12px;background:linear-gradient(120deg,#0f766e,#0ea5e9);color:#fff;">
  <div style="min-width:0;">
    <div style="font-size:16px;font-weight:700;line-height:1.3;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">新品上线，首单立减</div>
    <div style="margin-top:4px;font-size:13px;opacity:.9;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">点击查看活动详情</div>
  </div>
  <a href="https://example.com" target="_blank" rel="noopener" style="padding:8px 14px;border-radius:999px;background:#fff;color:#0f766e;text-decoration:none;font-size:13px;font-weight:600;white-space:nowrap;">立即参与</a>
</div>`
  }

  if (slot === 'home_left') {
    return `<div style="width:100%;height:100%;box-sizing:border-box;display:flex;flex-direction:column;border-radius:12px;overflow:hidden;background:#fff;box-shadow:0 8px 22px rgba(15,23,42,.08);">
  <img src="https://picsum.photos/640/360" alt="广告图" style="width:100%;height:48%;object-fit:cover;display:block;" />
  <div style="flex:1;padding:12px 12px 10px;display:flex;flex-direction:column;justify-content:space-between;">
    <div>
      <div style="font-size:22px;font-weight:700;line-height:1.3;color:#0f172a;">品牌特惠</div>
      <div style="margin-top:6px;font-size:16px;color:#475569;line-height:1.45;">限时活动，点击查看</div>
    </div>
    <a href="https://example.com" target="_blank" rel="noopener" style="align-self:flex-start;margin-top:8px;padding:8px 12px;border-radius:999px;background:#0f766e;color:#fff;text-decoration:none;font-size:16px;font-weight:600;">立即了解</a>
  </div>
</div>`
  }

  return `<div style="width:100%;height:100%;box-sizing:border-box;display:flex;align-items:center;gap:12px;border-radius:12px;overflow:hidden;background:#fff;box-shadow:0 8px 22px rgba(15,23,42,.08);padding:8px 10px;">
  <img src="https://picsum.photos/640/360" alt="广告图" style="height:100%;aspect-ratio:16/9;object-fit:cover;border-radius:8px;display:block;" />
  <div style="min-width:0;">
    <div style="font-size:14px;font-weight:700;color:#0f172a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">品牌特惠专场</div>
    <div style="margin-top:4px;font-size:12px;color:#475569;line-height:1.45;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">精选好物限时优惠，点击进入活动页。</div>
  </div>
</div>`
}

function sanitizeCodeForPreview(html: string) {
  return DOMPurify.sanitize(html, codePreviewSanitizeOptions)
}

const codePreviewDoc = computed(() => {
  const source = form.content.trim()
  const slot = codePreviewSlot.value
  const content = sanitizeCodeForPreview(source || '<div style="font-size:13px;line-height:1.65;color:#64748b;">在左侧输入或插入模板后，这里会按用户端真实样式渲染。</div>')

  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <style>
    html, body {
      margin: 0;
      padding: 0;
      background: #f8fafc;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }

    .preview-root {
      padding: 12px;
    }

    .ad-slot-banner {
      position: relative;
      border-radius: 16px;
      overflow: hidden;
      border: 1px solid rgba(148, 163, 184, 0.24);
      background: #fff;
      box-shadow: 0 10px 26px rgba(15, 23, 42, 0.14);
    }

    .ad-slide-window {
      overflow: hidden;
      position: relative;
    }

    .ad-slides {
      position: relative;
      width: 100%;
      min-height: 100px;
    }

    .ad-slide-item {
      position: absolute;
      inset: 0;
      width: 100%;
      opacity: 1;
    }

    .ad-code {
      padding: 0.75rem;
      min-height: 140px;
      box-sizing: border-box;
    }

    .ad-badge {
      position: absolute;
      left: 0.38rem;
      top: 0.36rem;
      z-index: 2;
      font-size: 0.56rem;
      letter-spacing: 0.02em;
      background: rgba(15, 23, 42, 0.36);
      color: #fff;
      border: 1px solid rgba(255, 255, 255, 0.2);
      padding: 0.08rem 0.42rem;
      border-radius: 999px;
      backdrop-filter: blur(2px);
      pointer-events: none;
    }

    .ad-close {
      position: absolute;
      right: 0.45rem;
      top: 0.45rem;
      z-index: 2;
      width: 24px;
      height: 24px;
      border: none;
      border-radius: 999px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      background: rgba(15, 23, 42, 0.42);
      color: #fff;
      font-size: 14px;
      line-height: 1;
      opacity: 0;
      transform: translate3d(0, -3px, 0);
      pointer-events: none;
      transition: opacity 180ms ease, transform 220ms ease;
    }

    .ad-slot-banner:hover .ad-close {
      opacity: 1;
      transform: translate3d(0, 0, 0);
      pointer-events: auto;
    }

    .ad-slot-banner--home_left {
      max-width: 220px;
      margin: 0 auto;
    }

    .ad-slot-banner--home_left .ad-slides {
      aspect-ratio: 5 / 8;
    }

    .ad-slot-banner--post_top .ad-slides,
    .ad-slot-banner--post_bottom .ad-slides {
      aspect-ratio: 16 / 5;
    }

    .ad-slot-banner--post_top .ad-badge,
    .ad-slot-banner--post_bottom .ad-badge {
      top: auto;
      bottom: 0.36rem;
    }
  </style>
</head>
<body>
  <div class="preview-root">
    <aside class="ad-slot-banner ad-slot-banner--${slot}">
      <button class="ad-close" type="button" aria-label="关闭广告">×</button>
      <div class="ad-slide-window">
        <div class="ad-slides">
          <div class="ad-slide-item active">
            <div class="ad-code">${content}</div>
          </div>
        </div>
      </div>
      <span class="ad-badge">广告</span>
    </aside>
  </div>
</body>
</html>`
})

function appendCodeChunk(chunk: string) {
  const current = form.content.trim()
  form.content = current ? `${form.content}\n\n${chunk}` : chunk
}

function applyCodeTemplate(key: CodeTemplateKey) {
  appendCodeChunk(getCodeTemplateBySlot(key, getSlotForTemplate(form.position)))
}

function insertBaseTemplate() {
  appendCodeChunk(getBaseTemplateBySlot(getSlotForTemplate(form.position)))
}

function clearCodeContent() {
  form.content = ''
}

async function copyCodeContent() {
  if (!form.content.trim()) return
  try {
    if (typeof navigator === 'undefined' || !navigator.clipboard) {
      throw new Error('clipboard-not-supported')
    }
    await navigator.clipboard.writeText(form.content)
    ElMessage.success('代码已复制')
  } catch {
    ElMessage.warning('当前环境不支持一键复制，请手动复制')
  }
}

// 时间预设
const timePresets = [
  { label: '7天', days: 7 },
  { label: '30天', days: 30 },
  { label: '90天', days: 90 },
  { label: '半年', days: 180 },
  { label: '1年', days: 365 },
]

function applyPreset(p: { days: number }) {
  const now = new Date()
  form.startTime = formatISO(now)
  const end = new Date(now.getTime() + p.days * 86400000)
  form.endTime = formatISO(end)
}

function formatISO(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

function posLabel(p: string) { return { home_left: '首页左侧', post_top: '文章顶部', post_bottom: '文章底部', post_list_card: '文章列表拟态卡', top: '顶部横幅', sidebar: '侧边栏', middle: '文章中部', bottom: '页面底部' }[p] || p }
function normalizeRulePitIndex(pitIndex: number | null | undefined) {
  return Number.isInteger(pitIndex) && Number(pitIndex) > 0 ? Number(pitIndex) : 1
}
function getPitCountByPosition(position: string) {
  if (position === 'post_list_card') {
    return Math.max(1, Math.min(MIMIC_MAX_SLOT, mimicSlotLimit.value || MIMIC_MAX_SLOT))
  }
  return MAX_PIT_COUNT
}
function pitPriceFactor(pitIndex: number) {
  const normalized = Math.max(1, Math.min(MAX_PIT_COUNT, pitIndex))
  const factor = 1 - (normalized - 1) * PIT_PRICE_STEP
  return Math.max(MIN_PIT_PRICE_FACTOR, Number(factor.toFixed(2)))
}
function calcPresetPrice(basePrice: number, pitIndex: number) {
  return Number((Number(basePrice || 0) * pitPriceFactor(pitIndex)).toFixed(2))
}
function getPresetPrice(position: string, durationDays: number, pitIndex: number) {
  const preset = (rulePresetsByPosition[position] || []).find(item => item.durationDays === durationDays)
  if (!preset) return 0
  return calcPresetPrice(preset.price, pitIndex)
}
function positionDisplayLabel(row: AdvertisementVO) {
  const base = posLabel(row.position)
  const pitIndex = normalizeRulePitIndex(row.pitIndex)
  const shouldShowPit = Number(row.pitIndex || 0) > 0
  if (!shouldShowPit) return base
  return `${base}（#${pitIndex}）`
}
function statusLabel(s: string) { return { pending: '待审核', approved: '已通过', rejected: '已拒绝', active: '投放中', expired: '已过期' }[s] || s }
function statusType(s: string) { return ({ pending: 'warning', active: 'success', rejected: 'danger', expired: 'info' }[s] || 'info') as 'warning' | 'success' | 'danger' | 'info' }
function fmt(t: string | null) { return t ? t.replace('T', ' ').slice(0, 16) : '' }

function parseDateTime(value: string | null | undefined) {
  if (!value) return null
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

function willAutoExtendOnApprove(record: AdvertisementVO | null) {
  if (!record || !record.advertiserId || record.status !== 'pending') return false
  const start = parseDateTime(record.startTime)
  if (!start) return false
  return Date.now() > start.getTime()
}

/** 计算剩余时间文本 */
function remainingText(endTime: string): string {
  const diff = new Date(endTime).getTime() - Date.now()
  if (diff <= 0) return '已过期'
  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  if (days > 30) return `剩${Math.floor(days / 30)}个月`
  if (days > 0) return `剩${days}天`
  if (hours > 0) return `剩${hours}小时`
  return `剩${Math.max(1, Math.floor((diff % 3600000) / 60000))}分钟`
}

/** 根据剩余时长返回样式级别 */
function remainingLevel(endTime: string): string {
  const diff = new Date(endTime).getTime() - Date.now()
  if (diff <= 0) return 'expired'
  if (diff < 86400000) return 'urgent'
  if (diff < 3 * 86400000) return 'warning'
  return 'normal'
}

function handleFilterChange() { pageNum.value = 1; loadData() }
function handleSizeChange() { pageNum.value = 1; loadData() }
function handleSelectionChange(rows: AdvertisementVO[]) {
  selectedRows.value = rows
  selectedIds.value = rows.map(r => r.id)
}

function openDetailDialog(row: AdvertisementVO) {
  detailRecord.value = { ...row }
  detailRejectReason.value = row.reviewReason || ''
  detailVisible.value = true
}

async function submitDetailReview(status: 'active' | 'rejected') {
  if (!detailRecord.value) return

  const reason = detailRejectReason.value.trim()
  if (status === 'rejected' && !reason) {
    ElMessage.warning('拒绝时请填写原因')
    return
  }

  detailSubmitting.value = true
  try {
    const shouldAutoExtend = status === 'active' && willAutoExtendOnApprove(detailRecord.value)
    await advertisementApi.updateStatus(detailRecord.value.id, status, status === 'rejected' ? reason : undefined)
    ElMessage.success(status === 'active'
      ? (shouldAutoExtend ? '审核已通过，系统已自动顺延投放期' : '审核已通过')
      : '审核已拒绝')
    await loadData()

    const latest = records.value.find(item => item.id === detailRecord.value?.id)
    if (latest) {
      detailRecord.value = { ...latest }
      detailRejectReason.value = latest.reviewReason || ''
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    detailSubmitting.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await advertisementApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, status: filterStatus.value || undefined, position: filterPosition.value || undefined })
    records.value = res.data.records
    total.value = res.data.total
    selectedIds.value = []
    selectedRows.value = []
    await loadMimicSlotMeta()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '加载失败') } finally { loading.value = false }
}

async function loadApplySwitch() {
  try {
    const res = await advertisementApi.getApplySwitch()
    applyEnabled.value = res.data.enabled
  } catch { /* 忽略 */ }
}

async function loadPriceRules() {
  try {
    const res = await advertisementApi.getPriceRules()
    priceRules.value = (res.data.rules || []).map(rule => ({
      position: rule.position,
      pitIndex: normalizeRulePitIndex(rule.pitIndex),
      durationDays: Number(rule.durationDays || 1),
      price: Number(rule.price || 0),
    }))
    if (priceRules.value.length === 0) {
      priceRules.value = buildDefaultRuleList()
    }
    sortPriceRulesInPlace()
  } catch {
    priceRules.value = buildDefaultRuleList()
    sortPriceRulesInPlace()
  }

  ensureActiveRulePitIndex()
}

function calcMimicSlotLimit(totalPosts: number) {
  if (!Number.isFinite(totalPosts) || totalPosts <= 0) return 0
  return Math.min(MIMIC_MAX_SLOT, Math.ceil(totalPosts / MIMIC_POSTS_PER_PAGE))
}

async function loadMimicSlotMeta() {
  mimicSlotLoading.value = true
  try {
    const [postRes, mimicRes] = await Promise.all([
      postApi.page({ pageNum: 1, pageSize: 1, status: 'published', isDisabled: false }),
      advertisementApi.list({ pageNum: 1, pageSize: 100, status: 'active', position: 'post_list_card' }),
    ])

    mimicArticleTotal.value = Number(postRes.data.total || 0)
    mimicSlotLimit.value = calcMimicSlotLimit(mimicArticleTotal.value)
    mimicActiveCount.value = Number(mimicRes.data.total || 0)
    mimicActiveSlots.value = (mimicRes.data.records || [])
      .slice()
      .sort((a, b) => Number(b.weight || 0) - Number(a.weight || 0))
      .slice(0, MIMIC_MAX_SLOT)
      .map(item => ({ id: item.id, title: item.title || `广告#${item.id}` }))
  } catch {
    mimicArticleTotal.value = 0
    mimicSlotLimit.value = 0
    mimicActiveCount.value = 0
    mimicActiveSlots.value = []
  } finally {
    mimicSlotLoading.value = false
    ensureActiveRulePitIndex()
  }
}

function buildDefaultRuleList(): AdPriceRuleVO[] {
  return Object.entries(rulePresetsByPosition).flatMap(([position, rules]) => {
    return Array.from({ length: MAX_PIT_COUNT }, (_, index) => index + 1).flatMap((pitIndex) => {
      return rules.map(item => ({
        position,
        pitIndex,
        durationDays: item.durationDays,
        price: calcPresetPrice(item.price, pitIndex),
      }))
    })
  })
}

function sortPriceRulesInPlace() {
  priceRules.value.sort((a, b) => {
    if (a.position !== b.position) {
      return (positionOrderMap[a.position] || 99) - (positionOrderMap[b.position] || 99)
    }
    const pitDiff = normalizeRulePitIndex(a.pitIndex) - normalizeRulePitIndex(b.pitIndex)
    if (pitDiff !== 0) {
      return pitDiff
    }
    return a.durationDays - b.durationDays
  })
}

function ensureActiveRulePitIndex() {
  const options = rulePitOptions.value
  if (!options.includes(ruleActivePitIndex.value)) {
    ruleActivePitIndex.value = options[0] || 1
  }
}

function addPriceRule(position?: string, pitIndex?: number) {
  const fallback = ['home_left', 'post_top', 'post_bottom', 'post_list_card'].includes(position || '')
    ? (position as string)
    : ruleActiveTab.value
  const normalizedPit = Math.min(getPitCountByPosition(fallback), Math.max(1, Number(pitIndex || ruleActivePitIndex.value || 1)))
  priceRules.value.push({
    position: fallback,
    pitIndex: normalizedPit,
    durationDays: 7,
    price: getPresetPrice(fallback, 7, normalizedPit),
  })
  sortPriceRulesInPlace()
}

function removePriceRule(row: AdPriceRuleVO) {
  const idx = priceRules.value.findIndex(item => item === row)
  if (idx >= 0) {
    priceRules.value.splice(idx, 1)
  }
}

function mergePresetRulesForPosition(position: string, pitIndex: number) {
  const presets = rulePresetsByPosition[position] || []
  const normalizedPit = Math.min(getPitCountByPosition(position), Math.max(1, Number(pitIndex || 1)))
  for (const preset of presets) {
    const exists = priceRules.value.some(row =>
      row.position === position
      && normalizeRulePitIndex(row.pitIndex) === normalizedPit
      && row.durationDays === preset.durationDays)
    if (!exists) {
      priceRules.value.push({
        position,
        pitIndex: normalizedPit,
        durationDays: preset.durationDays,
        price: calcPresetPrice(preset.price, normalizedPit),
      })
    }
  }
  sortPriceRulesInPlace()
}

function getRuleCount(position: string) {
  return priceRules.value.filter(rule => rule.position === position).length
}

function isPresetRule(row: AdPriceRuleVO) {
  const expected = getPresetPrice(row.position, row.durationDays, normalizeRulePitIndex(row.pitIndex))
  return Math.abs(Number(row.price || 0) - expected) < 0.01
}

function fillAllPresetRules() {
  for (const position of Object.keys(rulePresetsByPosition)) {
    const pitCount = getPitCountByPosition(position)
    for (let pitIndex = 1; pitIndex <= pitCount; pitIndex += 1) {
      mergePresetRulesForPosition(position, pitIndex)
    }
  }
  ElMessage.success('已补齐全部广告位推荐规则')
}

function addQuickDurationRule(durationDays: number) {
  const position = ruleActiveTab.value
  const pitIndex = ruleActivePitIndex.value
  const exists = priceRules.value.some(rule =>
    rule.position === position
    && normalizeRulePitIndex(rule.pitIndex) === pitIndex
    && rule.durationDays === durationDays)
  if (exists) {
    ElMessage.warning(`#${pitIndex} 的 ${durationDays}天规则已存在`)
    return
  }

  priceRules.value.push({
    position,
    pitIndex,
    durationDays,
    price: getPresetPrice(position, durationDays, pitIndex),
  })
  sortPriceRulesInPlace()
}

function replaceActiveRulesWithPreset() {
  const position = ruleActiveTab.value
  const pitIndex = ruleActivePitIndex.value
  const remained = priceRules.value.filter(rule => !(rule.position === position && normalizeRulePitIndex(rule.pitIndex) === pitIndex))
  const replaced = (rulePresetsByPosition[position] || []).map(item => ({
    position,
    pitIndex,
    durationDays: item.durationDays,
    price: calcPresetPrice(item.price, pitIndex),
  }))
  priceRules.value = [...remained, ...replaced]
  sortPriceRulesInPlace()
  ElMessage.success(`已重置 ${posLabel(position)} #${pitIndex} 规则`)
}

function resetPriceRulesToDefault() {
  priceRules.value = buildDefaultRuleList()
  sortPriceRulesInPlace()
  ElMessage.success('已恢复为默认价格规则')
}

function isRuleDuplicate(row: AdPriceRuleVO) {
  return duplicateRuleKeys.value.has(`${row.position}#${normalizeRulePitIndex(row.pitIndex)}#${row.durationDays}`)
}

function validatePriceRules() {
  if (priceRules.value.length === 0) {
    return '请至少配置一条价格规则'
  }

  const seen = new Set<string>()
  for (const row of priceRules.value) {
    if (!['home_left', 'post_top', 'post_bottom', 'post_list_card'].includes(row.position)) {
      return '存在无效广告位规则'
    }
    const pitIndex = normalizeRulePitIndex(row.pitIndex)
    if (!Number.isInteger(pitIndex) || pitIndex < 1 || pitIndex > MAX_PIT_COUNT) {
      return `坑位编号必须在 1~${MAX_PIT_COUNT}`
    }
    if (!Number.isInteger(row.durationDays) || row.durationDays < 1 || row.durationDays > 3650) {
      return '投放时长必须为 1 ~ 3650 的整数天'
    }
    if (!Number.isFinite(row.price) || row.price < 0) {
      return '价格不能小于 0'
    }

    const key = `${row.position}#${pitIndex}#${row.durationDays}`
    if (seen.has(key)) {
      return `存在重复规则：${posLabel(row.position)} #${pitIndex} ${row.durationDays}天`
    }
    seen.add(key)
  }

  return null
}

async function savePriceRules() {
  const error = validatePriceRules()
  if (error) {
    ElMessage.warning(error)
    return
  }

  ruleSaving.value = true
  try {
    const normalizedRules = priceRules.value
      .map(rule => ({
        position: rule.position,
        pitIndex: normalizeRulePitIndex(rule.pitIndex),
        durationDays: Number(rule.durationDays),
        price: Number(rule.price),
      }))
      .sort((a, b) => {
        if (a.position !== b.position) return (positionOrderMap[a.position] || 99) - (positionOrderMap[b.position] || 99)
        if (a.pitIndex !== b.pitIndex) return a.pitIndex - b.pitIndex
        return a.durationDays - b.durationDays
      })

    await advertisementApi.setPriceRules(
      normalizedRules
    )
    ElMessage.success('价格规则已保存')
    await loadPriceRules()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    ruleSaving.value = false
  }
}

watch(() => ruleActiveTab.value, () => {
  ensureActiveRulePitIndex()
})

watch(() => rulePitOptions.value, () => {
  ensureActiveRulePitIndex()
}, { immediate: true })

async function handleApplySwitchChange(val: boolean | string | number) {
  switchLoading.value = true
  try {
    await advertisementApi.setApplySwitch(Boolean(val))
    ElMessage.success(val ? '申请入口已开放' : '申请入口已关闭')
  } catch (e: unknown) {
    applyEnabled.value = !Boolean(val)
    ElMessage.error((e as Error).message || '操作失败')
  } finally { switchLoading.value = false }
}

function openDialog(row?: AdvertisementVO) {
  editingId.value = row?.id || null
  editingAdvertiserId.value = row?.advertiserId ?? null
  form.title = row?.title || ''; form.type = row?.type || 'image'; form.content = row?.content || ''
  form.linkUrl = row?.linkUrl || ''; form.position = row?.position || 'home_left'
  form.adInfo = row?.adInfo || ''
  form.mimicContent = row?.mimicContent || ''
  form.insertAfter = row?.insertAfter ?? 4
  form.pitEnabled = Boolean(row?.pitEnabled && !row?.advertiserId)
  form.closable = isCarouselPosition.value ? true : Boolean(row?.closable)
  form.autoRotate = isCarouselPosition.value ? true : Boolean(row?.autoRotate)
  form.rotateIntervalSec = row?.rotateIntervalSec ?? 6
  form.weight = row?.weight ?? 1
  form.permanent = !row?.startTime && !row?.endTime
  form.startTime = row?.startTime || ''; form.endTime = row?.endTime || ''
  pendingImageFile.value = null
  dialogVisible.value = true
}

watch(() => form.position, () => {
  if (form.type === 'code' && form.position === 'post_list_card') {
    form.position = 'post_top'
    ElMessage.warning('代码广告不支持拟态卡位，已自动切换到文章顶部')
    return
  }

  if (isCarouselPosition.value) {
    form.closable = true
    form.autoRotate = true
    if (!form.rotateIntervalSec || form.rotateIntervalSec < 2) {
      form.rotateIntervalSec = 6
    }
  } else {
    form.closable = false
    form.autoRotate = false
  }

  if (form.position === 'post_list_card' && form.insertAfter > 19) {
    form.insertAfter = 19
  }
})

watch(() => form.type, (type) => {
  if (type === 'code' && form.position === 'post_list_card') {
    form.position = 'post_top'
    ElMessage.warning('代码广告不支持拟态卡位，已自动切换到文章顶部')
  }
})

function triggerImageUpload() {
  imageInputRef.value?.click()
}

function handleImageFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    cropperImageSrc.value = ev.target?.result as string
    showImageCropper.value = true
  }
  reader.readAsDataURL(file)
  if (imageInputRef.value) imageInputRef.value.value = ''
}

function openImageCropper() {
  if (form.content && !form.content.startsWith('blob:')) {
    cropperImageSrc.value = form.content
    showImageCropper.value = true
  } else {
    triggerImageUpload()
  }
}

function handleImageCropped(data: { blob: Blob; url: string }) {
  if (form.content.startsWith('blob:')) URL.revokeObjectURL(form.content)
  form.content = data.url
  const ext = data.blob.type === 'image/webp' ? 'webp' : data.blob.type === 'image/png' ? 'png' : 'jpg'
  pendingImageFile.value = new File([data.blob], `ad.${ext}`, { type: data.blob.type })
}

async function handleSubmit() {
  submitting.value = true
  try {
    if (isCodeMimicPosition.value) {
      ElMessage.warning('代码广告不支持拟态卡位，请切换广告位置')
      return
    }

    if (editingAdvertiserId.value !== null && form.pitEnabled) {
      ElMessage.warning('用户申请广告不可设置坑位，已自动关闭')
      form.pitEnabled = false
    }

    if (form.position === 'post_list_card') {
      const normalizedInsertAfter = Number(form.insertAfter || 4)
      form.insertAfter = Math.min(19, Math.max(1, normalizedInsertAfter))
    }

    if (form.type === 'image' && pendingImageFile.value) {
      const uploadRes = await uploadApi.image(pendingImageFile.value, 'ad')
      if (form.content.startsWith('blob:')) URL.revokeObjectURL(form.content)
      form.content = uploadRes.data
      pendingImageFile.value = null
    }

    const data: Record<string, unknown> = {
      title: form.title, type: form.type, content: form.content,
      linkUrl: form.linkUrl, position: form.position,
      adInfo: form.adInfo || null,
      mimicContent: form.position === 'post_list_card' ? (form.mimicContent || null) : null,
      insertAfter: form.position === 'post_list_card' ? form.insertAfter : null,
      pitEnabled: editingAdvertiserId.value === null ? form.pitEnabled : false,
      closable: isCarouselPosition.value ? true : null,
      autoRotate: isCarouselPosition.value ? true : null,
      rotateIntervalSec: isCarouselPosition.value ? form.rotateIntervalSec : null,
      weight: form.weight,
      startTime: form.permanent ? null : (form.startTime || null),
      endTime: form.permanent ? null : (form.endTime || null),
    }
    if (editingId.value) { await advertisementApi.update(editingId.value, data); ElMessage.success('更新成功') }
    else { await advertisementApi.create(data); ElMessage.success('创建成功') }
    dialogVisible.value = false; loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') } finally { submitting.value = false }
}

async function handleStatus(row: AdvertisementVO, status: string) {
  try { await advertisementApi.updateStatus(row.id, status); ElMessage.success('操作成功'); loadData() }
  catch (e: unknown) { ElMessage.error((e as Error).message) }
}

async function handleDelete(row: AdvertisementVO) {
  await ElMessageBox.confirm(`确定删除广告「${row.title}」？`, '提示', { type: 'warning' })
  try { await advertisementApi.delete(row.id); ElMessage.success('删除成功'); loadData() }
  catch (e: unknown) { ElMessage.error((e as Error).message) }
}

async function handleBatchCommand(command: string) {
  if (command === 'delete') handleBatchDelete()
  else handleBatchStatus(command)
}

async function handleBatchStatus(status: string) {
  if (status === 'active') {
    const pendingUserApplications = selectedRows.value.filter(row => row.advertiserId && row.status === 'pending')
    if (pendingUserApplications.length > 0) {
      ElMessage.warning('选中项包含用户待审核申请，请在详情弹窗逐条审核（通过/拒绝并填写原因）')
      return
    }
  }

  const label = status === 'active' ? '上架' : '下架'
  await ElMessageBox.confirm(`确定批量${label}选中的 ${selectedIds.value.length} 条广告？`, '批量操作', { type: 'warning' })
  try {
    await advertisementApi.batchUpdateStatus(selectedIds.value, status)
    ElMessage.success(`批量${label}成功`)
    selectedIds.value = []
    selectedRows.value = []
    loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') }
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定批量删除选中的 ${selectedIds.value.length} 条广告？`, '批量删除', { type: 'warning' })
  try {
    await advertisementApi.batchDelete(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    selectedRows.value = []
    loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
}

// ========== 回收站 ==========
const showTrashBox = ref(false)
const trashRecords = ref<AdvertisementVO[]>([])
const trashLoading = ref(false)
const selectedTrashIds = ref<number[]>([])
const trashKeyword = ref('')
const trashPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

function onTrashSelectionChange(rows: AdvertisementVO[]) {
  selectedTrashIds.value = rows.map(r => r.id)
}

watch(showTrashBox, (val) => { if (val) { trashKeyword.value = ''; trashPagination.pageNum = 1; loadTrash() } })

let trashDebounce: ReturnType<typeof setTimeout> | null = null
watch(() => trashKeyword.value, () => {
  if (trashDebounce) clearTimeout(trashDebounce)
  trashDebounce = setTimeout(() => { trashPagination.pageNum = 1; loadTrash() }, 300)
})

async function loadTrash() {
  trashLoading.value = true
  try {
    const res = await advertisementApi.trashPage({
      pageNum: trashPagination.pageNum,
      pageSize: trashPagination.pageSize,
      keyword: trashKeyword.value || undefined,
    })
    trashRecords.value = res.data.records
    trashPagination.total = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally { trashLoading.value = false }
}

async function handleBatchRestore() {
  await ElMessageBox.confirm(`确定恢复选中的 ${selectedTrashIds.value.length} 条广告？`, '恢复确认', { type: 'info' })
  try {
    await advertisementApi.batchRestore(selectedTrashIds.value)
    ElMessage.success('恢复成功'); selectedTrashIds.value = []; loadTrash(); loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '恢复失败') }
}

async function handleBatchPermanentDelete() {
  await ElMessageBox.confirm(`确定彻底删除选中的 ${selectedTrashIds.value.length} 条广告？此操作不可恢复`, '彻底删除', { type: 'warning' })
  try {
    await advertisementApi.batchPermanentDelete(selectedTrashIds.value)
    ElMessage.success('彻底删除成功'); selectedTrashIds.value = []; loadTrash()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
}

async function handleRestoreOne(row: AdvertisementVO) {
  try {
    await advertisementApi.batchRestore([row.id])
    ElMessage.success('恢复成功'); loadTrash(); loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '恢复失败') }
}

async function handlePermanentDeleteOne(row: AdvertisementVO) {
  await ElMessageBox.confirm(`确定彻底删除广告「${row.title}」？此操作不可恢复`, '彻底删除', { type: 'warning' })
  try {
    await advertisementApi.batchPermanentDelete([row.id])
    ElMessage.success('彻底删除成功'); loadTrash()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
}

async function handleClearTrash() {
  await ElMessageBox.confirm('确定清空回收站？所有已删除广告将被永久删除，此操作不可恢复', '清空回收站', { type: 'warning' })
  try {
    await advertisementApi.clearTrash()
    ElMessage.success('回收站已清空'); trashRecords.value = []; trashPagination.total = 0; loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') }
}

onMounted(() => { loadData(); loadApplySwitch(); loadPriceRules() })
</script>

<style scoped lang="scss">
.ad-page {

  .content-tabs {
    margin-bottom: 10px;
  }

  .price-rule-panel {
    margin-bottom: 12px;
    padding: 12px;
    border: 1px solid var(--el-border-color-light);
    border-radius: 10px;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(248, 250, 252, 0.9));
  }

  .price-rule-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;

    h3 {
      margin: 0;
      font-size: 14px;
      font-weight: 700;
      color: var(--el-text-color-primary);
    }

    p {
      margin: 4px 0 0;
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }

  .price-rule-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    flex-shrink: 0;
    justify-content: flex-end;
  }

  .price-rule-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: 10px;
    }
  }

  .price-rule-tab-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .rule-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;
    flex-wrap: wrap;
    padding: 8px 10px;
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;
    background: var(--el-fill-color-blank);
  }

  .quick-duration-wrap {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  .pit-selector {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .quick-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-right: 2px;
  }

  .rule-toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .rule-footer {
    display: flex;
    align-items: center;
    gap: 12px;
    justify-content: flex-end;
    margin-top: 10px;
  }

  .price-preview {
    font-size: 12px;
    color: var(--el-color-primary);
    font-weight: 600;
  }

  .text-muted {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .apply-switch {
    display: flex; align-items: center; gap: 6px;
    padding: 4px 12px; border-radius: 8px;
    background: var(--el-fill-color-light);
    .switch-label { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; }
  }

  .time-permanent { font-size: 12px; color: var(--el-color-success); font-weight: 500; }

  .time-cell {
    display: flex;
    align-items: center;
    gap: 8px;
    .time-range-text { font-size: 12px; color: var(--el-text-color-regular); }
  }
  .remaining-tag {
    display: inline-block;
    font-size: 11px;
    font-weight: 600;
    padding: 0 6px;
    height: 18px;
    line-height: 18px;
    border-radius: 4px;
    width: fit-content;
    &.normal { background: var(--el-color-success-light-9); color: var(--el-color-success); }
    &.warning { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
    &.urgent { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
    &.expired { background: var(--el-fill-color-light); color: var(--el-text-color-disabled); text-decoration: line-through; }
  }

  .pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
}

.ad-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;

  :deep(.el-form-item) {
    margin-bottom: 14px;
  }
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.detail-item {
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  display: flex;
  flex-direction: column;
  gap: 4px;

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  strong {
    font-size: 13px;
    color: var(--el-text-color-primary);
    line-height: 1.5;
    word-break: break-word;
  }

  &.detail-item--full {
    grid-column: 1 / -1;
  }
}

.detail-reason-box {
  border-color: var(--el-color-danger-light-7);
  background: var(--el-color-danger-light-9);
}

.detail-preview {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-fill-color-blank);
}

.detail-preview-image {
  display: block;
  width: 100%;
  max-height: 260px;
}

.detail-code-preview {
  margin: 0;
  padding: 12px;
  max-height: 260px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  background: #0f172a;
  color: #cbd5e1;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-review-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;

  h4 {
    margin: 0;
    font-size: 14px;
    font-weight: 700;
    color: var(--el-text-color-primary);
  }
}

.detail-review-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.span-2 {
  grid-column: 1 / -1;
}


.ad-upload-area {
  width: 100%;
}

.ad-preview {
  position: relative;
  width: 100%;
  max-width: 260px;
  aspect-ratio: var(--ad-preview-ratio, 16 / 9);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}

.ad-preview::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 46%;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0) 36%, rgba(15, 23, 42, 0.18) 74%, rgba(15, 23, 42, 0.32) 100%);
}

.ad-preview--post_list_card::after {
  display: none;
}

.ad-preview-badge {
  position: absolute;
  left: 0.38rem;
  top: 0.36rem;
  z-index: 3;
  font-size: 0.56rem;
  letter-spacing: 0.02em;
  background: rgba(15, 23, 42, 0.36);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.2);
  padding: 0.08rem 0.42rem;
  border-radius: 999px;
  backdrop-filter: blur(2px);
  pointer-events: none;
}

.ad-preview--post_top .ad-preview-badge,
.ad-preview--post_bottom .ad-preview-badge {
  top: auto;
  bottom: 0.36rem;
}

.ad-preview-close {
  position: absolute;
  right: 0.45rem;
  top: 0.45rem;
  z-index: 3;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.42);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  opacity: 0;
  transform: translate3d(0, -3px, 0);
  pointer-events: none;
  transition: opacity 180ms ease, transform 220ms ease;
}

.ad-preview:hover .ad-preview-close {
  opacity: 1;
  transform: translate3d(0, 0, 0);
}

.ad-preview-img {
  width: 100%;
  height: 100%;
  display: block;
}

.ad-preview-overlay {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s;
}

.ad-preview-info {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 12px;
  z-index: 3;
  color: #fff;
  font-size: 11px;
  line-height: 1.35;
  font-weight: 600;
  text-align: center;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.42), 0 8px 20px rgba(0, 0, 0, 0.28);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ad-preview-info--home_left,
.ad-preview-info--post_top,
.ad-preview-info--post_bottom {
  bottom: 20px;
}

.ad-preview-info--post_list_card {
  left: 8px;
  right: 8px;
  bottom: 8px;
  text-align: left;
  font-size: 10px;
}

.ad-preview:hover .ad-preview-overlay {
  opacity: 1;
}

.ad-upload-placeholder {
  width: 100%;
  max-width: 260px;
  aspect-ratio: var(--ad-preview-ratio, 16 / 9);
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  color: var(--el-text-color-placeholder);
}

.ad-upload-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.inline-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mimic-slot-overview {
  width: 100%;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
}

.mimic-slot-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mimic-slot-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.mimic-slot-current {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.code-editor-wrap {
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 12px;
  background: #fff;
}

.code-editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.template-actions,
.editor-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.code-editor-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.code-input {
  :deep(.el-textarea__inner) {
    min-height: 320px !important;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
    font-size: 12px;
    line-height: 1.55;
  }
}

.code-preview-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  overflow: hidden;
  background: #f8fafc;
}

.code-preview-head {
  height: 36px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-bottom: 1px solid var(--el-border-color-light);
  background: #fff;
}

.code-preview-shell {
  padding: 10px;
  background: #f8fafc;
}

.code-preview-shell.slot-home_left {
  display: flex;
  justify-content: center;
}

.code-preview-frame {
  width: 100%;
  aspect-ratio: 16 / 5;
  height: auto;
  border: 0;
  display: block;
  background: #f8fafc;
}

.code-preview-shell.slot-home_left .code-preview-frame {
  max-width: 280px;
  aspect-ratio: 5 / 8;
}

.mimic-live-preview {
  margin-top: 10px;
  max-width: 420px;
  display: flex;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.mimic-live-cover {
  position: relative;
  width: 176px;
  aspect-ratio: 16 / 9;
  flex-shrink: 0;
}

.mimic-live-cover-img {
  width: 100%;
  height: 100%;
  display: block;
}

.mimic-live-ad-info {
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 8px;
  color: #fff;
  font-size: 10px;
  line-height: 1.35;
  text-shadow: 0 2px 7px rgba(0, 0, 0, 0.4);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.mimic-live-body {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mimic-live-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mimic-live-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

@media (max-width: 900px) {
  .ad-page .price-rule-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .ad-page .price-rule-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .ad-page .rule-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .ad-page .rule-footer {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .ad-form-grid {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: auto;
  }

  .mimic-live-preview {
    max-width: 100%;
  }

  .code-editor-main {
    grid-template-columns: 1fr;
  }

  .code-preview-shell.slot-home_left .code-preview-frame {
    max-width: 240px;
  }
}

@media (max-width: 640px) {
  .mimic-live-preview {
    flex-direction: column;
  }

  .mimic-live-cover {
    width: 100%;
  }

  .time-config .time-pickers {
    flex-wrap: wrap;
  }

  .inline-tip {
    display: block;
    margin-left: 0;
    margin-top: 4px;
  }
}

// ========== 弹窗滚动体 ==========
.dialog-scroll-body {
  max-height: 60vh;
  overflow-y: auto;
}

// ========== 时间配置 ==========
.time-config {
  display: flex; flex-direction: column; gap: 8px;
  .time-presets { display: flex; gap: 6px; flex-wrap: wrap; }
  .time-pickers { display: flex; align-items: center; gap: 8px; }
  .time-sep { color: var(--el-text-color-secondary); }
}

// ========== 回收站工具栏 ==========
.trash-toolbar {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
  .trash-actions { display: flex; gap: 8px; }
}
.trash-pagination { display: flex; justify-content: flex-end; margin-top: 12px; }

// ========== Switch ==========
:deep(.el-switch) { --el-switch-on-color: var(--el-color-primary); --el-switch-off-color: var(--el-fill-color-darker); height: 20px; }
:deep(.el-switch .el-switch__core) { height: 20px; min-width: 36px; border-radius: 10px; border: none; }
:deep(.el-switch .el-switch__core .el-switch__action) { width: 16px; height: 16px; }

// ========== 弹窗 ==========
:deep(.el-dialog) {
  border-radius: 16px; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  .el-dialog__header { padding: 20px 24px 12px; .el-dialog__title { font-weight: 700; font-size: 16px; } }
  .el-dialog__body { padding: 12px 24px 20px; }
}
</style>
