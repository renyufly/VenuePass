# VenuePass 后端 API 文档

本文对应当前 `venuepass-server` 的实际接口。基础地址为 `http://localhost:8080`，路径均以 `/api` 开头；请求体使用 JSON。可用 Postman、Apifox 或其他 HTTP 客户端逐项发送请求。项目目前没有 Swagger/OpenAPI 页面，也没有支付网关。

## 1. 启动与鉴权

在仓库根目录执行 `docker compose up --build`。等待 `GET http://localhost:8080/actuator/health` 返回 `{"status":"UP"}` 后开始测试。Docker Compose 会启动 MySQL、Redis、Kafka 和应用；首次启动由 Flyway 建表。直接运行 JAR 时，需自行提供这三个服务，并设置数据库、Redis、Kafka 及 `OPERATOR_PASSWORD`、`CHECKER_PASSWORD` 环境变量。默认端口为 8080，可用 `PORT` 覆盖。

除注册和健康检查外，所有接口均采用 HTTP Basic Auth。在 Postman/Apifox 的 Authorization 中选择 **Basic Auth**：

| 身份 | 用户名 | 密码来源 | 权限 |
| --- | --- | --- | --- |
| 运营人员 | `operator` | `OPERATOR_PASSWORD`；Compose 默认 `change-me-operator` | 管理场馆、活动、场次、支付、退款、报表，也可核销 |
| 核销人员 | `checker` | `CHECKER_PASSWORD`；Compose 默认 `change-me-checker` | 入场核销 |
| 普通用户 | 注册时指定 | 注册时指定 | 浏览、预约、查看和取消自己的预约 |

场馆、活动、场次的浏览接口需要登录，三种身份都可访问。运营和核销账号由配置提供，无需注册；注册接口禁止使用这两个用户名。

## 2. 统一响应与错误

业务成功时返回 `code: 1`，业务失败时返回 `code: 0`。`msg` 为错误文本，`data` 为业务数据；无值时为 `null`。

```json
{"code":1,"msg":null,"data":{"id":1,"name":"城市展览馆","address":"中心路 10 号","capacity":100}}
```

```json
{"code":0,"msg":"Session sold out or closed","data":null}
```

当前全局处理器对业务异常及数据库唯一键冲突返回上述错误包，**HTTP 状态码仍可能是 200**。因此测试时应检查 `code`，不要只看 HTTP 状态码。未登录和权限不足分别由 Spring Security 处理，通常返回 HTTP 401 和 403；非法 JSON、字段类型错误及未处理的服务器异常走 Spring 默认错误响应，不保证使用统一响应包。数据库唯一键冲突返回 `Already exists`。

ID 使用整数；金额使用十进制数。时间使用不带时区的 ISO 格式，如 `2026-11-04T18:30:00`，由服务端按其本地时间比较。创建场次时应使用**服务端当前时间之后**的开始时间；核销必须在该场次的开始和结束时间之间。

## 3. 接口总览

| 方法 | 路径 | 身份 | 作用 |
| --- | --- | --- | --- |
| `POST` | `/api/accounts` | 无 | 注册普通用户 |
| `GET` | `/api/venues` | 已登录 | 列出场馆 |
| `POST` | `/api/admin/venues` | 运营 | 创建场馆 |
| `GET` | `/api/events` | 已登录 | 列出启用的活动 |
| `POST` | `/api/admin/events` | 运营 | 创建活动 |
| `GET` | `/api/events/{id}/sessions` | 已登录 | 列出活动场次 |
| `POST` | `/api/admin/sessions` | 运营 | 创建场次与库存 |
| `POST` | `/api/admin/sessions/{id}/preheat` | 运营 | 预热热门场次 Redis 库存 |
| `POST` | `/api/reservations` | 普通用户 | 提交预约 |
| `GET` | `/api/reservations` | 普通用户 | 列出自己的预约 |
| `GET` | `/api/reservations/{id}` | 普通用户 | 查看自己的预约 |
| `GET` | `/api/reservations/queue/{sessionId}` | 普通用户 | 查询自己的热门场次排队结果 |
| `POST` | `/api/reservations/{id}/cancel` | 普通用户 | 取消自己的预约 |
| `POST` | `/api/admin/reservations/{id}/payment` | 运营 | 登记外部已确认的支付 |
| `POST` | `/api/admin/reservations/{id}/refund` | 运营 | 登记外部已完成的退款 |
| `POST` | `/api/admin/sessions/{id}/cancel` | 运营 | 取消场次及其有效预约 |
| `POST` | `/api/check-in` | 核销或运营 | 使用核销码入场 |
| `GET` | `/api/admin/sessions/{id}/report` | 运营 | 查看场次报表 |
| `GET` | `/actuator/health` | 无 | 健康检查，非 `/api` 路径 |
| `GET` | `/actuator/prometheus` | 运营 | Prometheus 指标，非 `/api` 路径 |

