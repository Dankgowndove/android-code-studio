# Android Code Studio (AndroidIDE) 项目完整分析

## 一、项目概览

| 项目属性 | 值 |
|---------|-----|
| **项目名** | AndroidCodeStudio (又称 AndroidIDE) |
| **许可证** | GPL v3 |
| **包名** | `com.tom.rv2ide` |
| **构建系统** | Gradle (Kotlin DSL) |
| **AGP 版本** | 8.13.0 |
| **Kotlin 版本** | 2.1.0 |
| **Target 平台** | Android (arm64-v8a / armeabi-v7a) |
| **源码总览** | ~1,350+ Kotlin 文件 + ~470 Java 文件 |

## 二、顶层模块架构图

```
android-code-studio/
├── 📱 core/                    ← 核心模块 (应用主入口 + 公共层)
│   ├── app/                    ← 【主应用】Android 应用入口 (APK)
│   ├── common/                 ← 公共基础组件 (UI主题/工具/Shell)
│   ├── actions/                ← 动作/命令系统
│   ├── projects/               ← 项目解析 (Android/Java 项目模型)
│   ├── projectdata/            ← 项目数据存取
│   ├── resources/              ← 【多语言资源】国际化字符串
│   ├── indexing-api/           ← 索引 API (代码索引定义)
│   ├── indexing-core/          ← 索引核心实现
│   ├── lsp-api/                ← LSP (语言服务器协议) API
│   └── lsp-models/             ← LSP 数据模型
│
├── ✏️ editor/                  ← 编辑器模块
│   ├── api/                    ← 编辑器抽象接口
│   ├── impl/                   ← 编辑器核心实现 (基于 sora-editor)
│   ├── lexers/                 ← 词法分析器
│   └── treesitter/             ← Tree-sitter 语法解析
│
├── ☕ java/                     ← Java/Kotlin 语言支持
│   ├── javac-services/         ← Javac 编译服务
│   ├── lsp/                    ← Java/Kotlin LSP 实现
│   └── lsp-setup/              ← LSP 配置/启动
│
├── 📄 xml/                     ← XML 处理模块
│   ├── aaptcompiler/           ← AAPT2 编译器 (Android 资源编译)
│   ├── dom/                    ← XML DOM 解析
│   ├── lsp/                    ← XML LSP 实现
│   ├── resources-api/          ← 资源 API
│   └── utils/                  ← XML 工具
│
├── 🛠️ tooling/                 ← Gradle 构建工具
│   ├── api/                    ← Tooling API
│   ├── impl/                   ← Tooling 实现 (Gradle 连接/同步)
│   ├── model/                  ← Tooling 模型
│   ├── builder-model-impl/     ← Builder 模型实现
│   ├── plugin/                 ← Gradle 插件
│   ├── plugin-config/          ← 插件配置
│   └── events/                 ← 构建事件
│
├── 🖥️ termux/                  ← 终端模块 (Termux 集成)
│   ├── application/            ← Termux 应用层
│   ├── emulator/               ← 终端模拟器
│   ├── shared/                 ← 共享库
│   └── view/                   ← 终端视图
│
├── 🧩 utilities/               ← 工具模块
│   ├── build-info/             ← 构建信息
│   ├── flashbar/               ← Snackbar/Toast 组件
│   ├── framework-stubs/        ← Android Framework Stub
│   ├── lookup/                 ← 符号查找
│   ├── preferences/            ← 偏好设置
│   ├── shared/                 ← 共享工具
│   ├── templates-api/          ← 模板 API
│   ├── templates-impl/         ← 模板实现
│   ├── treeview/               ← 树形视图组件
│   ├── uidesigner/             ← UI 设计器
│   └── xml-inflater/           ← XML 布局预览引擎
│
├── 📡 event/                   ← 事件总线
│   ├── eventbus/               ← EventBus 核心
│   ├── eventbus-android/       ← Android 适配
│   └── eventbus-events/        ← 事件定义
│
├── 📝 annotation/              ← 注解处理器
│   ├── annotations/            ← 注解定义
│   ├── processors/             ← 编译时处理器 (kapt)
│   └── processors-ksp/         ← KSP 处理器
│
├── 📊 logging/                 ← 日志模块
│   ├── idestats/               ← IDE 统计
│   ├── logger/                 ← 日志核心 (Logback)
│   └── logsender/              ← 日志上报
│
├── 🔌 external/                ← 外部集成
│   ├── acsprovider/            ← ACS Provider
│   ├── atc/                    ← ATC (Android Theme Creator)
│   └── logwire/                ← LogWire
│
├── 🔧 ideconfigurations/       ← IDE 配置系统 (更新/网络)
├── 🔨 composite-builds/        ← Gradle 复合构建
│   ├── build-logic/            ← 构建逻辑插件
│   ├── build-deps/             ← 构建期依赖 (JDT/AppIntro/javac...)
│   ├── build-deps-common/      ← 公共构建依赖
│   └── external/               ← 外部子模块
│
├── 📖 docs/                    ← 文档
│   ├── en/                     ← 英文文档
│   └── zh-cn/                  ← 中文文档
│
└── 🖼️ images/                  ← 图标资源
    └── icon.png
```

