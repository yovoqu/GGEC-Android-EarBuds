# 架构规则：分层、Provider 规范与自底而上梳理顺序

> 状态：active。适用范围：本仓全部模块（lib_* / biz_* / yotheme / app）。
> 与 `docs/architecture/2026-11-app-rebuild/design.md` 配套：design 定本轮切片，本文定长期规则。
> 依据：YoAgentDocs `rules/common/architecture.md`（严禁兼容层 / 禁止防御性修补 / 高内聚低耦合 / 模型跟随所在层）、
> `rules/by-type/android/README.md`、`rules/by-type/ui-kit/layering.md`。

## 1. 自底而上的四层

```
L0 lib_*        平台与协议机制：不知道产品、不知道业务（lib_common / lib_bluetooth / lib_ai / lib_ggec_opus）
L1 biz_*        产品与协议族的知识真源：型号目录、能力、命令表（biz_ggec_hs / biz_ggec_wy / biz_ggec_record / biz_appupdate）
L2 app/domain   应用领域：状态机、用例、端口；纯 Java，不认识 SDK/Android/JSON
L3 app/data     持久化：DocumentStore + 严格编解码 + 单写者仓库
L4 app/ui       View + ViewModel + UiState/UiMapper；只依赖 domain 用例与模型
```

- 依赖只能向下：`app/ui → app/domain ← app/data`，`app/domain` 通过端口由 `app/wiring` 注入 `provider` 实现。
- 每一层只认下一层的**公开契约**：lib 的 `api/` 或根门面、biz 的 `api/` 或根门面、app 的 `domain` 端口。
- 自底而上梳理：先让 L0 干净（无 compat 层、无死版本分支、无无用依赖），再让 L1 干净（目录=真源、无复制第二套），最后 L2–L4 才可能干净。下层不干净时，上层任何「适配」都会变成兼容层。
- 每层的门禁按**目录 + 包名前缀**判定，禁止类名/文件名白名单。

## 2. Provider 层定义

Provider 是「第三方依赖 ↔ 本应用端口」的**唯一适配层**。它翻译类型与生命周期，不承载业务。

### 2.1 目录 = 依赖模块名

```
app/src/main/java/com/ggec/earbuds/provider/
  lib_common/                  ← 依赖模块名（settings.gradle 里的 project 名）
    log/                       ← 子模块目录（模块内已有子域时）
    permission/
    refresh_rate/
  lib_ai/                      ← 只有一个大模块、api 文件多 → 按职责拆子目录
    chat/  platform/  realtime/  voices/
  biz_ggec_hs/
    device/                    ← 设备链路与能力目录
    record/                    ← 录音端口
    value/                     ← 值/失败/动作映射
  biz_ggec_wy/
    device/  record/  value/
  biz_appupdate/
```

规则：

1. **第一级目录名必须等于 Gradle 依赖模块名**（`lib_common`、`biz_ggec_hs`…），不得用 `bluetooth`、`hs` 之类的别名。
2. **模块有子模块 → 建子模块目录**（`provider/lib_common/log`、`provider/lib_common/permission`）。
   子模块指该依赖内部已经存在的职责域（包目录或独立 api 分组）。
3. **模块只有一个大模块、但公开 API 文件较多 → 按职责建独立子目录**（`provider/biz_ggec_hs/device|record|value`）。
   判断标准：一个目录内适配器/映射器超过 3 个、或一个文件同时服务两个以上 api 分组。
4. **公开 API 文件少（1–2 个）且职责单一时不强行拆**：直接放 `provider/<module>/` 下。
5. 子目录内一个文件一类职责：`XxxProviders`（SDK 生命周期与端口装配）、`XxxAdapter`（端口实现）、`XxxMapper`（类型翻译）。
   禁止 `Utils`/`Helper`/`Impl2`/`Manager` 这类垃圾桶命名。

### 2.2 只能引用依赖的公开 API

Provider 可以使用：

- 依赖模块的 `api/` 包（或根包门面类，如 `YoHsSdk`、`YoAiRuntime`）；
- 依赖模块 `model/` 下的公开模型（若该模块把 `model/` 声明为公开契约）。

Provider **严禁**：

- `import com.yovo.<module>.internal.**`、`**.impl.**`、`**.internal` 包内任何类型；
- 通过反射、类名字符串、资源名去访问依赖内部；
- 把 SDK 的类型放进 app 的公共签名（domain 端口、UiState、ViewModel 构造参数）；
- 反向 import 依赖的依赖（`provider/biz_ggec_hs` 不得 import `com.yovo.lib_bluetooth.**`；需要的能力应由 biz 再暴露）。

判定口径：**依赖模块内部模块不得耦合到依赖方的内部文件**。若 biz 只暴露了 App 需要的一半能力，正确做法是在 biz 的 `api/` 补契约，而不是让 provider 伸手进 `internal/`。

### 2.3 Provider 的职责边界

允许：
- 类型翻译（SDK 事件 → 领域快照/事件；领域意图 → SDK 调用）；
- 生命周期持有（一个 SDK 实例 = 一个 `XxxProviders`，`open/close` 成对）；
- 失败分类翻译（SDK 错误码 → 领域失败枚举），不得携带用户文案或技术英文串。