## 4. 账号与资源

### 注册普通用户

`POST /api/accounts`，无需鉴权。

```json
{"username":"alice","password":"testpass123"}
```

`username` 只能由 3–40 个英文字母、数字或下划线组成，不能是 `operator` 或 `checker`；密码至少 8 个字符。成功的 `data` 是用户名字符串。重复用户名返回 `code: 0`、`msg: "Already exists"`。注册后用该用户名和密码进行 Basic Auth。

### 创建与查看场馆

`POST /api/admin/venues`：

```json
{"name":"城市展览馆","address":"中心路 10 号","capacity":100}
```

名称、地址不能空白，`capacity` 必须大于 0。成功的 `data` 包含 `id`、`name`、`address`、`capacity`。`GET /api/venues` 返回场馆数组，按 `id` 升序。

### 创建与查看活动

`POST /api/admin/events`：

```json
{"venueId":1,"title":"科技展","description":"周末主题展览"}
```

`venueId` 必须指向已有场馆，标题不能为空白；`description` 可为空。新活动的 `active` 为 `true`。`GET /api/events` 返回所有启用的活动，按 `id` 升序。当前未提供修改、停用或删除活动的 API。

### 创建与查看场次

`POST /api/admin/sessions`：

```json
{
  "eventId": 1,
  "startsAt": "2026-11-04T18:30:00",
  "endsAt": "2026-11-04T20:00:00",
  "price": 49.90,
  "capacity": 50,
  "hot": false
}
```

测试时请将示例时间换成服务端时间之后的值。`eventId` 必须对应启用活动；开始时间必须晚于当前时间，结束时间必须晚于开始时间；`price` 不得为负；`capacity` 至少为 1，且不能超过场馆容量。`hot` 为 `false` 时同步创建预约，为 `true` 时使用 Redis/Kafka 排队链路。成功的 `data` 包含 `id`、`eventId`、`startsAt`、`endsAt`、`price`、`totalCapacity`、`available`、`locked`、`hot`、`status`；初始 `available=totalCapacity`、`locked=0`、`status="OPEN"`。

`GET /api/events/{id}/sessions` 返回指定活动的场次数组，按开始时间排序；未找到活动或没有场次时返回空数组。

### 预热热门场次

`POST /api/admin/sessions/{id}/preheat`，请求体为空。只接受状态为 `OPEN` 的热门场次；将数据库可用库存写入 Redis，成功的 `data` 为 `"READY"`。同一场次再次预热会返回 `Session inventory already preheated`。热门场次必须先预热，再开放预约；普通场次不需要此操作。

## 5. 预约与排队

### 提交预约

`POST /api/reservations`，普通用户鉴权：

```json
{"sessionId":1,"participantName":"张三"}
```

场次必须存在、处于 `OPEN`、尚未开始；参与人姓名不能空白。同一用户同一场次只允许一条预约记录，取消后也不能再次预约同场次。普通场次使用数据库条件更新扣减库存，售罄或关闭时返回业务错误。

