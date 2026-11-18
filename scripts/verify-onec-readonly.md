# B1 — 只读联调清单（1C `/hs/mobile/v1/`）

前置：测试账号、可访问的 1C 地址。**不要提交真实密码**。

```bash
export ONEC_BASE="http://178.47.32.166/test_mobile_1/hs/mobile/v1"
read -r -p "login: " ONEC_LOGIN
read -r -s -p "password: " ONEC_PASSWORD; echo
AUTH=$(printf '%s:%s' "$ONEC_LOGIN" "$ONEC_PASSWORD" | base64)
```

## 1. 登录（无 body）
```bash
curl -sS -i -u "$ONEC_LOGIN:$ONEC_PASSWORD" "$ONEC_BASE/auth/login"
```
检查：HTTP 200；`{"result":{"user":{...}},"error":null}`；无 JWT/token 字段。

## 2. 订单列表/详情
```bash
curl -sS -u "$ONEC_LOGIN:$ONEC_PASSWORD" "$ONEC_BASE/orders?limit=5&offset=0"
ID=... # 从上一响应复制
curl -sS -u "$ONEC_LOGIN:$ONEC_PASSWORD" "$ONEC_BASE/orders/$ID"
```
检查：envelope；`state.code` 是 UUID；vehicle 可为 null；日期原样；金额两位小数；无 worksCount/hasPhotos/plate/mileage/photos。

## 3. 目录
```bash
curl -sS -u "$ONEC_LOGIN:$ONEC_PASSWORD" "$ONEC_BASE/states"
curl -sS -u "$ONEC_LOGIN:$ONEC_PASSWORD" "$ONEC_BASE/defects?search=амортизатор"
```

## 4. 照片元数据（不写）
```bash
curl -sS -u "$ONEC_LOGIN:$ONEC_PASSWORD" "$ONEC_BASE/orders/$ID/photos"
```
检查：items/total；fileName/uploadedBy/uploadedAt/canDelete。

## 5. 错误路径
```bash
curl -sS -i -u "bad:bad" "$ONEC_BASE/auth/login"   # 期望 401，可能空 body
curl -sS -i -u "$ONEC_LOGIN:$ONEC_PASSWORD" "$ONEC_BASE/orders/00000000-0000-0000-0000-000000000000"
```
检查：401 不被强制解析 envelope；404 返回 `NOT_FOUND`。

## 记录
| 检查项 | 结果 |
|---|---|
| envelope 结构 | 未执行 |
| 401 空 body | 未执行 |
| 日期带/不带时区 | 未执行 |
| vehicle=null | 未执行 |
| state.code UUID | 未执行 |
| 金额小数位 | 未执行 |

**本轮无测试账号，B1 未执行。** 禁止在未授权/不可回滚环境执行写操作。