## 三、布局文件 (Layout) 位置

所有 UI 布局 XML 集中在：

```
core/app/src/main/res/layout/          (80+ 布局文件)
core/resources/src/main/res/layout/    (公共资源布局)
```

### 主要布局分类：

| 类别 | 文件 | 说明 |
|------|------|------|
| **主界面** | `activity_main.xml` | 主 Activity 布局 |
| **编辑器** | `activity_editor.xml`, `content_editor.xml` | 代码编辑器主界面 |
| **侧边栏** | `fragment_editor_sidebar.xml` | 编辑器侧边栏 |
| **Fragment** | `fragment_main.xml`, `fragment_*.xml` | 各 Fragment 布局(20+) |
| **底部弹窗** | `bottomsheet_*.xml` | Git Clone / 项目列表 |
| **对话框** | `dialog_*.xml` | Git/依赖/设置等对话框(15+) |
| **列表项** | `item_*.xml`, `layout_*.xml` | 各种 RecyclerView 条目 |
| **AI 功能** | `fragment_ai_*.xml`, `dialog_ai_*.xml` | AI Agent/Chat/历史 |
| **文件浏览** | `activity_filebrowser.xml`, `fragment_file_browser.xml` | 文件浏览器 |
| **设置** | `activity_preferences.xml`, `layout_settings.xml` | 偏好设置界面 |
| **终端** | `view_terminal_*.xml` | Termux 终端视图 |
| **NDK/CMake** | `ndk_layout.xml`, `cmake_layout.xml` | NDK/CMake 配置 |
| **搜索** | `layout_search_*.xml` | 搜索功能布局 |
| **响应式** | `layout-land/`, `layout-sw600dp/` | 横屏/平板适配 |

### 布局文件完整列表：