普通场次成功时，`data` 为预约对象，例如：

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "id": 12,
    "sessionId": 1,
    "username": "alice",
    "participantName": "张三",
    "status": "PENDING_PAYMENT",
    "amount": 49.90,
    "code": "7dbcb73e-c61c-4a1e-92e1-6ae8bd6a2a8f",
    "expiresAt": "2026-11-04T18:15:00",
    "createdAt": "2026-11-04T18:00:00"
  }
}
```

预约码虽然在待支付阶段返回，但只有状态为 `PAID` 且位于场次时间窗口内时才能核销。价格是创建预约时的场次价格快照。默认支付期限为 15 分钟，可用 `PAYMENT_MINUTES` 环境变量调整；定时任务每约 60 秒扫描一次超时预约，因此状态变化可能稍晚于 `expiresAt`。

### 热门场次排队结果

热门场次提交成功后，`data` 是字符串 `"QUEUED"`，尚不代表已生成预约。用相同普通用户身份请求 `GET /api/reservations/queue/{sessionId}`：

| `data` | 含义 |
| --- | --- |
| `"QUEUED"` | 已提交，仍在处理 |
| `"CONFIRMED:12"` | 已生成预约，冒号后为预约 ID |
| `"FAILED:..."` | 排队处理失败，冒号后为原因 |
| `null` | 该用户在此场次没有排队结果 |

确认后再通过 `GET /api/reservations/{id}` 获取预约与核销码。热门库存未预热、已售罄或同一用户重复提交时，提交接口返回 `code: 0`。队列响应超时仍可能返回 `QUEUED`，应查询排队结果，不要立即重复提交。

### 查看预约

`GET /api/reservations` 返回当前用户预约数组，按 ID 倒序。`GET /api/reservations/{id}` 返回单条预约；其他用户的 ID 与不存在的 ID 都返回 `Reservation not found`。预约对象字段见上面的提交响应。

## 6. 支付、取消与退款

### 登记支付

`POST /api/admin/reservations/{id}/payment`：

```json
{"reference":"PAY-20261104-0001"}
```

`reference` 是外部支付系统已确认交易的唯一编号，不得为空或重复。只有 `PENDING_PAYMENT` 且尚未超过 `expiresAt` 的预约可登记支付。成功的 `data` 为 `"PAID"`，预约状态变成 `PAID`。**此接口不扣款，不应在未实际支付的生产请求中调用。**

### 用户取消预约

`POST /api/reservations/{id}/cancel`，请求体为空，仅预约所属用户可调用。

| 原状态 | 新状态 / `data` | 库存 | 后续动作 |
| --- | --- | --- | --- |
| `PENDING_PAYMENT` | `CANCELLED` | 释放 1 个名额 | 无 |
| `PAID` | `REFUND_PENDING` | 释放 1 个名额 | 外部退款完成后登记退款 |

其他状态不能取消。支付超时由系统自动将待支付预约改为 `CANCELLED` 并释放名额。热门场次 Redis 名额释放由数据库重试记录保障。

### 登记退款完成

`POST /api/admin/reservations/{id}/refund`：

```json
{"reference":"REFUND-20261104-0001"}
```

仅 `REFUND_PENDING` 可处理；`reference` 是外部退款交易的唯一编号。成功的 `data` 为 `"REFUNDED"`。**此接口只记录外部已完成的退款，不发起退款。**

### 取消整个场次

`POST /api/admin/sessions/{id}/cancel`，请求体为空。仅 `OPEN` 场次可取消；成功的 `data` 为 `"CANCELLED"`。该场次中待支付预约转为 `CANCELLED`，已支付预约转为 `REFUND_PENDING`，相应库存释放；已核销预约不在批量取消范围内。已支付预约仍须在外部完成退款并逐笔调用退款登记接口。当前没有通知发送接口。

## 7. 入场核销与报表

### 核销

`POST /api/check-in`，核销人员或运营人员鉴权：

```json
{"code":"7dbcb73e-c61c-4a1e-92e1-6ae8bd6a2a8f","device":"north-gate-1"}
```

`code` 和 `device` 不能为空。预约必须为 `PAID`，场次必须为 `OPEN`，且当前服务端时间位于场次开始和结束时间之间。成功的 `data` 为 `"CHECKED_IN"`；同时记录核销人员用户名、设备和时间。再次使用同一码会返回 `Code already used or inactive`。

### 场次运营报表

`GET /api/admin/sessions/{id}/report`。存在预约时 `data` 示例：

```json
{"sessionId":1,"reservations":3,"paid":1,"checkedIn":1,"revenue":49.90}
```

`reservations` 统计该场次全部预约记录，包括已取消和退款记录；`paid` 只统计当前状态为 `PAID` 或 `CHECKED_IN` 的记录；`checkedIn` 只统计 `CHECKED_IN`；`revenue` 是当前 `PAID` 与 `CHECKED_IN` 预约金额之和，不是历史成交额。该场次没有任何预约时，`data` 为 `null`。

## 8. 建议的手工测试顺序

1. 用运营账号创建场馆、活动和普通场次，记录返回的 ID。场次容量设为 1，开始时间设为稍后的未来时间。
2. 注册两个不同用户。用户 A 提交预约，检查状态 `PENDING_PAYMENT`；用户 B 同场次提交应得到售罄错误；用户 A 重复提交应得到一人一场次限制错误。
3. 用户 A 取消待支付预约，用户 B 再提交应成功，证明库存释放。不要用用户 A 重试同场次，因为唯一预约规则仍会阻止他。
4. 运营人员为用户 B 的预约登记已确认的外部支付，检查状态 `PAID`。场次开始后由核销人员提交预约码，第一次成功、第二次失败；检查报表。
5. 新建一个热门场次，先预热，再用新用户提交预约；轮询排队结果直到得到 `CONFIRMED:{id}`，随后查询预约详情。
6. 测试支付后取消时，先确认进入 `REFUND_PENDING`，再用一个实际已完成的外部退款编号登记 `REFUNDED`。测试超时可在单独的本地环境把 `PAYMENT_MINUTES` 调小，等待到期与下一次定时扫描后检查状态和库存。

以上步骤会写入当前数据库。正式环境应使用专门的测试数据与账号。
