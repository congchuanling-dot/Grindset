# AGENTS.md

本文件面向在本仓库中工作的 AI Agent / 新协作者，说明项目定位、架构、接口、数据模型和硬性约定。改动代码前先读这里，再读 `docs/` 下对应的技术设计。

## 1. 项目是什么

Grindset 是一个「面试复习闭环」全栈应用：

```
上传简历 → AI 模拟面试（语音对话）→ 复盘报告 → 题库刷题（含编程题判题）
```

外加首页的 AI 助手对话。产品骨架是围绕「简历 → 面试 → 复盘」这条数据闭环做复习，而不是一个通用聊天工具。

技术栈：

- 后端：Spring Boot 3.4.1 / Java 21 / Maven / MyBatis-Plus 3.5.9 / MySQL 8 / Redis / JWT(jjwt) / PDFBox / Apache POI
- 消息队列：RabbitMQ 3.13 + Spring AMQP，用于简历解析异步任务（主队列 quorum + TTL 重试 + 死信队列）
- 前端：React 19 / TypeScript / Vite 8 / Tailwind CSS 4 / react-router-dom 7 / lucide-react / Monaco Editor
- 判题：本机 Docker 一次性容器（Java / Python），断网并限制 CPU、内存、进程数

## 2. 目录结构

```
Grindset/
├── AGENTS.md                  # 本文件
├── README.md                  # 快速说明（注意：端口信息已过期，见下文）
├── backend/                   # Spring Boot 后端
│   ├── src/main/java/com/grindset/backend/
│   │   ├── controller/        # HTTP 入口，只做参数校验和调用 service（auth/ 为登录子包）
│   │   ├── service/           # 业务逻辑（auth/ 为登录子包，含本地账号、身份表、GitHub/Google 登录、OAuth 凭证读取）
│   │   ├── llm/               # LLM 客户端、路由、密钥加密
│   │   ├── mapper/            # MyBatis-Plus Mapper（auth/ 为登录子包）
│   │   ├── entity/            # 数据库实体（auth/ 为登录子包）
│   │   ├── dto/               # 请求/响应对象（auth/ 为登录子包）
│   │   ├── security/          # JWT 过滤器、token 签发/刷新、refresh Cookie 写入、401/403 处理器
│   │   ├── config/            # SecurityConfig、各类 @ConfigurationProperties
│   │   └── common/            # 统一响应、异常、枚举、雪花 ID
│   └── src/main/resources/
│       ├── application.yml
│       └── db/                # llm_catalog.sql / llm_keys_balance.sql（手工执行）
├── frontend/                  # React 前端
│   └── src/
│       ├── api/               # 按业务域拆分的接口封装
│       ├── auth/              # 会话存储、路由守卫
│       ├── components/        # 简历预览、Toast 等
│       ├── layouts/           # 侧边栏外壳
│       └── pages/             # 各业务页面
├── sql/                       # 按编号顺序手工执行的建表/种子脚本
└── docs/                      # 各模块产品方案与技术设计
```

## 3. 启动与初始化

### 3.1 前置依赖

- JDK 21+、Maven 3.9+、Node.js 20+
- MySQL 8（Docker 即可）、Redis
- RabbitMQ 3.13（Docker 即可），简历异步解析依赖；未启动时 Worker 会持续重连并打印日志
- 使用编程题判题时需要本机 Docker，并提前拉取判题镜像

### 3.2 数据库

没有 Flyway / Liquibase，所有 DDL 都是手工执行。**按编号顺序执行**：

```bash
mysql -uroot -p123456 < sql/001_auth_schema.sql
mysql -uroot -p123456 < sql/002_resume_schema.sql
mysql -uroot -p123456 < sql/003_interview_schema.sql
mysql -uroot -p123456 < sql/006_question_bank_schema.sql
mysql -uroot -p123456 < sql/007_question_bank_seed.sql
mysql -uroot -p123456 < sql/008_coding_schema.sql
mysql -uroot -p123456 < sql/009_coding_seed.sql
mysql -uroot -p123456 < sql/010_oauth_client_schema.sql
mysql -uroot -p123456 < sql/011_agent_chat_schema.sql
mysql -uroot -p123456 < sql/012_resume_original_schema.sql
mysql -uroot -p123456 < sql/013_storage_config_schema.sql
mysql -uroot -p123456 < sql/014_resume_parse_mq.sql
```

大模型渠道/模型/密钥表不在 `sql/` 下，需要额外执行（**必须带 utf8mb4 参数**，否则中文展示名会乱码）：

```bash
mysql --default-character-set=utf8mb4 -uroot -p123456 grindset < backend/src/main/resources/db/llm_catalog.sql
mysql --default-character-set=utf8mb4 -uroot -p123456 grindset < backend/src/main/resources/db/llm_keys_balance.sql
```

