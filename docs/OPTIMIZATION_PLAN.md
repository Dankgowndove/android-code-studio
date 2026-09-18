# Android Code Studio 优化路线图

> 生成日期：2026-09-05 ｜ 基于 `feature/ai-agent-update` 分支分析与逐行核实
> 更新日期：2026-09-19 ｜ 追加「镜像功能收尾」执行记录（见第六节），基于 `feature/cn-mirror-download`
> 配套文档：仓库根 `PROJECT_ANALYSIS.md`（架构全览）
> 原则：按阶段推进，每阶段独立提交、独立验证；未标注「已执行」的条目均为**建议后续做**，避免一次性大改。

## 一、现状摘要

| 项目 | 值 |
|------|-----|
| 技术栈 | Kotlin/Java 多模块 Android 工程（AGP 8.13.0 / Kotlin 2.1.0） |
| 规模 | ~1,350 Kotlin + ~470 Java 文件，模块见 `PROJECT_ANALYSIS.md` 第二节 |
| 近期主线 | `feature/ai-agent-update`：AI 提供商/模型 2026 版本更新、自定义提供商（OpenAI/Claude 双协议）、签名配置、弃用 API 清理 |
| AI 代码位置 | `core/app/src/main/java/com/tom/rv2ide/artificial/`（agents / completion / dialogs / parser / models / rules / secrets） |
| 工作区状态 | 干净（本轮 4 项修复未提交前除外） |

## 二、阶段路线图

### P0 — 低风险清理（已执行：本轮 4 项）

- [x] **自定义提供商删除 Bug**：`CustomProviderManager.remove()` 补充 `AIAgentRegistry.unregister(id)`（原删除后注册工厂残留，提供商仍可被选中/自动切换）
- [x] **弃用 API**：`ProviderSwitchDialog` 迁移至 `androidx.preference.PreferenceManager`
- [x] **调试噪音**：删除 `SuggestionView` 的 `"CLICKED!!!"` 日志及 4 个 provider 的整段 Request body 日志（含完整对话内容）
- [x] **死代码**：删除 `artificial/models/ApplyModel.kt`（312 行，全项目零引用，内含潜在索引失效 Bug）

**其余候选（改动极小，随时可做）**：
- [ ] `agents/Agents.kt:171-172`：`setAgent` 中两次 `sp.edit()…apply()` 合并为一次
- [ ] `settings.gradle.kts:75` 配置期 `println` 移除（见 P1）
- [ ] `AIAgentManager` 各 AIAgent 子类中同构的状态类日志统一门控

### P1 — 构建性能（需在设备/CI 上验证耗时对比）

- [ ] 启用 `org.gradle.configuration-cache`（`gradle.properties:29` 现被注释）：
  - 前置条件：移除 `settings.gradle.kts:71-77` 配置期的 `println`/环境变量读取（configuration-cache 不允许配置期不确定行为）
  - 先用 `--configuration-cache` 试运行 + `--configuration-cache-problems=warn` 观察问题清单
- [ ] `android.nonTransitiveRClass=true`（`gradle.properties:25` 带 TODO 注释）：需同步清理跨模块直接 `R.` 引用，逐步验证
- [ ] 审计 `composite-builds/` 中构建期依赖（JDT / javac 等）是否需要随版本升级
- [ ] CI 维护（2026-09-05 构建告警）：`actions/checkout|cache|setup-java|upload-artifact@v4` 升级到 v5（Node 20 已弃用）、`setup-java` 的 `adopt` 发行版改 `temurin`、`styfle/cancel-workflow-action` 升级；arm64/v7a 产物路径正常，universal/x86_64 路径无产物属预期（可移除避免告警）

### P2 — 结构去重（收益最大、风险中等，建议单独立项）

- [ ] **7 处重复定义收敛**：`ConversationMessage`（OpenAI / DeepSeek / Anthropic / Gemini / Grok / LocalLLM / CustomProvider 各一份）、`FileModification` 等数据类提取为共享定义（建议放 `artificial/agents/model/` 或复用 secrets/models 层）
- [ ] **提供商请求引擎统一**：OpenAI / DeepSeek / Grok / Anthropic / LocalLLM 5 份近同实现（~2,300 行：请求构建、错误处理、历史、重试）收敛为一个引擎；`CustomProvider.kt` 已有可用的 OpenAI / Claude 双协议客户端可作参照；Gemini 官方 SDK 分支保持不变
- [ ] **公共行为上提基类**：retry / attempt / history / modification 状态与逻辑从各 `AIAgent` 提取到共享父类，只保留各协议差异层

### P3 — 功能一致性（需产品决策，勿在无确认时改动）

- [ ] **文件写入权限开关失效**：`AIAgentManager.kt:59-60` 无条件 `setFileWriteEnabled(true)` + `setRequireConfirmation(false)`，使已存在的权限确认 UI（`artificial/dialogs/AIPermissionDialog.kt` + `res/layout/dialog_ai_permissions.xml`）完全不可达 → 改为读取持久化开关并接入对话框
- [ ] **FILE_TO_MODIFY 双解析对齐**：`AIAgentManager.processModifications`（291-357 行）手写解析与 `parser/SnippetParser.kt` 的解析/清洗逻辑重复且行为可能发散 → 统一入口
- [ ] 重试语义确认：`executeRequest` 重试不回滚已部分成功的文件写入（产品决策：改为整体回滚 or 文档化现状）