```
activity_about.xml
activity_asset_studio.xml
activity_contributors.xml
activity_crash_handler.xml
activity_editor.xml
activity_filebrowser.xml
activity_m3icons.xml
activity_main.xml
activity_main_crash.xml
activity_material_icons_web.xml
activity_preferences.xml
activity_review_changes.xml
bottomsheet_git_clone.xml
bottomsheet_project_list.xml
cmake_layout.xml
content_editor.xml
dialog_add_remote.xml
dialog_ai_permissions.xml
dialog_atc_wizard.xml
dialog_branch_name.xml
dialog_clone.xml
dialog_color_picker.xml
dialog_commit.xml
dialog_copy_to.xml
dialog_credentials.xml
dialog_dependency_updates.xml
dialog_deps_progress.xml
dialog_edit_module_config.xml
dialog_file_browser.xml
dialog_icon_edit.xml
dialog_loading.xml
dialog_local_llm_config.xml
dialog_progress.xml
dialog_project_settings.xml
dialog_push_pull.xml
dialog_update_confirm.xml
dialog_user_config.xml
dropdown_item.xml
fragment_ai_agent.xml
fragment_ai_agent_simple.xml
fragment_ai_history.xml
fragment_ai_preferences.xml
fragment_artificial.xml
fragment_asset_studio.xml
fragment_branches.xml
fragment_build_variants.xml
fragment_changes.xml
fragment_chat.xml
fragment_editor_sidebar.xml
fragment_empty_state.xml
fragment_file_browser.xml
fragment_git_client.xml
fragment_history.xml
fragment_init.xml
fragment_log.xml
fragment_log_viewer.xml
fragment_main.xml
fragment_non_editable_editor.xml
fragment_onboarding.xml
fragment_onboarding_greeting.xml
item_ai_menu.xml
item_branch.xml
item_color_preset.xml
item_commit.xml
item_dependency.xml
item_file.xml
item_file_change.xml
item_file_modification.xml
item_history.xml
item_menu.xml
item_project.xml
item_remote.xml
item_sidebar_navigation.xml
layout_about_header.xml
layout_about_list_section.xml
layout_build_variant_item.xml
layout_chip_error.xml
layout_chip_warning.xml
layout_contributors_item.xml
layout_contributors_list_section.xml
layout_crash_report.xml
layout_create_file_java.xml
layout_diagnostic_group.xml
layout_diagnostic_info.xml
layout_diagnostic_item.xml
layout_editor_bottom_action.xml
layout_editor_bottom_sheet.xml
layout_editor_build_status.xml
layout_editor_file_tree.xml
layout_editor_sidebar_header.xml
layout_filetree_item.xml
layout_indexing_banner.xml
layout_main_action_item.xml
layout_mem_usage.xml
layout_onboarding_item.xml
layout_onboarding_multiaction.xml
layout_onboarding_permission_item.xml
layout_onboarding_statistics.xml
layout_onboardng_setup_config.xml
layout_optionssheet_item.xml
layout_progress_sheet.xml
layout_project_not_initialized.xml
layout_run_task.xml
layout_run_task_dialog.xml
layout_run_task_item.xml
layout_run_tasks_confirmation.xml
layout_search_project.xml
layout_search_result_group.xml
layout_search_result_item.xml
layout_settings.xml
layout_symbol_item.xml
layout_template_list_item.xml
layout_template_widgetlist_item.xml
layout_text_size_slider.xml
m3icons_dialog_color_picker.xml
navigation_rail_header.xml
ndk_layout.xml
text_actions_layout.xml
view_suggestion.xml
view_terminal_toolbar_extra_keys_outer.xml
```

## 四、核心模块详解

### 1. `core/app` — 主应用入口 (349 Kotlin + 33 Java 文件)

```
com.tom.rv2ide
├── app/                        ← Application/Activity 基类
│   ├── IDEApplication.kt       ← Application 入口 (378行)
│   ├── IDEActivity.kt          ← Activity 基类
│   ├── IDEActivityDelegate.kt
│   └── configuration/          ← 构建配置/JDK 分发
│
├── activities/                 ← Activity 层
│   ├── editor/                 ← ★ 编辑器 Activity (核心)
│   │   ├── EditorActivityKt.kt     ← 主编辑器入口
│   │   ├── BaseEditorActivity.kt
│   │   ├── EditorHandlerActivity.kt
│   │   ├── ProjectHandlerActivity.kt
│   │   └── IDELogcatReader.kt
│   ├── IDEConfigurations.kt    ← 配置管理
│   ├── ContributorsActivity.kt
│   └── ...
│
├── fragments/                  ← Fragment 层
│   ├── MainFragment.kt         ← 主界面
│   ├── EditorFragment (sidebar)
│   ├── TerminalFragment.kt     ← 终端
│   ├── FileBrowserFragment.kt  ← 文件浏览
│   ├── Git 相关 (Branches/Changes/History/Init/Remotes)
│   ├── AI 相关 (AIHistory/Chat)
│   ├── SearchResultFragment.kt
│   ├── Settings/Preferences
│   └── onboarding/             ← 引导向导 (7个Fragment)
│       ├── GreetingFragment.kt
│       ├── PermissionsFragment.kt
│       ├── OnboardingFragment.kt
│       └── ...
│
├── artificial/                 ← ★ AI 功能模块
│   ├── agents/                 ← AI 代理
│   │   ├── openai/OpenAI.kt
│   │   ├── anthropic/Anthropic.kt
│   │   ├── google/Gemini.kt
│   │   ├── deepseek/DeepSeek.kt
│   │   ├── grok/Grok.kt
│   │   └── local/LocalLLM.kt   ← 本地 LLM
│   ├── completion/             ← AI 代码补全
│   │   ├── AICodeCompletionService.kt
│   │   ├── CodeCompletionUI.kt
│   │   └── SuggestionView.kt
│   ├── models/ApplyModel.kt    ← AI 修改应用模型
│   ├── parser/SnippetParser.kt ← 代码片段解析
│   ├── project/awareness/      ← 项目感知 (上下文注入)
│   ├── services/ArtificialService.kt
│   ├── permission/             ← AI 权限管理
│   ├── secrets/ApiKey.kt       ← API Key 管理
│   └── rules/WritingRules.kt   ← 写作规则
│
├── services/builder/           ← Gradle 构建服务
│   ├── GradleBuildService.kt
│   ├── ToolingServerRunner.kt
│   └── ...
│
├── handlers/                   ← 事件处理器
│   ├── AIRequestHandler.kt     ← AI 请求处理
│   ├── LspHandler.kt           ← LSP 处理
│   ├── EditorBuildEventListener.kt
│   └── system/                 ← NDK/CMake/包安装器
│
├── indexing/                   ← 索引导航
├── lsp/                        ← LSP 集成
├── git/                        ← Git 操作 (基于 JGit)
├── templates/android/          ← 项目模板 (8种)
│   ├── basic/ empty/ cpp/ game/ navigation/ etc/
│
├── ui/                         ← 自定义 UI 组件
│   ├── CodeEditorView.kt       ← 代码编辑器视图
│   ├── EditorBottomSheet.kt
│   ├── themes/ThemeManager.kt
│   └── ...
│
├── managers/                   ← 管理器
│   ├── NavigationRailManager.kt
│   └── CodeCompletionManager.kt
│
├── viewmodel/                  ← ViewModel (14个)
│   ├── MainViewModel.kt
│   ├── EditorViewModel.kt
│   ├── GitViewModel.kt
│   ├── TerminalFragmentViewModel.kt
│   └── ...
│
├── adapters/                   ← 适配器 (18个)
│   └── FileAdapter, BranchesAdapter, SearchListAdapter...
│
├── preferences/                ← 偏好设置扩展 (14个)
│   ├── editorPrefExts.kt
│   ├── aiAgentPrefExts.kt
│   ├── buildAndRunPrefExts.kt
│   └── ...
│
└── terminal/session/           ← 终端会话管理
```