默认连接：MySQL `localhost:3306/grindset`，root/123456；Redis `localhost:6379`，无密码（见 `backend/src/main/resources/application.yml`）。

RabbitMQ 本地初始化（Docker）：

```bash
docker run -d --name grindset-rabbitmq -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=grindset -e RABBITMQ_DEFAULT_PASS=grindset123 \
  -e RABBITMQ_DEFAULT_VHOST=/grindset rabbitmq:3.13-management
```

管理台 `http://localhost:15672`。队列/交换机由应用启动时自动声明，无需手工创建。

### 3.3 后端

```bash
cd backend
mvn spring-boot:run
```

**实际端口是 8081**（`application.yml` 中 `server.port: 8081`）。README 里写的 8080 是过期信息，不要被误导。健康检查：`GET http://localhost:8081/api/health`。

同一个 jar 支持两种启动角色（用 `WORKER_ENABLED` 切换，不需要拆微服务）：

- **api**：`WORKER_ENABLED=false`，只接收 HTTP 请求和投递解析消息，不消费
- **worker**：`WORKER_ENABLED=true`（默认），既提供 HTTP 也消费简历解析消息；本地开发单进程用这个即可

### 3.4 前端

```bash
cd frontend
npm install
npm run dev
```

默认 `http://localhost:5173`，Vite 将 `/api` 代理到 `http://localhost:8081`（可用 `VITE_PROXY_TARGET` 覆盖）。

### 3.5 常用环境变量

