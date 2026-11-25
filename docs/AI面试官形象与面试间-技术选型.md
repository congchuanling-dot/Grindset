# AI 面试官形象与面试间 · 技术选型调研

> 版本：v0.1
> 作者：从传领
> 日期：2026-11-25
> 关联：[AI面试-技术设计](AI面试-技术设计.md)、[面试复习闭环-PRD](面试复习闭环-PRD.md)
> 一句话：为「模拟面试」场景选型 AI 面试官形象（二次元）渲染方案与面试间（摄像头 / 语音 / 共享屏幕）交互方案，并给出分期落地路线。

---

## 1. 背景与目标

### 1.1 需求

在现有「AI 面试闭环」（文本线程式对话）基础上新增**面试间**：开始面试后界面呈现两位参与者。

- **左侧 = 自己**：本地摄像头画面，支持开关摄像头；语音、共享屏幕第一版先占位。
- **右侧 = 面试官**：需要有形象、会说话、嘴能跟着语音动的「二次元形象」。

### 1.2 关键诉求

1. 面试官形象**成本要低**（云端数字人路线因成本过高被否决）。
2. 面试官说话时**嘴型要对得上**，理想状态是「看嘴型就能读出在说什么」。

### 1.3 约束

| 维度 | 说明 |
|---|---|
| 形象风格 | 二次元（2D / 3D 均可） |
| 运行位置 | 优先浏览器端渲染，避免云端渲染 + 独立 GPU 推流 |
| 技术栈契合 | 前端 React 19 + Vite + TS；后端 Spring Boot；已接入火山引擎流式 ASR |
| 成本 | 尽量免费 / 低成本，避免按天、按小时的云数智人计费 |

---

## 2. 核心结论（TL;DR）

1. **VAM（Virt-A-Mate）不是省钱的二次元方案**。它把「云端渲染成本」换成了「自建 GPU 渲染机 + 复杂推流链路」，成本反而更高、集成更难，且其内容生态存在合规风险，**不推荐**。
2. 二次元形象首选 **Live2D（2D）**，备选 **VRM + VRoid（3D）**。两者都能在浏览器 WebGL 直接渲染，零推流、低成本，且 `pixi-live2d-display` / `@pixiv/three-vrm` 均为前端成熟库。
3. 口型分两档：**幅度级**（张嘴幅度随音量变化，第一版够用）与**音素级**（每个音节对应标准口型，「看嘴型能读字」）。模拟面试场景**建议第一版先做幅度级跑通闭环，音素级作为二期增强**。
4. TTS **继续用火山引擎**（与现有 ASR 同厂商、同套凭证体系）。官方接口支持字级 / 音素级时间戳；音素级口型的真正难点不在 TTS，而在「**音素 → 口型参数**」的映射层。

---

## 3. 面试官形象方案对比

| 方案 | 形象类型 | 浏览器运行 | 口型同步 | 成本 | 适配度 |
|---|---|---|---|---|---|
| **Live2D** | 2D 二次元（纸片人，VTuber 业界标准） | ✅ WebGL 直接渲染 | ✅ 官方 MotionSync 音频驱动；可扩展音素级 | 模型免费 / 几十~几百元；SDK 个人免费 | ⭐⭐⭐⭐⭐ 首选 |
| **VRM + VRoid** | 3D 二次元（可 360° 转动） | ✅ three.js 直接渲染 | ✅ 内置 5 个标准口型（aa/ih/ou/ee/oh） | 全部免费（VRoid Studio 免费建模） | ⭐⭐⭐⭐ 备选 |
| **VAM（Virt-A-Mate）** | 3D 写实 | ❌ 桌面独占，需独立 GPU 机 + 推流 | 需外挂插件 | 高（GPU 机 + 复杂集成） | ⭐ 不推荐 |
| **云数智人（腾讯等）** | 数字人 | 云渲染推流 | ✅ | 交互约 140 元/天、播报约 1200 元/小时 | ⭐⭐ 仍是数字人，成本未降 |

### 3.1 Live2D（推荐首选）

