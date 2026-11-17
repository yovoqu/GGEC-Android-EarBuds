# vendored：第三方 Opus SDK（只读区）

本目录是 **vendored（内嵌第三方）源码区**，不是本模块的业务代码：

- 包名保持 `com.bes.opus` 不变。JNI 入口名由包名+类名决定
  （`Java_com_bes_opus_OpusDecoder_init_1native` 等，见 `src/main/cpp/opus/*.cpp`），
  改包名/改类名必须同时改 native 符号，属于跨语言改动，因此挪目录、不改包。
- 本目录由 `lib_ggec_opus/build.gradle` 的 `sourceSets.main.java.srcDirs` 显式加入编译，
  并且 `checkLibGgecOpusBoundary` 强制：
  1. 本目录之外不得出现 `com.bes.opus` 的引用（模块外也不允许）；
  2. 本目录内的代码不得 import `com.yovo.lib_ggec_opus.internal.*`（只允许 `api.*`）；
  3. `com.bes.opus` 不得回流到 `src/main/java`（vendored 只有一个入口）。
- 对外只经门面 `YoOpusSdk` / `api/**` 暴露；`com.bes.opus` 属于实现细节，
  与 `internal/**` 同等待遇。
- `OpusConstants` 已删除：它只是 `api.YoOpusAudioContract` 的重复字面量副本，
  且全仓库无引用。

修改本目录等同于升级第三方 SDK，需同时核对 `src/main/cpp/**` 与 proguard 规则
（`consumer-rules.pro` 内的 `com.bes.opus` keep 规则保证 JNI 方法名不被裁剪/混淆）。