### 2. `core/common` — 公共基础设施

```
common/
├── app/configuration/          ← 全局配置接口
├── shell/                      ← Shell 命令执行
├── syntax/                     ← 语法高亮
│   ├── colorschemes/           ← 配色方案
│   └── highlighters/           ← 语法高亮器
├── ui/themes/                  ← 主题系统
├── colorpicker/                ← 颜色选择器
├── managers/                   ← 通用管理器
├── fragments/                  ← 公共 Fragment
├── adapters/                   ← 公共适配器
├── models/                     ← 公共数据模型
├── utils/                      ← 工具函数
├── interfaces/                 ← 接口定义
├── tasks/                      ← 后台任务
└── window/                     ← 窗口管理
```

### 3. `editor/impl` — 编辑器核心

```
editor/impl/
├── editor/
│   ├── language/               ← 语言支持
│   │   ├── java/               ← Java 语言
│   │   ├── kotlin/             ← Kotlin 语言
│   │   ├── xml/                ← XML 语言
│   │   ├── groovy/             ← Groovy (Gradle)
│   │   ├── treesitter/         ← Tree-sitter 集成
│   │   ├── incremental/        ← 增量分析
│   │   └── newline/            ← 自动换行缩进
│   ├── lsp/                    ← LSP 客户端集成
│   ├── schemes/                ← 配色方案 (internal/)
│   ├── snippets/               ← 代码片段
│   ├── ui/                     ← 编辑器 UI
│   ├── utils/                  ← 编辑器工具
│   └── adapters/               ← 编辑器适配器
└── io/github/rosemoe/sora/     ← sora-editor 自定义扩展
    ├── text/                   ← 文本处理
    └── widget/                 ← 编辑器 Widget
```

### 4. `java/lsp` — Java/Kotlin LSP 服务器

```
java/lsp/
├── java/                       ← Java LSP
│   ├── compiler/               ← Java 编译器封装
│   ├── parser/                 ← 解析器
│   ├── providers/              ← LSP Provider (补全/诊断/跳转...)
│   ├── visitors/               ← AST 访问器
│   ├── actions/                ← 代码操作
│   ├── edits/                  ← 代码编辑
│   ├── rewrite/                ← 代码重写
│   ├── models/                 ← 数据模型
│   └── utils/                  ← 工具
├── kotlin/                     ← Kotlin LSP
│   ├── compiler/               ← Kotlin 编译器封装
│   ├── providers/              ← LSP Provider
│   └── etc/
└── clang/                      ← C/C++ LSP (ClangD)
```