- **适配理由**：就是需求里的「二次元形象」，业界虚拟主播 / 纸片人面试官的标准方案；学术界的 AI 面试助手原型也大多用它（Live2D Cubism + TTS + 唇形同步）。
- **技术栈**：PixiJS + `pixi-live2d-display`（或社区维护的 `@jannchie/pixi-live2d-display`、`@laplace.live/pixijs-live2d`），npm 直接安装，在 React 组件里挂载 canvas 渲染。
- **口型同步**：官方 MotionSync 由音频驱动嘴型；也提供 `model.speak(audioUrl)` / `startMicrophoneLipSync()` 等能力。
- **注意点**：
  - `live2dcubismcore.min.js` 是闭源核心，**不能通过 npm 安装**，需从官方 SDK 下载后自行托管静态资源。
  - 商用超营收阈值需购买 Release License；个人 / 小规模使用免费。

### 3.2 VRM + VRoid（3D 备选）

- **适配理由**：VRoid Studio 可免费建模并导出 VRM（开放标准，glTF 扩展）；`@pixiv/three-vrm`（MIT）在浏览器渲染。
- **优势**：VRM 1.0 内置标准表情集，含 5 个口型（aa / ih / ou / ee / oh，与 ARKit 口型对齐）+ 眨眼 + 视线 + 头发物理；模型可免费商用（以 pixiv 授权条款为准）。
- **适用**：想要 3D、能转头给面试者「注视感」时选它。

### 3.3 为什么选 Live2D 而不是 VRM

| 维度 | Live2D | VRM |
|---|---|---|
| 观感 | 二次元纸片人，最贴近需求预期 | 3D 二次元，可转动 |
| 音素级口型成本 | 每个模型 Mouth Form 参数自定义，映射需按模型调 | 内置 5 个标准口型，映射标准现成，**更省事** |
| 资源体积 | 小 | 较大（模型 + 贴图） |
| 生态成熟度 | VTuber 生态最成熟 | 3D / 游戏生态更成熟 |

结论：**观感优先选 Live2D，口型工程省事选 VRM**。本项目更看重「二次元观感 + 低成本」，故首选 Live2D。

---

## 4. VAM 方案专项评估（为什么不推荐）

| 维度 | 实际情况 |
|---|---|
| 运行形态 | 桌面独占的 VR 沙盒应用（Virt-A-Mate，2017 年前后发布），**无浏览器 SDK**，不能直接跑在现有 React 前端里 |
| 硬件成本 | 需要独立 GPU 渲染机，社区反馈面捕 + 实时渲染最低 RTX 3060 级别、推荐 RTX 3090 24G 起 |
| 接入链路 | VAM 渲染画面 → 虚拟摄像头 / OBS 窗口采集 → 再走 RTMP / WebRTC 推流回网页，链路长且重 |
| 动作控制 | 依赖社区插件（如 BusDriver）+ UDP 协议外挂控制，非官方支持能力 |
| 合规风险 | VAM 的社区内容生态偏成人向，作为面试产品形象存在内容合规隐患 |

> 补充：网上另有「虎牙 VAM（Vivid Avatar Model）」，是虎牙的实时数字人大模型（DiT 架构，一张照片生成实时说话数字人），与本项目讨论的 VAM 不是一回事；其未开源，本质仍属数字人路线，成本未降。

**判定**：VAM 不是「便宜的二次元方案」，而是「把数字人成本转嫁给自己运维 GPU 渲染机 + 推流链路」，对本项目是负优化。

---

## 5. 口型同步方案

### 5.1 两档口型

| 档次 | 效果 | 能否「看嘴型猜出在说什么」 |
|---|---|---|
| **幅度级**（Live2D 默认 MotionSync） | 张嘴幅度随音量变化 | ❌ 只能看出「在说话」 |
| **音素级（viseme）** | 每个音节对应标准口型（啊 / 衣 / 乌 / 诶 / 哦…） | ✅ 可以 |

### 5.2 音素级口型的技术链路

```
TTS 合成音频时，同时返回「音素 / viseme 时间戳」
    例：[{phone:"n", start:0.00, end:0.05}, {phone:"i", start:0.05, end:0.12}, ...]
                    ↓
前端拿到音素序列 → 按时间逐帧映射到嘴部参数（Live2D Mouth Form / VRM 口型）
                    ↓
模型嘴巴按音素精确变化
```