| 变量 | 作用 |
| --- | --- |
| `JWT_SECRET` | access token 签名密钥 |
| `ADMIN_TOKEN` | 管理接口 `X-Admin-Token`；未配置时 `/api/admin/**` 全部 403 |
| `SECRET_MASTER_KEY` | 加密数据库里 LLM API Key、OAuth client_secret 与 OSS AccessKey 的主密钥；换掉会导致已存密文全部解不开 |
| `DEEPSEEK_API_KEY` / `DASHSCOPE_API_KEY` / `ZHIPU_API_KEY` / `MINIMAX_API_KEY` / `ARK_API_KEY` | 各厂商兜底 key（正常应通过管理接口写入数据库 key 池） |
| `ALIYUN_OSS_ENDPOINT` / `ALIYUN_OSS_BUCKET` / `ALIYUN_OSS_ACCESS_KEY_ID` / `ALIYUN_OSS_ACCESS_KEY_SECRET` | 简历原件的 OSS 兜底配置；正式环境应通过 `/api/admin/storage/oss` 写入数据库，这里只给本地开发用 |
| `ALIYUN_OSS_RESUME_PREFIX` / `ALIYUN_OSS_URL_EXPIRE_SECONDS` | 可选：原件对象前缀（默认 `resumes`）与签名 URL 有效期（默认 3600 秒） |
| `RESUME_LLM_PROVIDER` / `RESUME_LLM_MODEL` | 简历解析固定使用的厂商/模型；两者都不配则自动选第一个可用 |
| `INTERVIEW_LLM_PROVIDER` / `INTERVIEW_LLM_MODEL` | AI 面试/评分/复盘/题库评分固定使用；规则同上 |
| `VOLC_ASR_API_KEY` / `VOLC_ASR_RESOURCE_ID` | 豆包流式语音识别（面试间语音回答） |
| `VOLC_TTS_API_KEY` / `VOLC_TTS_RESOURCE_ID` / `VOLC_TTS_SPEAKER` | 豆包语音合成（面试官播报）；`VOLC_TTS_API_KEY` 未配时回落到 `VOLC_ASR_API_KEY` |
| `JUDGE_JAVA_IMAGE` / `JUDGE_PYTHON_IMAGE` | 判题镜像名 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ 地址，默认 `localhost:5672` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` / `RABBITMQ_VHOST` | RabbitMQ 凭证与 vhost，默认 `grindset` / `grindset123` / `/grindset` |
| `WORKER_ENABLED` | 是否消费简历解析消息。api 角色设 `false`，worker 角色设 `true`（默认） |
| `RESUME_UPLOAD_PERMITS` | 单实例上传并发闸门，默认 8 |
| `RESUME_PARSE_MAX_ATTEMPTS` / `RESUME_PARSE_LOCK_TTL_MINUTES` | 最大执行次数（默认 4，即首次 + 3 次重试）、解析锁 TTL 分钟数（默认 10） |
| `RESUME_PARSE_STUCK_MINUTES` / `RESUME_PARSE_RECONCILE_MS` | 卡住判定分钟数（默认 10）、补偿扫描间隔毫秒（默认 300000） |
| `VITE_PROXY_TARGET` | 前端开发代理目标 |

## 4. 后端架构

### 4.1 分层约定

`Controller → Service → Mapper → MySQL`，Controller 不写业务逻辑；跨表事务放在 Service，使用 `TransactionTemplate`（项目里基本不用 `@Transactional` 注解，遵循现有风格）。

统一响应：

```json
{ "code": 0, "message": "ok", "data": {} }
```

- `code = 0` 成功；非 0 为业务错误
- 业务错误抛 `BizException(code, message)`，由 `GlobalExceptionHandler` 转成统一响应
- 鉴权失败/无权限分别由 `RestAuthenticationEntryPoint`、`RestAccessDeniedHandler` 输出 JSON

主键统一由 `SnowflakeIdGenerator` 生成 `BIGINT`，**数据库不用自增**。返回给前端的长 ID 在 DTO 里用 `String`（如 `resumeId`、`questionId`），避免 JS Number 精度丢失。

### 4.2 认证与鉴权

- 账号即邮箱，密码 BCrypt 存储（`users` + `user_identities`，provider=`LOCAL`）
- access token：JWT，默认 30 分钟，放响应体，前端存内存/sessionStorage 并放在 `Authorization: Bearer`
- refresh token：放 HttpOnly Cookie，路径限定 `/api/auth`，默认 7 天，存 Redis
- `/api/auth/refresh` 采用**轮换**：旧 refresh 立即作废；`/api/auth/logout` 吊销并清 Cookie
- 除 `/api/health`、`/api/auth/{login,register,refresh,logout}`、`/api/auth/{github,google}/**`、`/api/admin/**` 外，其余接口都必须登录
- 第三方登录凭证存放在 `oauth_client_config` 表（`client_secret` 用 `SecretCipher` 的 AES-GCM 加密，AAD 为行 id），**不放配置文件和环境变量**；换主密钥或改行 id 会导致密文解不开
- 需要当前用户的接口用 `@AuthenticationPrincipal LoginUser loginUser` 取 `userId`，**不要接受前端传入的 userId**
- 管理接口不接 JWT，只校验请求头 `X-Admin-Token`（`AdminGuard`）
- 简历/面试等资源的所有权校验失败统一返回 404，避免泄露资源是否存在

### 4.3 流式输出（SSE）

对话和 AI 出题使用 Spring `SseEmitter`，跑在虚拟线程上（`spring.threads.virtual.enabled=true`）：

- `POST /api/chat`：事件 `delta`（`{"text":"..."}`）→ `done` → 结束；失败发 `error`（`{"message":"..."}`）
- `POST /api/agent/chat`：首帧 `conversation`（`{"conversationId":"...","title":"..."}`），随后 `status` / `tool_call` / `tool_result` / `delta`，结束 `done` 带会话 ID 与最新标题
- `POST /api/interviews/{id}/questions`：同上，`done` 额外带 `{"questionId":"...","seq":"..."}`
- `POST /api/tts`：请求体 `{text}`；事件 `delta`（`{"audio":"<base64 PCM>"}`，24k/s16le/单声道）→ `done`；失败发 `error`

前端不用 `EventSource`，而是 `fetch` + `ReadableStream` 解析（见 `frontend/src/api/chat.ts`），因为需要带 `Authorization` 头。

### 4.4 并发与事务约定

- 单实例内用 `ConcurrentHashMap<Long, ReentrantLock>` 按用户/题目串行化，`tryLock()` 失败返回 409/429
- 慢 LLM 调用放在数据库事务外，先拿到结果再开短事务落库
- 判题使用单线程 `ExecutorService` 串行执行，提交先落库 `PENDING`，前端轮询
- 简历上传用 `Semaphore` 限制单实例并发（默认 8），临时文件落盘、用后即删

### 4.5 异步任务（RabbitMQ）

简历解析走 RabbitMQ，生产者与消费者在同一个应用里，靠 `WORKER_ENABLED` 控制角色：

- 生产者：`ResumeService.upload()` 落库 `parse_status=0` 后调用 `ResumeParsePublisher.publish()`，消息只带元数据（`resumeId` / `userId` / `objectKey` / `fileType` / `fileName` / `attempt` / `traceId`），不带文件字节
- 消费者：`ResumeParseConsumer` 手动 ack，Redis 锁 `resume:parse:{resumeId}`（TTL 10 分钟）做幂等；从 OSS 重新下载原件 → 抽文本 → LLM → 事务落库
- 拓扑（`RabbitResumeConfig`）：
  - `resume.exchange`（direct）→ `resume.parse.queue`（quorum、持久化、DLX=`resume.dlx`）
  - `resume.parse.dlq`（quorum）：超过最大尝试次数或永久失败的消息
  - `resume.parse.retry.30s` / `.2m` / `.10m`（classic TTL）：TTL 到期通过 DLX 回到主交换机
- 重试：`attempt` 是 **1-based 执行次数**（首次=1）；可重试错误按 `attempt+1` 投递下一档重试（2→30s、3→2m、4→10m），达到 `max-attempts`（默认 4）后标记失败并进 DLQ
- 锁：Redis `resume:parse:{resumeId}`，TTL 由 `RESUME_PARSE_LOCK_TTL_MINUTES` 控制；释放时用 Lua 比对 token，避免误删其它 worker 的锁
- 补偿：`ResumeParseReconciler` 每 5 分钟扫描 `parse_status=0` 且 `updated_at` 超过 10 分钟、`parse_attempt<3` 的记录，重新投递或标记失败
- 可靠性：事务提交后再发消息；publisher confirm 只记日志；暂时不做 outbox，靠补偿任务兜底
- OSS 不可用或投递失败时，上传接口回退为同步解析，保证功能可用

## 5. 前端架构

### 5.1 路由与页面

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/login` | `LoginPage` | 登录/注册 |
| `/` | `ChatPage` | AI 助手对话（SSE 流式） |
| `/resume` | `ResumePage` | 上传简历、列表、预览 |
| `/questions` | `QuestionBankLayout` | 题库工作台：左侧题目列表 + 右侧详情（问答题） |
| `/questions/:id` | `QuestionDetailPage` | 问答题详情（右侧内容区） |
| `/questions/coding` | `CodingListPage` | 编程题列表（左筛选 + 右表格） |
| `/questions/coding/:id` | `CodingWorkspace` | 编程题工作台：题面 + 编辑器左右分栏 |
| `/interview` | `InterviewPage` | 面试配置入口（简历、岗位、轮次、风格、题数） |
| `/interview/:sessionId` | `InterviewRoomPage` | 全屏面试间：自己摄像头 + VRM 面试官，语音对话，不套 AppLayout |
| `/records` | `InterviewRecordsPage` | 面试记录列表、按简历筛选、删除 |

除 `/login`、`/auth/callback`、`/interview/:sessionId` 外都套在 `RequireAuth` + `AppLayout` 下；面试间只套 `RequireAuth`。

### 5.2 请求层

- `src/api/http.ts`：`authFetch` 自动带 access token；401 时**单飞**调用 refresh 并重试一次，避免并发刷新轮换两次
- 浏览器请求统一 `credentials: 'include'`，保证 refresh Cookie 被携带
- 各业务接口在 `src/api/*.ts` 中封装，页面不直接拼 URL

## 6. 核心业务链路

### 6.1 简历解析

`ResumeController → ResumeService → ResumeParsePublisher → RabbitMQ → ResumeParseConsumer → ResumeParseService`（原件 `ResumeOriginalStorage`）

1. 上传 PDF/DOCX/MD/TXT（单文件 ≤ 10MB），`MultipartFile.transferTo()` 流式落临时文件，堆里不保留整份文件
2. 原件流式上传**阿里云 OSS 私有桶**，`resumes.original_object_key` 只存对象 key
3. 落库 `parse_status=0`、`parse_attempt=0` 后投递解析消息，**接口立即返回**，前端每 2 秒轮询 `GET /api/resumes/{id}`
4. 消费者从 OSS 重新下载原件，用 PDFBox / POI / 文本读取做**确定性文本抽取**（不交给 LLM）
5. LLM 只负责把纯文本转成固定 JSON 结构（失败重试一次）
6. 事务落库通用章节模型：`resumes` → `resume_sections` → `resume_section_items`，技能单独 `resume_skills`；成功后 `parse_status=1`
7. 失败：可重试错误按 30s / 2m / 10m 重试，达到最大执行次数（默认 4，首次 + 3 次重试）后标记 `parse_status=2` 并写 `error_msg`，消息进死信队列
8. OSS 未配置、上传失败或消息投递失败时，回退为上传请求内同步解析，不阻断功能
9. PDF 预览走 `GET /api/resumes/{id}/original` 由后端流式代理（OSS 默认域名会强制下载，不能直接给 iframe）；下载与分享用带时效的签名 URL（`originalUrl` / `originalDownloadUrl`）
10. **不保存原始纯文本**；章节类型见 `common/ResumeSectionType`，未知类型统一降级 `OTHER` 并保留原章节标题；新增类型只改枚举，不改表

### 6.2 AI 面试

`InterviewController → InterviewService → InterviewLlmService`

- 创建会话：必须选一份已解析成功的简历，岗位/风格（`pressure`/`middle`/`relaxed`）/轮次/侧重点/题数（3/5/8）
- 出题：`POST /{id}/questions` 流式生成下一道主问题，落 `interview_questions` + `interview_messages`
- 答题：`POST /{id}/answers` 一次请求内完成「一句话点评 + 是否追问 + 是否收束本题」
  - 一道大题 = 主问题 + 最多 3 次追问；题与题之间不共享上下文
  - 收束时写入本题评分（1~5）、知识点、总评、建议
- 面试间前端链路：出题（SSE）→ TTS 播报 → 自动开麦收音（结束语/5 秒无新文本自动提交）→ 提交 → 追问/下一题；答完自动生成复盘
- 复盘：`POST /{id}/finish` 生成 0~100 总分与完整报告 JSON，存 `report_json`
- 会话状态：`0 进行中 / 1 已结束`；题目状态：`0 未开始 / 1 提问中 / 2 作答中 / 3 已收束 / 4 已放弃`

### 6.3 题库

`QuestionBankService`

- 题型 `type`：`BASICS` / `SCENARIO` / `PROJECT` / `CODE`
- 判题形态 `judge_mode`：`TEXT` 问答 / `CODING` 编程
- 问答题作答：调用 AI 评分（命中/遗漏要点、点评、建议），写 `question_attempts` 与 `user_question_progress`
- 进度筛选状态：`TODO` / `DONE` / `WRONG`
- 管理端导入：`POST /api/admin/questions/import`，按题干 SHA-256 幂等

### 6.4 编程题判题

- 每道编程题**同时支持两种模式**，前端在工作台顶部切换，提交时用 `codeMode` 指定：
  - `ACM`：用户写 `Main` / `main()`，从标准输入读、向标准输出写；用例存 `case_mode='ACM'` 的 stdin 文本
  - `LEETCODE`：用户只写 `class Solution` 的函数；判题器生成驱动读 JSON 参数、调函数、按 JSON 比较返回值；用例存 `case_mode='LEETCODE'` 的 JSON 参数
- 函数签名存在 `question_bank.function_name` / `param_types`(JSON) / `return_type`；`starter_code` 是 ACM 初始代码，`starter_code_function` 是力扣初始代码
- 支持类型：`int` `long` `double` `boolean` `char` `String`、对应一维数组、`int[][]` `char[][]` `String[][]`、`List<Integer>` `List<String>` `List<List<Integer>>`
- 提交异步：先落 `PENDING` 再进单线程判题队列，前端轮询 `GET /api/questions/submissions/{id}`

`CodingService → JudgeService`

- 语言：`JAVA` / `PYTHON`；ACM 模式固定 `Main.java` / `main.py`，函数模式固定 `Solution.java` / `solution.py` 加自动生成的驱动
- 每次运行起一次性 Docker 容器，断网，限制 CPU/内存/PID，超时即杀
- `POST /{id}/run` 同步运行**例题**（不写历史）；`POST /{id}/submissions` 异步判题并立即返回 `PENDING`
- `GET /api/questions/submissions/{submissionId}` 前端轮询
- 同一用户同一时刻只允许一个运行/提交任务，否则 429
- 判题结束只回写做题进度（`user_question_progress`），不做跨题聚合统计
- 判题状态：`PENDING` / `RUNNING` / `ACCEPTED` / `WRONG_ANSWER` / `COMPILE_ERROR` / `RUNTIME_ERROR` / `TIME_LIMIT_EXCEEDED` / `SYSTEM_ERROR`

## 7. 接口清单

所有接口都以 `/api` 开头；除标注「公开」「管理」外都需要 JWT。

### 认证 `/api/auth`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/register` | 公开。邮箱 + 密码（8~16 位且含字母数字）注册，返回 access token 并下发 refresh Cookie |
| POST | `/login` | 公开。登录，返回同上 |
| POST | `/refresh` | 公开。用 Cookie 中的 refresh 轮换新 token 对 |
| POST | `/logout` | 公开。吊销 refresh 并清 Cookie |
| GET | `/me` | 当前用户资料（id/nickname/email） |
| GET | `/github/authorize` | 公开。跳转 GitHub 授权页 |
| GET | `/github/callback` | 公开。GitHub 回调，写 refresh Cookie 后跳前端 |
| POST | `/github/exchange` | 公开。一次性 code 换本系统令牌 |
| GET | `/google/authorize` | 公开。跳转 Google 授权页 |
| GET | `/google/callback` | 公开。Google 回调，写 refresh Cookie 后跳前端 |
| POST | `/google/exchange` | 公开。一次性 code 换本系统令牌 |

### AI 助手 `/api/chat`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/models` | 可用厂商 + 模型 + 余额 + 可用 key 数（只含已配置 key 的渠道） |
| POST | `/` | 请求体 `{provider, model, messages:[{role,content}]}`；SSE 流式对话，system 提示词由服务端注入 |

### 首页 Agent `/api/agent`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/chat` | 请求体 `{conversationId?, provider, model, message}`；SSE 流式对话。无 `conversationId` 时懒建会话，有则从库里重放历史 |
| GET | `/conversations` | 当前用户会话列表（最多 100 条，按最后消息时间倒序） |
| GET | `/conversations/{id}` | 会话详情，消息已按一轮工具调用合并成展示结构 |
| PATCH | `/conversations/{id}` | 重命名，请求体 `{title}` |
| DELETE | `/conversations/{id}` | 删除会话及其全部消息 |

### 简历 `/api/resumes`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/` | multipart 字段 `file`，上传并**异步解析**；立即返回 `parseStatus=0`，前端轮询详情 |
| GET | `/` | 当前用户简历列表 |
| GET | `/{id}` | 简历详情（章节 + 条目 + 技能） |
| GET | `/{id}/original` | 代理返回原件字节（PDF 内嵌预览用，inline 响应头） |
| DELETE | `/{id}` | 删除简历及子表数据 |

### AI 面试 `/api/interviews`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/` | 创建会话 `{resumeId, position, style, round, focus[], questionCount}` |
| GET | `/` | 历史会话列表；可选 `resumeIds` 过滤 |
| GET | `/{id}` | 会话详情（刷新页面恢复） |
| POST | `/{id}/questions` | SSE 生成下一道主问题 |
| POST | `/{id}/answers` | 提交回答 `{content}`，返回点评 + 追问/收束/评分 |
| POST | `/{id}/finish` | 生成复盘报告 |
| DELETE | `/{id}` | 删除会话 |

### 语音 `/api/tts`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/` | 请求体 `{text}`（≤500 字）；SSE 流式返回 PCM 音频块，事件 `delta`/`done`/`error` |

面试官播报由前端 `useInterviewerVoice` 消费：base64 PCM → WebAudio 排队播放 → AnalyserNode 实时驱动 VRM 口型；支持随时打断（停播报 + 立即开麦）。

### 题库 `/api/questions`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 列表 + 统计；可选 `domain`、`type`、`difficulty`、`status`、`keyword`、`judgeMode`、`page`、`size` |
| GET | `/stats` | 只取做题统计；可选 `judgeMode=TEXT/CODING` |
| GET | `/facets` | 领域与题型的动态筛选项，带题目数量；可选 `judgeMode` |
| GET | `/random` | 随机一题，可选 `excludeId` 与 `judgeMode` |
| GET | `/{id}` | 题目详情 + 我的作答历史（含所属模式的题号 `seq`） |
| GET | `/{id}/neighbors` | 同一模式（问答题/编程题）下的前后相邻题 |
| GET | `/{id}/attempts` | 某题作答历史 |
| POST | `/{id}/attempts` | 提交问答题 `{answer}`，AI 评分 |
| GET | `/{id}/coding` | 编程题详情（题面 + 例题 + 初始代码 + 时限 + 判题模式与函数签名） |
| POST | `/{id}/run` | 运行例题 `{language, code, codeMode}`，同步返回逐用例结果 |
| POST | `/{id}/submissions` | 提交判题 `{language, code, codeMode}`，返回 PENDING |
| GET | `/submissions/{submissionId}` | 轮询判题结果 |
| GET | `/{id}/submissions` | 我的提交历史 |
| GET | `/images?key=` | 公开。题库配图代理，从 OSS `questions/` 前缀流式读取 |

### 管理接口（需要 `X-Admin-Token`，不需要 JWT）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/llm/channels` | 渠道列表：余额、key 掩码、失败次数、冷却时间 |
| POST | `/api/admin/llm/channels/{id}/keys` | 给渠道新增 key（加密存储，返回掩码） |
| PATCH | `/api/admin/llm/keys/{id}` | 启用（0）/ 禁用（2）某把 key |
| PUT | `/api/admin/llm/channels/{id}/balance` | 手工维护余额，来源改为 `MANUAL` |
| POST | `/api/admin/llm/channels/{id}/balance/refresh` | 立即拉取余额（目前仅 DeepSeek） |
| POST | `/api/admin/questions/import` | JSON 批量导入题库，按内容哈希幂等 |
| POST | `/api/admin/questions/images` | 上传题库配图到 OSS `questions/`，返回代理 URL |
| GET | `/api/admin/storage/oss` | 查看简历原件 OSS 配置（AccessKey 只返回掩码） |
| PUT | `/api/admin/storage/oss` | 新增/更新 OSS 配置（AK/SK 加密入库；不传则保留原密文） |

## 8. 大模型接入架构

- 渠道、模型目录、key 池都在数据库：`llm_channels`、`llm_models`、`llm_keys`
- key 用 AES-GCM 密文存储（`llm/SecretCipher`），主密钥来自 `SECRET_MASTER_KEY`
- `ModelCatalogService` 每次请求直接查库聚合，改库即时生效（无缓存）
- `LlmRouter` 按「渠道优先级 + key 最久未用」依次尝试候选：
  - **流式**：只有尚未吐出第一个字时才允许换 key；已输出后失败直接上报，避免内容重复
  - **非流式**：失败可直接换下一把 key
- 失败会 `markFailed()`，连续失败进入冷却，鉴权失败自动停用
- 场景与模型配置：
  - 简历解析：`app.resume.llm.*`，都是空则自动选第一个可用厂商/模型
  - AI 面试、面试评分、复盘、题库评分：`app.interview.llm.*`，规则同上
  - 首页对话：前端选择的 provider/model
- 所有结构化输出的 LLM 调用都要求固定 JSON，并做一次解析失败重试；再失败按业务错误返回

## 9. 数据模型

| 脚本 | 表 | 用途 |
| --- | --- | --- |
| `001_auth_schema.sql` | `users`、`user_identities` | 用户与登录身份（LOCAL 密码 / 预留 OAuth） |
| `002_resume_schema.sql` | `resumes`、`resume_sections`、`resume_section_items`、`resume_skills` | 通用章节式简历结构 |
| `003_interview_schema.sql` | `interview_sessions`、`interview_questions`、`interview_messages` | 面试会话、题目线程、线程内消息 |
| `006_question_bank_schema.sql` | `question_bank`、`user_question_progress`、`question_attempts` | 题库、做题进度、作答记录 |
| `007_question_bank_seed.sql` | — | 约 60 道多领域种子题 |
| `008_coding_schema.sql` | `question_test_cases`、`question_submissions` + `question_bank` 扩展列 | 编程题用例与提交 |
| `009_coding_seed.sql` | — | 编程题种子数据 |
| `010_oauth_client_schema.sql` | `oauth_client_config` | 第三方登录客户端凭证（secret 加密存储） |
| `011_agent_chat_schema.sql` | `agent_conversations`、`agent_messages` | 首页 Agent 会话与完整消息流（可重放、可恢复） |
| `012_resume_original_schema.sql` | `resumes.original_object_key` | 简历原件在 OSS 中的对象 key |
| `013_storage_config_schema.sql` | `storage_config` | 外部存储配置（OSS AccessKey 密文，AAD 绑定行 id） |
| `014_resume_parse_mq.sql` | `resumes` 扩展列 | `parse_attempt`、`parse_started_at`、`parse_finished_at`（异步解析状态） |
| `db/llm_catalog.sql` | `llm_channels`、`llm_models`、`llm_keys` | 大模型渠道/模型/密钥池 |
| `db/llm_keys_balance.sql` | 余额相关扩展列 | 渠道余额维护字段 |

约定：

- 表之间**没有物理外键**，关联与级联由代码保证（删除简历/面试时要手动清理子表）
- 主键均为雪花 `BIGINT`
- 列表/快照类内容用 JSON 列（知识点、要点、章节条目、报告等）
- 所有建表脚本尽量可重复执行（`IF NOT EXISTS` / `INSERT IGNORE` / 动态加列）

## 10. 硬性约定（改代码时必须遵守）

1. 简历原件存**阿里云 OSS 私有桶**，库中只存 `original_object_key`；未配置 OSS 或上传失败必须自动跳过，不能阻断解析。**不保存原始纯文本**。
2. 简历章节使用通用模型，新增经历类型只改枚举，不改表结构。
3. 文本抽取只用 PDFBox / POI 等确定性库，**不要用 LLM 做文本抽取**。
4. LLM 只负责「纯文本 → 固定 JSON」，未知章节统一 `OTHER` 并保留原标题。
5. 资源归属校验失败统一返回 404，包括简历、面试。
6. 面试记录接口的用户身份只从 JWT 读取，不接受前端传 userId。
7. 会话全程落库；刷新页面能通过 `GET /api/interviews/{id}` 恢复。
8. 题库第一版是力扣刷题形态，不做 PDF/爬虫导入、管理后台 UI、代码判题以外的扩展。
9. 简历预览组件在简历管理页和 AI 面试页共用同一实现，两处样式/行为必须一致。
10. 闭集字段（文件类型、章节类型、候选人阶段、题型、判题形态、题目来源、判题语言/状态、面试风格等）在**实体层用枚举**；DTO 与前端契约保持字符串或数字。数据库值必须与枚举 `name()` 一致，例外如 `InterviewStyle` 用小写 `@EnumValue`。
11. 简历解析是**异步任务**：上传只负责落库 + 投递消息，不在请求线程里调 LLM；消费者必须幂等（Redis 锁 + `parse_status` 判断），可重试错误走 TTL 重试队列，超过次数进死信队列。**不保存原始纯文本**，worker 从 OSS 重新下载原件。
12. 面试间的口型、打断、字幕都走前端状态机（出题 → 播报 → 收音 → 提交）；语音识别与合成只能通过后端 `/api/asr/stream`、`/api/tts` 代理，**API Key 不能下发到浏览器**。

## 11. 常见改动指引

**新增一个业务接口**

1. `controller/` 加方法，用 `@AuthenticationPrincipal LoginUser` 取用户
2. 业务放 `service/`，长 ID 转 `String` 再出 DTO
3. 前端在 `src/api/` 对应模块加封装，页面调用 `authFetch`

**新增一张表**

1. 在 `sql/` 追加编号脚本（可重复执行）
2. `entity/` + `mapper/`（继承 MyBatis-Plus `BaseMapper`）
3. 主键用 `SnowflakeIdGenerator`，不要用 `AUTO_INCREMENT`

**新增一个 LLM 场景**

1. 在对应 `XxxLlmService` 中拼 messages + 固定 JSON 协议
2. 通过 `ModelCatalogService` + `LlmRouter` 选目标，不要直连 HTTP
3. 非流式用 `complete()`，流式用 `stream()`；解析失败重试一次

**新增前端页面**

1. `pages/` 建组件，`App.tsx` 注册路由（默认放在 `AppLayout` 内）
2. 侧边栏入口在 `layouts/AppLayout.tsx` 的 `navItems`

## 12. 已知坑与注意事项

- **端口**：后端实际 8081，README 的 8080 是过期信息；前端代理也只指向 8081。
- **SQL 手工执行**：没有自动迁移，新环境必须按顺序跑完 `sql/` + `db/` 脚本。
- **LLM 表脚本编码**：执行 `llm_catalog.sql` 必须带 `--default-character-set=utf8mb4`。
- **管理接口默认关闭**：`ADMIN_TOKEN` 为空时所有 `/api/admin/**` 返回 403。
- **判题需要 Docker**：Docker 未启动、镜像不存在时提交会失败；镜像名可在 `application.yml` 或环境变量里改。
- **长 ID 精度**：所有返回给前端的雪花 ID 用字符串；前端不要用 `Number` 解析 ID。
- **SSE 参数错误**：对话/出题在响应头发出后再出错，只能走 `error` 事件，前端要处理。
- **单实例假设**：锁和判题队列都是进程内实现，暂不支持多实例水平扩展。
- **JSON 列**：修改实体 JSON 字段时注意 MyBatis-Plus 的类型处理器配置，避免写入变成字符串。
- **时间**：数据库时区 `Asia/Shanghai`，展示格式统一 `yyyy-MM-dd HH:mm`。
- **OAuth 凭证在库里**：GitHub / Google 的 client_secret 是 `oauth_client_config` 里的 AES-GCM 密文，AAD 绑定该行 `id`。换 secret 必须用同一主密钥重新加密后 `UPDATE`，不要只改明文；改 `id` 也会解不开。
- **OSS 凭证在库里**：`storage_config` 的 AccessKey 同样是 AES-GCM 密文，AAD 绑定行 `id`；数据库配置优先，环境变量仅兜底。未配置或上传失败时原件自动跳过，解析照常。
- **签名 URL 会过期**：默认 1 小时，页面长时间停留后原件预览 403，重新拉 `GET /api/resumes/{id}` 换新链接。
- **RabbitMQ 必须可连**：简历异步解析依赖 RabbitMQ；未启动时应用仍能启动，但消息发不出去会回退同步解析。检查 `resume.parse.queue` 的 consumers 与 messages。
- **worker 角色**：`WORKER_ENABLED=false` 的 api 实例只投递不消费；只部署 api 不部署 worker 会导致简历一直停在 `parse_status=0`。
- **死信队列要监控**：`resume.parse.dlq` 有消息说明解析连续失败或消息非法，需要看日志并人工重投。
- **解析字段**：`parse_attempt` 是 1-based 执行次数，`parse_started_at` / `parse_finished_at` 用于排查卡住任务；补偿任务依赖它们。

## 13. 文档索引（docs/）

| 文档 | 内容 |
| --- | --- |
| `登录模块-技术设计.md` | JWT + refresh 轮换、表结构、接口约定 |
| `简历解析-技术设计.md` | 文本抽取、LLM JSON 协议、通用章节模型、LLM 渠道表 |
| `AI面试-技术设计.md` | 会话状态机、出题/追问/评分/复盘、SSE |
| `AI面试官形象与面试间-技术选型.md` | VRM 形象、VRMA 坐姿、TTS 口型、面试间交互（含 Live2D 选型过程） |
| `面试复习闭环-PRD.md` | 整体产品闭环 |
| `编程题判题-技术设计.md` | Docker 沙箱、判题流程、异步提交与轮询 |
| `JWT鉴权-技术设计.md` | 鉴权细节补充 |
| `首页Agent会话持久化-技术设计.md` | 会话/消息表、落库与重放、LLM 标题、并发锁、会话侧边栏 |
| `用户钱包与昵称-技术设计.md` | 钱包/流水表、注册送 1 元、AI 扣费、余额闸门、昵称修改 |

修改任何模块前，先读对应设计文档；实现与文档冲突时，以代码现状为准并同步更新文档。