### 5. `xml/` — XML/Android 资源处理

```
xml/
├── aaptcompiler/               ← AAPT2 Android 资源编译器 (Google/Android)
├── dom/                        ← Eclipse LSP4XML/WTP DOM 解析
├── lsp/                        ← XML LSP 实现
├── resources-api/              ← Android 资源 API
└── utils/                      ← XML 工具
```

### 6. `tooling/impl` — Gradle 构建工具

```
tooling/impl/
├── tooling/impl/
│   ├── internal/               ← 内部实现
│   ├── sync/                   ← 项目同步
│   ├── net/                    ← 网络
│   ├── progress/               ← 进度报告
│   ├── util/                   ← 工具
│   └── logging/                ← 日志
```

## 五、多语言/国际化资源

### 主要字符串资源位置：

```
core/resources/src/main/res/
├── values/strings.xml             ← 英语 (默认, 1044行)
├── values-zh-rCN/strings.xml      ← 简体中文 (1007行)
├── values-ar-rSA/strings.xml      ← 阿拉伯语 (508行)
├── values-bn-rIN/strings.xml      ← 孟加拉语
├── values-de-rDE/strings.xml      ← 德语
├── values-es-rES/strings.xml      ← 西班牙语
├── values-fr-rFR/strings.xml      ← 法语
├── values-hi-rIN/strings.xml      ← 印地语
├── values-in-rID/strings.xml      ← 印尼语
├── values-pt-rBR/strings.xml      ← 葡萄牙语 (巴西)
├── values-ro-rRO/strings.xml      ← 罗马尼亚语
├── values-ru-rRU/strings.xml      ← 俄语
└── values-tr-rTR/strings.xml      ← 土耳其语
```

共支持 **13 种语言** (含英语)，通过 Crowdin 平台众包翻译。

### 附加资源文件：

```
core/app/src/main/res/values/strings.xml    ← App 特有字符串
core/resources/src/main/res/                ← 共享资源 (布局/图片/动画/字体等)
logging/logsender/src/main/res/values/      ← 日志上报字符串
```

### `core/resources` 完整资源目录：

```
core/resources/src/main/res/
├── anim/                    ← 动画
├── animator/                ← 属性动画
├── drawable/                ← 图片资源
├── drawable-hdpi/           ← 高清图片
├── drawable-mdpi/           ← 中清图片
├── drawable-v24/            ← API 24+ 图片
├── drawable-xhdpi/          ← 超高清图片
├── drawable-xxhdpi/         ← 超超高清图片
├── drawable-xxxhdpi/        ← 超超超高清图片
├── font/                    ← 字体
├── layout/                  ← 布局文件
├── menu/                    ← 菜单
├── mipmap-*/               ← 应用图标 (各密度)
├── values/                  ← 默认字符串/颜色/样式
├── values-night/            ← 夜间模式
├── values-{lang}/          ← 各语言字符串 (13种)
└── resources.properties
```

## 六、架构设计总结

```
                         ┌─────────────────────────┐
                         │   core/app (APK Entry)   │
                         │  Activities / Fragments  │
                         │  ViewModels / Adapters   │
                         └───────────┬─────────────┘
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        │                            │                            │
   ┌────▼─────┐              ┌──────▼──────┐              ┌──────▼──────┐
   │  Editor   │              │   Tooling    │              │   Termux    │
   │ (sora +   │              │  (Gradle     │              │  (Terminal  │
   │ TS/LSP)   │              │  Tooling)    │              │  Emulator)  │
   └────┬─────┘              └──────┬──────┘              └──────┬──────┘
        │                            │                            │
   ┌────▼─────┐              ┌──────▼──────┐              ┌──────▼──────┐
   │ Java LSP │              │    Build    │              │   Common    │
   │ Kotlin   │              │   Service   │              │   (Shell    │
   │ XML LSP  │              │   (bg job)  │              │   Themes)   │
   │ C LSP    │              └─────────────┘              └─────────────┘
   └──────────┘
                                     │
                         ┌───────────┴───────────┐
                         │    core/common         │
                         │  (Base Utils/UI/Task)  │
                         └───────────┬───────────┘
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        │                            │                            │
   ┌────▼─────┐              ┌──────▼──────┐              ┌──────▼──────┐
   │ Projects │              │   Indexing   │              │     Git     │
   │ (Model)  │              │  (Code Nav)  │              │   (JGit)   │
   └──────────┘              └─────────────┘              └─────────────┘
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        │                            │                            │
   ┌────▼─────┐              ┌──────▼──────┐              ┌──────▼──────┐
   │ Artificial│              │  Templates  │              │    XML      │
   │  (AI)     │              │  (Project)  │              │  (AAPT2)   │
   └──────────┘              └─────────────┘              └─────────────┘
```