**关键认知**：「看嘴型读字」的核心不在形象（Live2D / VRM 都行），而在「**TTS 音素时间戳 + 音素到口型的映射**」。

- **Live2D 路线**：模型需带 Mouth Form（口型）参数，前端自己写「音素 → Mouth Form」映射表。
- **VRM 路线**：内置 5 个标准口型（对齐 ARKit），映射标准现成，更省事。

### 5.3 建模注意事项

- 若**自己找人建模**：建模时须开启 MotionSync 相关功能并导出对应参数（VTuber 商业模型通常默认开启）。
- 若**使用现成模型**：务必确认模型带 MotionSync / 口型参数，避免只买基础表情模型。

---

## 6. TTS 选型

| 维度 | 火山引擎（推荐） | 微软 Azure |
|---|---|---|
| 音素级时间戳 | ✅ 支持（异步长文本 `enable_subtitle=3`；音频技术 HTTP API `enable_timestamp=true` 返回 `phonemes`） | ✅ 支持，直接返回 22 个标准 viseme ID |
| 输出内容 | 原始**音素**（如 `C0n`），需自行映射口型 | 已归类的 **viseme ID**，可直接驱动 |
| 中文支持 | 原生中文 | viseme 能力以英文 locale 为主，中文需额外确认 |
| 生态 | **与现有流式 ASR 同厂商、同套 API Key** | 需引入第二个云服务 |

**结论**：

- TTS 继续用火山引擎，与 [VolcAsrClient.java](../backend/src/main/java/com/grindset/backend/asr/VolcAsrClient.java) 保持同厂商、同凭证体系。
- 火山给「原始音素」、Azure 给「归好类的 viseme」，**两者都躲不掉「音素 / viseme → Live2D 口型参数」这层映射**；差别只是火山要自建映射表（音素粒度更细），Azure 直接给 22 类现成口径。
- 相关接口（以官方最新文档为准）：
  - 单向流式语音合成 HTTP：`POST https://openspeech.bytedance.com/api/v3/tts/unidirectional`，`audio_params.enable_subtitle` 可返回字级时间戳。
  - 小模型异步长文本合成：`enable_subtitle=3` 返回音素级时间戳。
  - 音频技术 HTTP API：`audio_config.enable_timestamp=true` 返回 `words`（字级）+ `phonemes`（音素级）时间戳。

---

## 7. 面试间技术方案（第一版）

**核心认知：第一版不需要真正的 WebRTC P2P**，因为对面是 AI 形象而不是真人。

```
┌────────────────────────────────────────────────────┐
│  面试间布局（React 组件）                             │
│  ┌──────────────┐        ┌──────────────┐          │
│  │  自己（左）    │        │ 面试官（右）   │          │
│  │ <video> 本地   │        │ Live2D canvas │          │
│  │ 摄像头画面     │        │ 待机 + 口型动画 │          │
│  └──────────────┘        └──────────────┘          │
│  底部控制栏：📷 摄像头开关  🎤 语音（占位）  🖥️ 共享（占位） │
└────────────────────────────────────────────────────┘
```

| 能力 | 第一版做法 | 后续 |
|---|---|---|
| 自己摄像头 | `navigator.mediaDevices.getUserMedia({ video: true })` 取本地流，`<video>` 展示；开关 = 切换 `track.enabled`（或 stop / 重新获取） | — |
| 语音开关 | 占位按钮，先持有 audio track | 接现有火山流式 ASR 做语音答题 |
| 共享屏幕 | 占位按钮 | `getDisplayMedia()` |
| 面试官形象 | Live2D canvas 渲染，待机动画 | TTS 音频驱动口型 |
| 面试官说话 | LLM 出题文本 → 火山 TTS → 播音频 + 驱动口型 | 全双工、可打断 |
| 多人 / 连麦 | 不需要 | 真多人面试官才引入 `RTCPeerConnection` + SFU（LiveKit / mediasoup） |

**设计取舍**：

