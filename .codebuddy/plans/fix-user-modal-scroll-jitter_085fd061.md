---
name: fix-user-modal-scroll-jitter
overview: 定位并修复用户端登录模态框开关时导航栏/公告栏横向位移问题，确保遮罩覆盖滚动条且页面无抖动，同时统一模态滚动锁行为。
todos:
  - id: scan-impact
    content: 用[subagent:code-explorer]复核滚动锁调用链和影响面
    status: completed
  - id: unify-search-lock
    content: 改SearchModal复用useScrollLock并补全卸载解锁
    status: completed
    dependencies:
      - scan-impact
  - id: fix-fixed-compensation
    content: 给default与AnnouncementBanner添加滚动条补偿样式
    status: completed
    dependencies:
      - scan-impact
  - id: regression-verify
    content: 回归登录搜索确认弹窗，验证无抖动且遮罩覆盖滚动条
    status: completed
    dependencies:
      - unify-search-lock
      - fix-fixed-compensation
---

## User Requirements

- 修复用户端打开/关闭登录模态框时页面抖动问题，重点是导航栏与公告栏不要横向位移。
- 保持“模态遮罩覆盖滚动条区域”的视觉效果，不出现右侧滚动条裸露。
- 登录模态框与搜索模态框在滚动锁行为上保持一致，避免一个抖动、一个不抖动的体验割裂。

## Product Overview

- 当任意全屏模态打开时，页面主内容与顶部固定区域保持稳定，不发生左右跳动。
- 模态遮罩完整覆盖视口（包含原滚动条位置），关闭后页面滚动状态正确恢复。

## Core Features

- 统一模态滚动锁逻辑，保证打开/关闭过程稳定。
- 为固定定位顶部区域提供滚动条宽度补偿，消除局部位移。
- 处理多模态/快速开关/组件卸载场景下的滚动锁释放，避免残留锁定状态。

## Tech Stack Selection

- 沿用现有用户端技术栈与模式：Nuxt 4 + Vue 3 + TypeScript + SCSS。
- 复用现有滚动锁能力（`weblog-user/composables/useScrollLock.ts`），不引入新框架或新状态库。

## Implementation Approach

- 采用“统一滚动锁入口 + 固定元素补偿”的策略：让搜索模态与登录/确认弹窗都走同一套 `lockScroll/unlockScroll` 计数锁；固定定位的导航栏与公告栏消费 `--scrollbar-width` 做右侧补偿。
- 该方案在不改动现有模态结构的前提下，直接修复位移根因（fixed 元素未补偿）并解决行为不一致（搜索模态单独锁 body）。
- 复杂度为 O(1)：仅样式变量读写与少量 watch 分支；性能瓶颈低，无额外渲染遍历。

## Implementation Notes (Execution Details)

- 保持 `useScrollLock` 的引用计数语义，避免多个弹窗并存时提前解锁。
- SearchModal 改造时使用局部 `locked` 标记，防止重复 `lock/unlock`。
- 不做无关重构（如登录表单逻辑），将改动限制在滚动锁链路与顶部 fixed 样式。
- 错误恢复优先：组件卸载时兜底解锁，确保不会遗留 `overflow/padding` 状态。

## Architecture Design

- 现有链路：弹窗可见状态变化 → 滚动锁逻辑 → 文档滚动条状态变化 → 顶部 fixed 区域布局受影响。
- 调整后链路：  
1) 全部模态统一调用 `useScrollLock`；
2) `useScrollLock` 继续维护 `--scrollbar-width`；
3) `default.vue` 与 `AnnouncementBanner.vue` 消费该变量补偿 fixed 宽度变化。
- 该改动与现有架构一致，不新增模块层级。

## Directory Structure

## Directory Structure Summary

本次为已有项目问题修复，聚焦滚动锁一致性与 fixed 顶栏补偿，最小化改动范围。

- `c:/weblog/weblog-user/components/SearchModal.vue`  `[MODIFY]`  
目的：统一搜索模态的滚动锁行为。
功能：替换 `document.body.style.overflow` 方案，改为复用 `useScrollLock`。
要求：处理打开、关闭、卸载三类生命周期，避免重复锁定或漏解锁。

- `c:/weblog/weblog-user/layouts/default.vue`  `[MODIFY]`  
目的：修复导航栏在滚动锁期间的横向位移。
功能：为 `.navbar`（及必要内部容器）增加基于 `--scrollbar-width` 的补偿。
要求：不影响现有透明/隐藏/移动端样式状态切换。

- `c:/weblog/weblog-user/components/AnnouncementBanner.vue`  `[MODIFY]`  
目的：修复公告栏在滚动锁期间与导航栏不同步位移。
功能：为 `.announcement-bar`（及必要内部容器）增加同源补偿。
要求：保持轮播、透明态、暗黑态与交互不受影响。

- `c:/weblog/weblog-user/composables/useScrollLock.ts`  `[MODIFY-可选]`  
目的：仅在需要时增强锁状态表达（如数据属性/注释/边界保护）。
功能：维持计数锁与变量写入一致性。
要求：保持向后兼容，不破坏 ConfirmDialog/BaseModal 现有调用。

## Agent Extensions

- **subagent: code-explorer**
- **Purpose**: 在实施前后快速扫描滚动锁调用点与受影响 fixed 样式文件，确认无遗漏。
- **Expected outcome**: 得到完整影响面清单与回归检查清单，降低改动遗漏和副作用风险。