禁止：
- 业务策略（默认型号、重试策略、值域推导、状态机、权限判定）；
- 持久化/文件 IO/SharedPreferences；
- 静态可写状态与 `install*` 全局注册表（幂等由库内保证，provider 只调用一次）；
- 为旧调用方保留的转发/别名/双签名（兼容层）。

### 2.4 可见性与引用

- `provider/**` 只被 `wiring/**` 引用；`ui`/`domain`/`data` 零 import。
- 装配根只拿 provider 暴露的**领域端口**，不出现 SDK 类型。
- 同一 SDK 的多个端口必须来自同一个 `XxxProviders` 实例（扫描/连接/目录共用一条链路）。

### 2.5 门禁（结构性）

```
对每个 src/main/java/com/ggec/earbuds/provider/<dir>/**/*.java：
  - <dir> 必须 ∈ implementation 里的 project 名集合（从 configurations 派生，不手写）
  - import com.yovo.* 必须匹配 com.yovo.<dir>.*
  - import 不得包含 ".internal." / ".impl."（依赖内部实现）
对每个非 provider 的 src/main/java/**：
  - import com.yovo.<module>.* 一律失败（业务模块只能经 provider 接入）
  - 例外仅按包名分类（com.yovo.lib_common.event.*，且仅 ui/**）
```

新增一个依赖模块时：`settings.gradle` 加 include → `app/build.gradle` 加 `implementation project(...)` → 建 `provider/<module>/` → 门禁自动覆盖，不需要改任何名单。

## 3. 各层硬规则

### 3.1 lib_*
- 不知道产品型号/项目/能力名；`modelId|projectId|deviceType|HS0` 关键字零命中。
- 无 `compat`/`legacy`/`shim`/`bridge` 目录或类型；平台差异只用一处能力查询（如 `AndroidPlatform.supportsXxx()`）。
- `minSdk` 以下的 `Build.VERSION.SDK_INT` 分支是死代码，必须删除。
- 禁止 `catch (Throwable ignored)`：按失败类型归一；回调异常不得吞。
- 公开面只有 `api/`/根门面 + `model/`；`internal/**` 不对外。

### 3.2 biz_*
- 产品目录（型号/能力/项目）是唯一真源；app 只读投影。
- 同类协议族（HS/WY）共享的帧装配/命令表必须有单一实现，禁止复制第二套。
- `api/` 只放契约类型与转发；不 import `internal`，不写算法。

### 3.3 app/domain
- 状态机一功能一个：`sealed` 状态 + `reduce(state,event)` 纯函数 + 显式 effect；
  禁止布尔群、禁止在 View/VM 各自维护半份状态。
- 端口不用 `Consumer<Boolean>`/`onError(String)`；失败用分类枚举 + 结构化载荷。
- 不 import Android/JSON/data/ui/platform/provider/wiring。

### 3.4 app/data
- 唯一 IO 原语 `DocumentStore`；唯一原子写实现；每文档一个写者（串行、合并写）。
- 编解码严格版本化：`v` 不匹配 → `DocumentFormatException`；无迁移分支、无 fallback 参数。
- 持久化 DTO 与领域模型分家（`*Dto` 只服务编解码）。
- 一次业务提交只经一个仓库方法；跨文件提交成对成功或成对失败。

### 3.5 app/ui
- View 只渲染 `UiState` 与转发意图；ViewModel 不持 Context/View/Handler、不拼文案。
- 未确认写入、筛选、编辑草稿等临时状态归 ViewModel 的 UiState，不留在 View 字段里。
- 文案进 `res/values/strings*.xml`；尺寸/颜色/时长进 theme/token。

## 4. 变更流程（强制）

1. 声明切片：模块 + 数据链路 + 可判定验收（例：lib_bluetooth 扫描链路 + `compat` 目录删除 + `grep compat = 0`）。
2. 只读审查该切片，写「设计前链路」（入口 → 状态 → 变换 → 副作用终点，节点标证据等级）。
3. 自底而上改：先改下层契约，再改上层调用点；一次替换旧路径，删除旧实现。
4. 同切片内禁止留 TODO/兼容/双轨/非阻塞挂账；未完成即未通过。
5. 编译 + 门禁 + 单测；未接真机的设备链路如实标「已读到」。
6. 文档回写：本文件或 `design.md` 的契约若变更，同一切片内更新。

## 5. 当前落位（本轮结束后的目标）

| 依赖 | provider 目录 | 子目录 |
|------|---------------|--------|
| lib_common | `provider/lib_common/` | `log/`、`permission/`、`refresh_rate/` |
| lib_ai | `provider/lib_ai/` | `chat/`、`platform/`、`realtime/`（按现有文件职责归并） |
| biz_ggec_hs | `provider/biz_ggec_hs/` | `device/`、`record/`、`value/` |
| biz_ggec_wy | `provider/biz_ggec_wy/` | `device/`、`record/`、`value/` |
| biz_appupdate | `provider/biz_appupdate/` | 单目录（API 少） |
| biz_ggec_record | 隔离，本轮不纳入 | — |
| lib_ggec_audio（L1 共享音频装配） | 不由 app 直接依赖；由 `biz_ggec_hs`/`biz_ggec_wy` 依赖 | — |
| biz_appupdate | `provider/biz_appupdate/` | 单目录（`AppUpdateProviders`/`UpdateUiHostAdapter`/`UpdateUiMapper`） |
