# AI 编码简报

## 权威来源
- API：Контракт_API_полный (1).doc、Контракт_API_кратко (1).doc。冲突时以“不存在 worksCount/hasPhotos/plate/mileage/photos”为准。
- 旧 ChatGPT 对话只用于理解产品意图，不是 API 权威。
- 产品语义冲突不擅自决定，写 TODO(contract-clarification) 并在交付中列出。

## 硬约束
1. 认证只用 Basic；不用 Bearer/JWT/Idempotency-Key 作为服务端保证。
2. 照片 JSON base64，不用 multipart；GET 二进制不走 envelope，但带 Basic 头。
3. DTO 不声明 worksCount/hasPhotos/plate/mileage/photos；UI 不渲染。
4. workId/employeeId 不硬编码 UUID；不新增工作行、不新增执行者（Q1/Q2 未确认）。
5. 编辑已有工作行：lineId 原样，workId=work.id，executors 原样回传 employeeId+percent，不带 shopId（Q4 未确认，保守必须回传）。
6. 写操作统一走 SyncQueue 单一路径，不做在线直发双路径（阶段 6 落地；之前写操作可先直发但标 TODO(stage 6)）。
7. 401：清内存+清密文+SyncQueueGate.pauseForAuth()+回登录；Worker 不重试 401。
8. isMaster 仅展示，不用于权限。
9. 旧 ApiClient/SessionStore/MechanicRepository 标 legacy，不注入；新代码不得引用。
10. 日志不打印 Authorization/base64/password/contentBase64。
11. 不改 backend，不接旧 backend 运行期回退，不降 compileSdk，不改 AGP/Compose 版本。
12. 不伪造 1C 字段或端点；合同没有的标 TODO。

## 待组长确认
- Q1 workId 目录：未确认 → 不新增工作行。
- Q2 employeeId 目录：未确认 → 不新增/改派执行者。
- Q3 worksCount/hasPhotos：按不存在实现。
- Q4 executors 不传是否清空：未确认 → 保守原样回传。
- 产品语义：开始/完成 vs PUT+post；“3 个最近车辆”定义；“5 张照片”验收口径；幂等验收改为“先 GET 重读”；states 仅 current/upcoming/closed 筛选是否可接受。

## 当前阶段
- 阶段 2–7 完成（UI 横幅、登出清理、只读联调清单）。
- 照片/订单/目录走 Room + SyncQueue；Writes 单一路径；迁移真机验证待跑。

## 交付格式（强制）
1. 变更表：文件 | 动作 | 一句话。
2. 关键 diff：只贴被改行，每处 ≤5 行，不贴完整文件。
3. 测试：命令 + 通过/失败数。
4. 未做/TODO：列表。
5. 需我决策：≤3 条。
总长 ≤150 行；不复述需求，不贴已完成代码全文。
