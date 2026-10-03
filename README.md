# Cashier 记账

[![CI](https://github.com/MoliDuo/CashierHelper/actions/workflows/ci.yml/badge.svg)](https://github.com/MoliDuo/CashierHelper/actions/workflows/ci.yml)
[![最新发布](https://img.shields.io/github/v/release/MoliDuo/CashierHelper)](https://github.com/MoliDuo/CashierHelper/releases/latest)

Cashier 记账是 Cashier 的安卓伴侣：**双击手机侧键，把当前屏幕截下来，交给 Cashier 自动识别并记一笔账。** 识别完成后，手机会弹出一条通知，直接告诉你记了多少钱。

需要 Android 11 及以上。主要在三星 Galaxy S24（One UI）上使用，其他手机同样可用，但没有逐一实测。

## 安装

1. 在手机浏览器打开[最新发布页](https://github.com/MoliDuo/CashierHelper/releases/latest)，下载 `CashierHelper-v1.0.N.apk`，点开安装。系统提示“不允许安装未知应用”时，按提示允许浏览器安装一次。
2. 如果手机上已经装过旧版本的 Cashier Helper，**先卸载旧版，再装这个版本**。旧版用的是另一把签名密钥，系统不允许直接覆盖。卸载后需要重新填写 API 密钥、重新开启截图服务。
3. 从这个版本起，以后的更新都可以在应用里直接完成（见下文“更新”），不用再手动下载。

## 第一次使用

打开应用会进入设置引导，一共五步，大约两分钟。每一步做完会自动跳到下一步，随时可以点“跳过设置”，之后在“设置 → 重新运行设置引导”里还能再来一遍。

1. **连接 Cashier。** 填服务器地址（只写域名也行，会自动补上 `https://`）和 API 密钥。密钥在 Cashier 网页的 **设置 → API 密钥 → 新建密钥** 里创建，类型选“分账”，复制后点“粘贴”。点“测试并保存”，连得上才会保存。
2. **开启截图服务。** 点“去开启”，在无障碍设置里打开“Cashier 记账截图服务”。它只在你双击侧键时截一张图，不会读取屏幕上的文字。
   - 如果开关是灰色的，或者提示“受限制的设置”：这是安卓对手动安装应用的保护。点引导里的“打开应用信息”，右上角 ⋮ → “允许受限制的设置”，再回来开启。
   - 开启一天左右，系统会弹出一次“该应用可以查看你的屏幕”的提醒，请选“保留”。
3. **允许通知。** 识别结果靠通知告诉你。三星手机可以再到 **设置 → 通知 → 通知弹出样式** 选“详细”，这样横幅上能直接看到金额。
4. **不限制后台运行。** 把电池设为“不受限制”。没有网络时截的图，才能在恢复网络后稳定地自动上传。可以跳过。
5. **设置侧键。** 到手机的 **设置 → 高级功能 → 侧键**，把“双击”设为“打开应用”，选“Cashier 记账”。然后按引导的提示双击一次侧键，看到“试用成功”就完成了（这次的截图不会上传）。

## 日常使用

**双击侧键。** 手机轻轻震一下，表示截图成功，页面不会有任何跳转。之后通知里会依次显示：

> 上传中 → 处理中 → 结果（例如“星巴克 ¥35.00”）

结果通知里的“在 Cashier 查看”会直接打开这笔记录。不需要等：截完图可以马上继续用手机。

**没有网络也没关系。** 图片会先保存在手机里，网络恢复后自动上传；通知会写明“等待重新上传”。不会因为一次失败就挡住后面的截图。

**分享和相册。** 在相册或其他应用里选图片，点“分享”，选“Cashier 记账”；或者在应用首页点“从相册选图记账”。一次最多 9 张。选了 2 到 3 张时，会问你它们是“同一张账单”（合成一笔）还是“分别记账”。分享进来的照片，如果带有拍摄时间，就按拍摄日期记账。

**需要处理的情况。** 首页的“需要处理”列表会列出没能完成的截图和原因，例如密钥失效、服务器拒绝。点“重试”或“去修复”；修好连接后，等着的任务会自动重新上传。

## 更新

应用打开时和每天一次会检查有没有新版本。发现新版本会在后台下载，校验无误后弹出通知和首页横幅，点“立即更新”并在系统确认页点“更新”即可；点“稍后”则不再提醒这个版本。也可以在 **设置 → 检查更新…** 里手动检查，没有新版本会显示“已是最新版本”。

第一次更新时，系统可能要求你允许 Cashier 记账“安装未知应用”，按提示打开开关后回来再点一次“立即更新”。更新失败不会影响当前版本。

## 常见问题

**双击侧键没有反应，或者弹出了应用首页。** 首页顶部会写明原因，点“去修复”即可。最常见的是：截图服务显示已开启，但系统没有真正启动它。到 **设置 → 辅助功能** 里把“Cashier 记账截图服务”关掉再打开，就好了。

**通知只显示应用名，看不到金额。** 三星手机到 **设置 → 通知 → 通知弹出样式** 改成“详细”。

**银行、支付密码页面截不了图。** 这些页面被系统禁止截图，应用无法绕过。可以在付款完成页再截。

**识别结果不对。** 点通知上的“在 Cashier 查看”，在 Cashier 里修改。

---

以下是给开发者的说明。

## 工作方式

应用不常驻后台，也不显示账单。每次截图或分享图片都成为一个**任务**，由 WorkManager 在后台执行，进程被杀、手机重启、断网恢复之后都会自动续上。

```
QUEUED ─上传→ UPLOADING ─201→ PROCESSING ─完成→ DONE
   ▲ 网络错误/5xx：回到 QUEUED，指数退避        (outcome：已记账 / 无法记账 / 识别失败 / 已被删除 / 无结果)
   └ 需要处理：NEEDS_ACTION(problem)：密钥无效、没有配置、服务器拒绝、冲突、配置已变
```

- 图片（JPEG，长边不超过 3200，目标不超过 1.5 MiB，硬上限 4 MiB）先写到 `noBackupFilesDir/task-images/<id>/`，再写任务记录；收到 201 后先把任务存成 PROCESSING，才删除本地图片。
- 任务记录是 DataStore 中的 JSON。读失败不会删除任务：损坏的文件改名隔离，图片保留。
- 幂等键和记账日期（`entryDate`）在建任务时就固定，重试原样发送，所以重试永远不会产生重复记录，也不会因日期变化触发 409。
- 每个任务一个唯一的 `TaskWorker`：要求有网络，从 20 秒起指数退避，每次运行最多 150 秒（先上传，再轮询），没完成就让 WorkManager 稍后重试；Android 12 及以上使用加急任务。轮询默认每 5 秒一次，遵守服务器的 `Retry-After`，24 小时仍没有结果就标记为“无结果”。
- 配置指纹绑定服务器地址和密钥：还没发出过请求的任务会自动换绑新配置；已经发过请求的任务会进入 `CONFIG_CHANGED`，不会把旧截图用新凭据发往别处。保存新的有效连接后，等待连接的任务自动重新排队。
- 删除任务时先取消后台工作，再删记录，仓库只更新已存在的记录，已删除的任务不会被后台写回。
- 保留策略：已完成的任务保留 7 天、最多 50 条；未完成的任务不会被自动清理。

### 触发流程

`LauncherActivity` 是透明的，不显示界面。它依次判断：正在试侧键（引导第 5 步）→ 只截图并丢弃；没配置、密钥读不出、服务未开启或没在运行 → 打开主界面并说明原因；否则截图、立即震动确认、交给任务系统后关闭。

`LauncherActivity` 和 `ShareActivity` 使用独立的 `taskAffinity`（`pro.xiangyu.cashierhelper.trigger`），`MainActivity` 使用默认 affinity，每次都用 `NEW_TASK` 启动。这样主界面永远不会残留在触发器的任务栈里，下一次双击侧键一定是截图，而不是弹出设置页。

### 请求契约

```http
POST {BASE_URL}/api/v1/source-documents
Authorization: Bearer {API_KEY}
Content-Type: application/json
Idempotency-Key: {任务创建时生成的 UUID}

{ "images": [ { "data": "<raw JPEG Base64>", "mimeType": "image/jpeg" } ], "entryDate": "YYYY-MM-DD" }
```

返回 201 并带有 `sourceDocumentId` 才算提交成功；用同一个幂等键重放会得到同样的 201。之后：

```http
GET {BASE_URL}/api/v1/source-documents/{sourceDocumentId}
```

顶层 `status` 是 `processing`、`completed`、`invalid`、`failed` 或 `cancelled`。`completed` 的 `title`、`total` 和 `result.entries` 用于通知；`invalid` 直接显示服务器返回的 `error.message`；`404` 表示记录已在服务器上被删除。

“测试连接”对一个随机 UUID 发 GET：返回 404 且错误码为 `NOT_FOUND` 表示地址和密钥都对；401 表示密钥无效；其他 404 表示这不是 Cashier。

超时：连接 15 秒，读写各 60 秒；整个上传调用不超过 120 秒，GET 不超过 20 秒。请求体流式写出 JSON 和 Base64，不在内存里拼大字符串。

### 通知

三个渠道：`progress`“记账进度”（静默）、`results`“记账结果”（弹出横幅）、`alerts`“需要处理”（弹出横幅）；另有 `updates`“应用更新”。每个任务一条通知：进度通知（id `10000+seq`）依次显示上传中、等待重新上传、处理中，出结果时取消进度通知并重新发出结果通知（id `20000+seq`），这样横幅才能可靠弹出。每条通知都有标题、正文和只含标题的锁屏版本。通知被关闭时，改用 Toast 兜底。

## 安全

- 服务器地址只接受 HTTPS；禁止用户名密码、查询参数和片段。
- API 密钥使用 Android Keystore AES-GCM 加密保存。解密失败会明确提示重新填写，不会静默当成“未配置”。
- 应用禁止云备份与设备迁移：`allowBackup=false`，并在 `data_extraction_rules.xml` 中排除全部数据域。
- 密钥、截图、完整响应都不写入日志或通知。曾出现在截图中的密钥应先撤销并重新生成。
- 应用内更新只接受本仓库发布的下载地址，并在安装前校验 SHA-256、包名、版本号和签名证书，任何一项不符就丢弃安装包。

## 构建

项目使用 JDK 21 运行 Gradle，Kotlin 与 Java 字节码目标为 17。Linux/WSL 下：

```bash
source /home/xiangyu/.config/android-env.sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

| 产物 | 路径 | 说明 |
|---|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 应用名“Cashier 记账 调试”，包名带 `.debug` 后缀，可以和正式版装在同一台手机上。调试版不检查更新。 |
| Release APK（未签名） | `app/build/outputs/apk/release/app-release-unsigned.apk` | 未提供签名环境变量时的产物；构建成功不代表可分发。 |
| Release APK（已签名） | `app/build/outputs/apk/release/app-release.apk` | 提供了签名环境变量时的产物。 |

本地要产出已签名的 Release 包，先设置这四个环境变量（发布工作流用同名变量）：

```bash
export CASHIERHELPER_KEYSTORE_PATH=/path/to/release.jks
export CASHIERHELPER_KEYSTORE_PASSWORD=...
export CASHIERHELPER_KEY_ALIAS=cashierhelper
export CASHIERHELPER_KEY_PASSWORD=...
./gradlew assembleRelease -PreleaseVersionName=1.0.2
```

`versionName` 默认是基线 `1.0.1`，可用 `-PreleaseVersionName` 覆盖；`versionCode` 按规范 006 的 6.2.3 由版本号换算：`X*1000000 + Y*1000 + Z`（1.0.2 → 1000002），也可以用 `-PreleaseVersionCode` 显式指定。设置 `CASHIERHELPER_REQUIRE_SIGNING=true` 后缺少任何一个签名变量都会立即失败，发布工作流依赖这个行为。仓库不保存私钥。

## 自动发布

| 工作流 | 触发 | 行为 |
|---|---|---|
| `CI` | 非 `main` 分支的推送、面向 `main` 的 PR | 运行 `python3 scripts/test_release.py` 和 `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`（Release 构建让 R8 和 lintVital 在 PR 阶段就跑一遍）；失败时上传测试与 lint 报告。 |
| `Release` | 推送到 `main`，且改动涉及 `app/`、Gradle 文件、`scripts/` 或发布工作流本身 | 运行同一套检查，再用自动递增的版本构建、签名并发布一个 GitHub Release。 |

只改 README 或其他文档的推送不会发版，避免用户收到没有实际变化的更新提示。一次推送包含多个提交时只发布最后一个。`Release` 使用固定并发组并配置 `queue: max`，多个运行按顺序排队，不会同时分配版本号。

### 版本规则

`1.0.1` 是基线：下一个自动发布产出 `v1.0.N`，补丁位依据已有的 Release（含草稿）和 `v1.0.*` tag 取最大值加一，由 `scripts/release.py` 通过 Gradle 参数注入，因此不会产生自动改版本的提交。`versionCode` 由版本号按上面的公式换算，所以永远单调递增。

### 发布产物与更新源

| 附件 | 说明 |
|---|---|
| `CashierHelper-v1.0.N.apk` | 用固定发布密钥签名的 Release APK。 |
| `release-metadata.json` | **应用内更新读取的 feed**：`versionName`、`versionCode`、`apkUrl`（指向本版本的资产，不指向 `latest`）、`sha256`、`publishedAt`、`notes`（提交说明，去掉署名行），以及构建提交和运行信息。 |
| `SHA256SUMS` | 附件校验和，用 `sha256sum -c SHA256SUMS` 校验。 |
| `mapping.txt` | 该版本的混淆映射，用于还原崩溃堆栈。 |

应用读取固定入口 `https://github.com/MoliDuo/CashierHelper/releases/latest/download/release-metadata.json`（规范 007 的 7.3.1）。

流水线在公开 Release 前先用 `apksigner verify` 校验签名，再用 `aapt2 dump badging` 确认内嵌版本；版本先以草稿创建，附件全部上传成功后才公开。公开之后，`scripts/release.py verify-feed` 会像应用一样下载 `latest` 下的 feed，核对版本、下载地址和 SHA-256；对不上就把这个 Release 改回草稿并让流水线失败（规范 006 的 6.5.5、6.8）。

### 签名密钥

发布密钥保存在仓库外的 `/home/xiangyu/.config/cashierhelper/signing/`（目录权限 `700`、文件权限 `600`）：RSA 3072 位 keystore（alias `cashierhelper`，有效期 10000 天）与随机密码，同时配置为仓库 Secrets：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `release.jks` 的 base64 编码。 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码。 |
| `ANDROID_KEY_ALIAS` | `cashierhelper`。 |
| `ANDROID_KEY_PASSWORD` | 密钥密码。 |

工作流把密钥解码到 runner 的临时目录，结束时无论成功失败都会删除。请备份上面那个目录：**密钥永远不换**，丢失后已安装的应用将再也收不到更新（规范 007 的 7.5.5、7.5.6）。

### 失败恢复

版本号在构建前就已占用，所以失败的运行可能让公开版本跳号，但不会重复使用已占用的版本。

- 构建或上传失败时草稿 Release 会保留，重新运行同一个工作流会复用同一个草稿和版本号。
- 同一个提交重新推送时，如果已经发布成功，`Release` 直接结束。
- 附件上传中断后重跑，会先删除同名旧附件再重新上传。
- 补发一个较老的草稿不会覆盖更高版本的 Latest 标记。
- 如果 `v1.0.N` tag 已存在但指向别的提交，工作流直接失败并要求人工处理。

## 已知限制

- 截图范围仅为默认显示屏，不支持 DeX 外接屏或其他显示器。
- 银行、密码管理器等使用安全窗口的界面会被 Android 拒绝截图，无法绕过。
- 设置引导里三星的菜单路径（侧键、通知弹出样式）按 One UI 的常见写法给出，不同版本的名称可能略有出入。
- 三星电源管理、旋转与分屏、锁屏、省电模式等行为仍需在目标 One UI 版本上实机验证。
- 系统强制停止应用后，任务要等应用下次运行或系统重新调度时才会继续。
