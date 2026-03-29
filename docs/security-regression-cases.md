# Weblog 安全回归用例集

## 1. 目标

- 固定高风险攻击样本，避免修复后被回归破坏。
- 作为预发布与上线前安全冒烟的标准输入。

---

## 2. 接口类用例

## A. 认证与验证码

1. `POST /api/portal/auth/check-email` 无 `X-Captcha-Token`
   - 预期：拒绝（`40001`）
2. `POST /api/portal/auth/check-email` 伪造 `X-Captcha-Token`
   - 预期：拒绝（`40002`）
3. `POST /api/portal/auth/check-email` 同 IP+域名高频请求
   - 预期：触发限流（`429`）
4. `POST /api/portal/auth/login` 同邮箱高频失败
   - 预期：触发用户维度限流（`429`）
5. `POST /api/portal/auth/send-code` 同邮箱同场景高频请求
   - 预期：触发用户维度限流（`429`）

## B. 访问控制

1. `POST /api/portal/access/read/{invalidPostId}`
   - 预期：拒绝（`40402`）
2. 同会话读取 3 篇后切换 `User-Agent`
   - 预期：`readCount` 不重置，仍不可继续免费阅读
3. 新会话（新 cookie）访问
   - 预期：重新计数（策略预期行为）

## C. 搜索

1. `GET /api/search` 关键词长度 > 80
   - 预期：参数错误（`400`）
2. `GET /api/search` 控制字符关键词
   - 预期：参数错误（`400`）

---

## 3. XSS/注入类样本

以下 payload 应在保存/渲染后不可执行脚本：

1. `<script>alert(1)</script>`
2. `<img src=x onerror=alert(1)>`
3. `<a href="javascript:alert(1)">x</a>`
4. `<iframe srcdoc="<script>alert(1)</script>"></iframe>`
5. `<svg onload=alert(1)>` 
6. `<math><maction actiontype="statusline" xlink:href="javascript:alert(1)">x</maction></math>`
7. `<a href="data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==">x</a>`
8. ````markdown
   ```html
   <img src=x onerror=alert(1)>
   ```
   ````

验证点：

- 用户端文章详情 `weblog-user/pages/post/[slug].vue`
- 用户端专题详情 `weblog-user/pages/topic/[id].vue`
- 管理端 AI 流渲染 `weblog-admin/components/ai/AiStreamRenderer.vue`

---

## 4. 环境与配置类用例

1. `prod` + `blog.security.dev-bypass-enabled=true`
   - 预期：应用启动失败
2. `prod` 未配置 `TRUSTED_PROXY_IPS`
   - 预期：应用启动失败
3. `NUXT_CSP_STAGE=dual`
   - 预期：同时下发 `Content-Security-Policy` 与 `Content-Security-Policy-Report-Only`

---

## 5. 建议执行频率

- 每次安全相关改动：全量执行。
- 每次发版前：至少执行第 2 节和第 4 节。
- 每周巡检：第 3 节抽样 + 线上告警回放。