### 关键架构特点：

1. **模块化设计** — 50+ 独立 Gradle 模块，清晰的关注点分离
2. **MVVM 架构** — ViewModel + DataBinding + Navigation Component
3. **EventBus** — GreenRobot EventBus 模块间通信
4. **LSP 协议** — 基于 Eclipse LSP4J，支持 Java/Kotlin/XML/C
5. **Tree-sitter** — 高效语法解析引擎
6. **sora-editor** — 富文本代码编辑器 (GitHub: Rosemoe)
7. **Gradle Tooling API** — 通过独立进程连接 Gradle 进行构建同步
8. **Termux 集成** — 内置完整 Linux 终端模拟器
9. **AI 多 Provider** — 支持 OpenAI / Anthropic / Gemini / DeepSeek / Grok / 本地 LLM

## 七、依赖关系汇总

### 关键外部依赖

| 类别 | 库 | 用途 |
|------|-----|------|
| **代码编辑器** | `io.github.rosemoe:editor` | sora-editor 富文本编辑器 |
| **语法解析** | Tree-sitter (Java/Kotlin/XML/C/JSON/Log) | 语法高亮和AST解析 |
| **LSP** | `org.eclipse.lsp4j` | 语言服务器协议实现 |
| **构建** | Gradle Tooling API | Gradle 项目构建同步 |
| **Git** | `org.eclipse.jgit` | Git 版本控制 |
| **终端** | Termux (emulator/view) | 内置终端模拟器 |
| **AI** | `google.ai.client.generativeai` | Gemini AI 集成 |
| **日志** | Logback (logback-android) | 日志框架 |
| **事件** | GreenRobot EventBus | 模块间事件通信 |
| **HTTP** | Retrofit + OkHttp | 网络请求 |
| **图片** | Glide | 图片加载 |
| **Markdown** | Markwon | Markdown 渲染 |
| **XML** | Xerces, LSP4XML/WTP | XML 解析和 DOM |
| **AAPT2** | Android Build Tools | Android 资源编译 |
| **Java 编译** | Eclipse JDT, javac | Java/Kotlin 编译 |
| **Bytecode** | ASM, javapoet | 字节码操作 |
| **序列化** | Gson, Protobuf | 数据序列化 |
| **UI** | Material Design, ConstraintLayout, Flexbox | UI 组件 |
| **导航** | AndroidX Navigation | Fragment 导航 |
| **后台** | AndroidX WorkManager | 后台任务 |
| **测试** | JUnit, Espresso, MockK, Robolectric | 测试框架 |

### 模块间代码量统计

| 模块 | Kotlin | Java | 总计 |
|------|--------|------|------|
| core | 477 | 74 | 551 |
| editor | 117 | 6 | 123 |
| java | 137 | 78 | 215 |
| xml | 99 | 64 | 163 |
| tooling | 158 | 6 | 164 |
| utilities | 318 | 32 | 350 |
| termux | 3 | 153 | 156 |
| logging | 5 | 20 | 25 |
| event | 8 | 33 | 41 |
| annotation | 7 | 1 | 8 |
| external | 22 | 2 | 24 |
| ideconfigurations | 1 | 0 | 1 |
| **总计** | **~1,352** | **~469** | **~1,821** |

## 八、项目文件概览

```
根目录关键文件:
├── build.gradle.kts                 ← 根构建脚本
├── settings.gradle.kts              ← 模块注册 (46个子模块)
├── gradle.properties                ← Gradle 属性
├── gradle/libs.versions.toml        ← 版本目录 (依赖管理)
├── version.properties               ← 版本信息
├── updater.json                     ← 更新配置
├── crowdin-contributors.json        ← 翻译贡献者
├── README.md                        ← 项目说明
├── .gitignore / .gitmodules         ← Git 配置
├── consumer-rules.pro               ← ProGuard 规则
├── gradlew / gradlew.bat            ← Gradle Wrapper
└── signing/signing-key.jks          ← 签名密钥
```