- 左半是标准 `getUserMedia`（浏览器原生能力，零后端依赖）。
- 右半是 Live2D canvas（纯前端渲染，零推流）。
- 第一版**不引入 `RTCPeerConnection`**，把复杂度降到最低；`InterviewSessionPage` 现有的文本线程对话可作为面试间右侧的消息区，或另起 `InterviewRoomPage`。

**浏览器权限注意**：

- `getUserMedia` 要求 HTTPS 或 `localhost`，本地开发 5173 可直接用。
- 用户拒绝授权时必须有降级 UI（显示占位头像 + 提示）。

---

## 8. 推荐实施路线（分期）

```
第一步（第一版，先跑通闭环）：
  形象 = Live2D（二次元纸片人）
  TTS  = 火山流式 TTS（延续火山全家桶）
  口型 = 幅度级（音频驱动张嘴，Live2D 原生 MotionSync）
  面试间 = 左 getUserMedia 摄像头 + 右 Live2D canvas + 控制栏
          （摄像头真做；语音、共享屏幕先占位）

第二步（第二版，可选增强）：
  TTS  = 火山 TTS 开启 enable_timestamp / enable_subtitle=3 拿音素时间戳
  前端 = 建「中文音素 → Live2D Mouth Form 口型」映射表，逐帧驱动
  效果 = 看嘴型能读出字

第三步（可选）：
  语音答题闭环（火山 ASR）→ 全双工对话 / 打断
  如需真实多人面试官，再引入 WebRTC + SFU
```

---

## 9. 成本估算

| 项 | 成本 |
|---|---|
| Live2D SDK | 个人 / 小规模免费；商用超营收阈值需 Release License |
| Live2D 模型 | 可用免费模型，或建模师定制（几十~几百元起，视复杂度） |
| VRM 路线 | VRoid Studio 免费建模，模型可免费商用（以授权条款为准） |
| 火山 TTS | 按字符计费（如 LAS 3 元/万字符、seed-tts-2.0 按字符版），以官方最新价为准 |
| 云数智人（参照） | 交互约 140 元/天、播报约 1200 元/小时（不采用） |
| VAM（参照） | GPU 渲染机 + 推流链路（不采用） |

> 以上均为调研时公开信息，正式立项前需以官方最新报价为准。

---

## 10. 风险与待确认项

1. **火山 TTS 流式接口的音素级时间戳**：需实测实时（短文本）场景能否稳定返回音素级时间戳，还是仅异步长文本接口支持；这是音素级口型能否落地的前提。
2. **Live2D 模型授权**：选定模型后需核实 MotionSync 与 Mouth Form 参数是否齐全、商用授权范围。
3. **中文音素 → 口型映射表**：若上音素级，需自建中文音素到 Live2D Mouth Form 的映射，是主要开发工作量与质量风险点。
4. **音频与口型时序对齐**：浏览器播放时钟与驱动口型的时钟需对齐，避免「嘴先动、声音后到」；建议以 `AudioContext.currentTime` 为统一时间基准。
5. **静态资源托管**：Live2D 闭源核心文件需自行托管，注意与现有前端构建 / 部署流程的集成。

---

## 11. 参考资料

- Live2D Cubism SDK MotionSync（Web）：https://docs.live2d.com/en/cubism-sdk-manual/use-on-scene-motion-sync-web/
- pixi-live2d-display（npm）：https://www.npmjs.com/package/@jannchie/pixi-live2d-display
- VRoid Studio：https://www.vroid.com/studio
- @pixiv/three-vrm：https://github.com/pixiv/three-vrm
- 火山引擎 单向流式语音合成 HTTP：https://www.volcengine.com/docs/6561/2528925?lang=zh
- 火山引擎 小模型异步长文本合成（`enable_subtitle=3`）：https://docs.volcengine.com/docs/doubao-voice/api-interface-documentation-2?lang=zh
- 火山引擎 音频技术 HTTP API（`enable_timestamp`）：https://www.volcengine.com/docs/6489/71999?lang=zh
- Azure 语音服务 viseme（22 个 viseme ID）：https://docs.azure.cn/ja-jp/ai-services/speech-service/how-to-speech-synthesis-viseme
- 腾讯云智能数智人价格：https://cloud.tencent.com/document/product/1240/101944