### 不动清单（看起来可优化，实际勿动）

- `agents/Agents.kt` 中的 2026 模型 ID（gpt-5.6-terra、deepseek-v4-flash-vision-exp 等）：内部一致（注册表/默认值/回退均匹配），「纠正」会破坏功能
- Gemini 用官方 SDK、其余手写 HTTP 的并存：有意的架构取舍，统一属于 P2 大重构而非修复
- `AIAgentManager.executeRequest` 的空 catch / 防御性默认值：行为一致且有意
- 上游 AndroidIDE 原样代码（布局 ImageView 缺 contentDescription 等）：与上游同步策略另行决定

## 三、风险说明与回滚

- 本轮已执行项全部为 LOW 风险：1-2 行逻辑补全 / import 迁移 / 日志删除 / 死代码删除，均可独立 `git revert`（ApplyModel.kt 可在 git 历史找回）
- P2 属大型重构：建议每提取一个共享类型即编译一次 `:core:app`，避免一次大 diff
- P3 涉及产品行为，改动前需确认期望语义（默认允许写入？首次询问？）
- P1 的 configuration-cache 若在设备端（AndroidIDE 环境）出现兼容问题，可随时恢复注释状态

## 六、执行记录：镜像功能收尾（2026-09-19，`feature/cn-mirror-download`）

### 已执行

| 提交 | 内容 |
|------|------|
| `0b3462e` | 修复 CN 镜像提交引入的**编译错误**（非 suspend 回调中调用 `withContext`）；`downloadFile` 改为 suspend + 可挂起进度回调，增加截断检测/取消传播/百分比节流；展开 `updater.json` 的 `{baseUrl}`/`{versionName}` 模板；统一 ABI 变体选择（`selectVariantForDevice`） |
| `6db147a` | 删除重构后失去唯一调用点的 `updater_http_error`、`updater_changelog_error`（3 个 locale） |
| `717c585` | `fetchText` 引入总体时限（清单 20s / 变更日志 12s，单次尝试 ≥3s），替换原先「每个候选各 10s」的最坏 30s 行为 |
| `a77d643` | 设置 → 通用 新增「国内镜像加速」开关（与引导页同一 `use_mirror_downloads` key）；`DownloadMirrors` 从 `core/app` 移到 `core/common`（包名不变），`core/common` 显式声明 coroutines |
| `625fca3` | Kotlin 语言服务器（`java/lsp-setup`）的清单与安装包下载接入镜像 |
| （本次） | 更新本文档 |

- 验证方式：云端 CI 手动 dispatch（`gh workflow run "Build Android Code Studio"`）。本 fork 的 **push 与 pull_request 事件都不触发 Actions**（历史运行全部为 `workflow_dispatch`），因此每次改动后需手动触发。
- PR：`feature/cn-mirror-download` → `dev`（30 个提交），合并前需设备冒烟：设置页开关同步、更新检查/下载、LSP 更新。

### 未执行 / 已知限制

- **首次安装（`idesetup`）无法接入镜像**：该安装器是 `termux/application/src/main/assets/data/common/{arm,arm64}/idesetup` 中的预编译 ELF（ARC4 加密载荷 + `ptrace` 反调试），仓库内无源码；上游 `androidide-tools` 的 `idesetup` 是 bash 脚本且只暴露短选项 `-m <manifest-url>`，与本 App 的长选项接口不匹配。设置项文案已明确该范围限制。
- 镜像策略只对 GitHub 域生效；Gradle 发行包（`services.gradle.org`）、`dl.google.com` Maven、Termux APT 不在范围内。
- `x86_64`/`x86` 无 `idesetup` 资产，这两个架构的自动安装本就不受支持（既有缺陷）。

### 下一步（P2 重构，独立分支 `feature/ai-agent-core-unification`）

按「先删除、再共享、后统一」的顺序推进，每步一个提交 + CI 编译 + 设备冒烟：

1. **测试地基**：`core/app` 增加 `testImplementation(libs.tests.junit)` 与 `src/test`，workflow 增加 `testDebugUnitTest` 步骤（目前 `core/app` 无任何测试源集/依赖）。
2. **删死代码**：6 份从未被调用的 `parseAndApplyModifications()` 与 6 份 `FileModification`（约 −330 行）；活路径是 `AIAgentManager.processModifications` + `BaseFileModification`。
3. **共享模型**：7 份 `ConversationMessage`（字段完全一致）提取到 `artificial/agents/model/`。
4. **抽纯逻辑 + 基类**：`PromptBuilder`（~70 行 ×5）、`CorrectionDetector`（×6）、`ConversationHistory`（×7）与 7 份相同的 modification/计数器/writeFile 逻辑提到 `AbstractAIAgent`；注意 **CustomProvider 的 prompt 不含历史/文件内容，且其工厂返回缓存单例**，不可顺手统一。
5. **统一 HTTP 引擎**：以 `CustomProvider` 的双协议客户端为蓝本抽出 `AiHttpClient`（endpoint/鉴权头/system 位置/响应取值四个参数轴），迁移 OpenAI/DeepSeek/Grok/Anthropic/LocalLLM/CustomProvider，统一到 OkHttp 并按现值保留超时；Gemini 保持官方 SDK。

风险：AI 层无运行时自动化验证，CI 只能保证编译与纯逻辑单测，**每个提供商的设备冒烟是硬门槛**；逐个提供商单独提交以便二分回滚。
