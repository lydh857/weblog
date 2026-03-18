# 安全限流参数矩阵

## 目标

- 为用户端接口提供统一的限流基线，降低爬虫抓取、撞库、刷接口和资源消耗风险。
- 为后续按线上流量微调阈值提供基准。

## 关键基线

- `@RateLimit` 默认按 IP 计数（`perIp = true`）。
- 认证链路叠加滑块验证码：`register`、`login`、`login-by-code`、`send-code`、`forgot-password`。

## 高风险接口基线（本次落地）

| 分类 | 接口 | key | 阈值 |
|---|---|---|---|
| 认证 | `POST /api/portal/auth/login` | `login` | `5/60` |
| 认证 | `POST /api/portal/auth/login-by-code` | `loginByCode` | `5/60` |
| 认证 | `POST /api/portal/auth/forgot-password` | `forgotPassword` | `5/60` |
| 认证 | `POST /api/portal/auth/logout` | `logout` | `30/60` |
| 内容查询 | `GET /api/portal/post` | `portal-post-list` | `120/60` |
| 内容查询 | `GET /api/portal/post/{slug}` | `portal-post-detail` | `180/60` |
| 内容查询 | `GET /api/portal/ranking` | `portal-ranking` | `120/60` |
| 内容查询 | `GET /api/portal/category/tree` | `portal-category-tree` | `120/60` |
| 内容查询 | `GET /api/portal/tag/cloud` | `portal-tag-cloud` | `120/60` |
| 内容查询 | `GET /api/portal/topic/{id}/catalogs` | `portal-topic-catalogs` | `90/60` |
| 评论/互动 | `DELETE /api/portal/comment/{commentId}` | `comment-delete` | `30/60` |
| 评论/互动 | `DELETE /api/portal/comment/batch` | `comment-batch-delete` | `10/60` |
| 评论/互动 | `GET /api/portal/interaction/status/{postId}` | `interaction-status` | `180/60` |
| 访问控制 | `GET /api/portal/access/check/{postId}` | `access-check` | `120/60` |
| 广告/友链 | `GET /api/portal/advertisement` | `ad-list` | `120/60` |
| 广告/友链 | `GET /api/friend-link` | `friend-link-list` | `120/60` |
| 个人中心 | `GET /api/portal/user/profile` | `user-profile-get` | `60/60` |

## 调优建议

- 先观察 7 天按 `key` 聚合的限流命中日志，再做阈值微调。
- 命中高但真实用户多：优先上调 `capacity`。
- 命中高且机器流量特征明显：下调 `capacity` 或叠加验证门槛。
