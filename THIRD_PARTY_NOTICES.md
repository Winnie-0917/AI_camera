# Third-Party Notices

查核日期：2026-09-06。基準提交：`e12e9e3`。本文件記錄第三方來源與授權證據。團隊已選擇 MIT 作為原創程式碼、文件、自行拍攝資料及 AI 製作素材的授權；第三方內容不因此重新授權。

## Scope and verification

- 掃描目前工作目錄全部 97 個儲存庫檔案（包含隱藏設定檔，排除 `.git` 內部資料），檢查 Kotlin 原始碼、測試、XML、Gradle 設定、imports、URL、註解、詞彙表、27 個圖片／動畫檔與 Gradle Wrapper JAR。另查閱模型／素材相關提交紀錄。
- 初次掃描時未找到專案層級的 LICENSE、LICENSE.md、LICENSE.txt、COPYING 或 NOTICE；原 README 的 `License：無` 不構成明確授權。本次依團隊選擇新增 [MIT LICENSE](LICENSE)。`gradlew`、`gradlew.bat` 的 Apache-2.0 檔頭僅適用於該第三方程式碼。
- 專案使用 Gradle，未找到 npm、Python、Dart、Maven、Cargo 或 Go 的其他依賴宣告。未找到模型權重、checkpoint、訓練腳本、獨立訓練資料、音訊、影片、SVG 或自帶字型檔。
- 共列 **38 項揭露紀錄**：22 個直接 Maven 座標、6 個建置工具／已追查的間接依賴、1 個 SDK 平台、2 個程式碼來源確認項、1 個微調模型、1 個詞彙表、2 個資料項、1 個系統字型項、1 組 AI 圖片素材及 1 個外部服務。這是揭露紀錄數，不是第三方作者數或 APK 依賴總數；不存在於儲存庫的模型／訓練資料也依程式線索列入。
- 22 個直接依賴均讀取指定版本的官方 Maven POM；未明寫版本的 Compose 元件依 BOM `2024.12.01` 查核。POM／上游 LICENSE 的授權不代表內含每個原生或間接元件都只有同一授權。
- 此環境未找到可用的 Java 指令及 Gradle dependency cache，未執行 Gradle dependency resolution、未產生 APK。下列間接依賴版本為上游 POM 宣告，不能當成最終衝突解析結果。完整 runtime／test／build 間接依賴及發行包 NOTICE 仍需在實際建置環境核對。

## Software and Libraries

表格欄位對應 Name、Purpose、Source、License、Attribution / Notice requirements；「備註」包含使用範圍與商業限制。`A`、`M`、`E`、`B` 的完整要求列於本節末尾。

### Direct dependencies

| ID | 項目類型 | 專案／Name | 用途／Purpose | 來源／Source | License | 需要 Attribution／NOTICE | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S01 | Software / Libraries | `androidx.core:core-ktx:1.16.0` | Android Kotlin 擴充 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.16.0/core-ktx-1.16.0.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S02 | Software / Libraries | `junit:junit:4.13.2` | JVM 單元測試 | [官方版本 POM](https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.pom) | EPL-1.0 | E | 測試用；授權允許商用，須履行對應條款 |
| S03 | Software / Libraries | `androidx.test.ext:junit:1.2.1` | Android 儀器測試 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/test/ext/junit/1.2.1/junit-1.2.1.pom) | Apache-2.0 | A | 測試用；授權允許商用，須履行對應條款 |
| S04 | Software / Libraries | `androidx.test.espresso:espresso-core:3.6.1` | Android UI 測試 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/test/espresso/espresso-core/3.6.1/espresso-core-3.6.1.pom) | Apache-2.0 | A | 測試用；授權允許商用，須履行對應條款 |
| S05 | Software / Libraries | `androidx.lifecycle:lifecycle-runtime-ktx:2.8.4` | 生命週期與協程 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/2.8.4/lifecycle-runtime-ktx-2.8.4.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S06 | Software / Libraries | `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4` | Compose ViewModel | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-compose/2.8.4/lifecycle-viewmodel-compose-2.8.4.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S07 | Software / Libraries | `androidx.activity:activity-compose:1.10.1` | Activity 與 Compose 整合 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/1.10.1/activity-compose-1.10.1.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S08 | Software / Libraries | `androidx.compose:compose-bom:2024.12.01` | Compose 版本對齊 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2024.12.01/compose-bom-2024.12.01.pom) | Apache-2.0 | A | 版本平台，非執行程式碼；授權允許商用，須履行對應條款 |
| S09 | Software / Libraries | `androidx.compose.ui:ui:1.7.6` | Compose UI | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui/1.7.6/ui-1.7.6.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S10 | Software / Libraries | `androidx.compose.ui:ui-graphics:1.7.6` | UI 繪圖 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-graphics/1.7.6/ui-graphics-1.7.6.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S11 | Software / Libraries | `androidx.compose.ui:ui-tooling:1.7.6` | Debug UI 工具 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-tooling/1.7.6/ui-tooling-1.7.6.pom) | Apache-2.0 | A | Debug 用；授權允許商用，須履行對應條款 |
| S12 | Software / Libraries | `androidx.compose.ui:ui-tooling-preview:1.7.6` | UI 預覽 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-tooling-preview/1.7.6/ui-tooling-preview-1.7.6.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S13 | Software / Libraries | `androidx.compose.ui:ui-test-manifest:1.7.6` | Debug 測試 manifest | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-manifest/1.7.6/ui-test-manifest-1.7.6.pom) | Apache-2.0 | A | Debug 用；授權允許商用，須履行對應條款 |
| S14 | Software / Libraries | `androidx.compose.ui:ui-test-junit4:1.7.6` | Compose 儀器測試 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-junit4/1.7.6/ui-test-junit4-1.7.6.pom) | Apache-2.0 | A | 測試用；授權允許商用，須履行對應條款 |
| S15 | Software / Libraries | `androidx.compose.material3:material3:1.3.1` | Material 3 介面 | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/1.3.1/material3-1.3.1.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S16 | Fonts / Icons | `androidx.compose.material:material-icons-extended:1.7.6` | 相機、設定與操作圖示（見 Fonts and Icons） | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/compose/material/material-icons-extended/1.7.6/material-icons-extended-1.7.6.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S17 | Software / Libraries | `androidx.exifinterface:exifinterface:1.3.7` | JPEG EXIF | [官方版本 POM](https://dl.google.com/dl/android/maven2/androidx/exifinterface/exifinterface/1.3.7/exifinterface-1.3.7.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S18 | Software / Libraries | `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` | 裝置端 ONNX 推論 | [官方版本 POM](https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.20.0/onnxruntime-android-1.20.0.pom) | MIT | M；內含元件另見下文 | App 依賴；授權允許商用，須履行對應條款 |
| S19 | Software / Libraries | `io.coil-kt:coil-compose:2.7.0` | Compose 圖片載入 | [官方版本 POM](https://repo.maven.apache.org/maven2/io/coil-kt/coil-compose/2.7.0/coil-compose-2.7.0.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S20 | Software / Libraries | `io.coil-kt:coil-gif:2.7.0` | GIF 解碼 | [官方版本 POM](https://repo.maven.apache.org/maven2/io/coil-kt/coil-gif/2.7.0/coil-gif-2.7.0.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |
| S21 | Software / Libraries | `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0` | 協程單元測試 | [官方版本 POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-test/1.9.0/kotlinx-coroutines-test-1.9.0.pom) | Apache-2.0 | A | 測試用；授權允許商用，須履行對應條款 |
| S22 | Software / Libraries | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` | Android 非同步工作 | [官方版本 POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.9.0/kotlinx-coroutines-android-1.9.0.pom) | Apache-2.0 | A | App 依賴；授權允許商用，須履行對應條款 |

### Tools, indirect dependencies and source-code provenance

| ID | 項目類型 | 專案／Name | 用途／Purpose | 來源／Source | License | 需要 Attribution／NOTICE | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S23 | Software / Libraries | Kotlin 2.0.0（Android／Compose compiler plugin、stdlib） | Kotlin 編譯與標準函式 | [Kotlin Repository](https://github.com/JetBrains/kotlin)、[stdlib POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.0.0/kotlin-stdlib-2.0.0.pom)、[Kotlin plugin POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/2.0.0/kotlin-gradle-plugin-2.0.0.pom)、[Compose compiler plugin POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/compose-compiler-gradle-plugin/2.0.0/compose-compiler-gradle-plugin-2.0.0.pom) | Apache-2.0（已核對三個 POM；compiler 內含第三方另查） | A | 可商用；工具內附元件不一律視為 Apache |
| S24 | Software / Libraries | Android Gradle Plugin 8.8.0 | Android 建置 | [官方 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.8.0/gradle-8.8.0.pom)、[原始碼](https://android.googlesource.com/platform/tools/base/) | Apache-2.0 | A | 可商用；build dependencies 尚未完整解析 |
| S25 | Software / Libraries | Gradle 8.10.2／Wrapper | 建置啟動與工具下載 | `gradlew`、`gradlew.bat`、`gradle/wrapper/`；[官方 LICENSE](https://github.com/gradle/gradle/blob/v8.10.2/LICENSE) | Apache-2.0；Gradle 發行套件另含 EPL／BSD 等條款 | A；保留腳本 Copyright 2015 the original author or authors | 可商用；Wrapper JAR 無獨立 LICENSE entry，實際 JAR 版本／來源雜湊待核對，不能僅由 distributionUrl 證明 |
| S26 | Software / Libraries | OkHttp 4.12.0（上游宣告） | Coil 的間接 HTTP 依賴 | [Coil base POM](https://repo.maven.apache.org/maven2/io/coil-kt/coil-base/2.7.0/coil-base-2.7.0.pom)、[官方 LICENSE](https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt) | Apache-2.0 | A | 可商用；GeminiClient 本身使用 HttpURLConnection，仍不能據此排除 Coil 的 OkHttp |
| S27 | Software / Libraries | Okio 3.9.0（上游宣告） | Coil 間接 I/O 依賴 | [Coil base POM](https://repo.maven.apache.org/maven2/io/coil-kt/coil-base/2.7.0/coil-base-2.7.0.pom)、[官方 LICENSE](https://github.com/square/okio/blob/parent-3.9.0/LICENSE.txt) | Apache-2.0 | A | 可商用；最終版本待 Gradle 解析 |
| S28 | Software / Libraries | Hamcrest Core 1.3 | JUnit 間接測試 matcher | [官方 JAR](https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar) 內 `LICENSE.txt`、[parent POM](https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-parent/1.3/hamcrest-parent-1.3.pom) | BSD-3-Clause（已核對 JAR 三項條件） | B；Copyright (c) 2000-2006, www.hamcrest.org | 可商用；測試依賴 |
| S29 | Software / Libraries | Android SDK Platform 35／Android 平台 API | Camera2、MediaStore、系統 JSON、網路、字型等 | [Android SDK License Agreement](https://developer.android.com/studio/terms)、`app/build.gradle.kts`、Android imports | Android SDK 自訂授權；平台個別元件依各自授權 | 保留 SDK 隨附權利聲明；不能以專案授權重新授權 SDK | 開發工具／裝置平台，未整套提交；商用 App 開發依 SDK 條款，SDK 再散布另有限制 |
| S30 | Software / Libraries | WordPieceTokenizer.kt | 重現 BERT／Hugging Face 分詞行為 | `app/src/main/java/com/example/ai_camera/emotion/WordPieceTokenizer.kt` 註解稱 reimplemented；⚠️ 來源待人工確認 | ⚠️ License 待人工確認 | 確認是否曾複製或改寫外部程式；若有需提供來源及原聲明 | 未發現直接複製證據，也不能僅憑註解證明完全原創；Hugging Face 僅是實作線索，不列成已安裝依賴 |
| S31 | Software / Libraries | 疑似 Android Studio 範本程式／XML | 初始測試、主題、備份設定 | `ExampleUnitTest.kt`、`ExampleInstrumentedTest.kt`、`ui/theme/`、`res/xml/`；⚠️ 來源待人工確認 | ⚠️ License 待人工確認 | 確認 IDE 範本版本／產生紀錄及有無需保留檔頭 | 檔案具有範本式註解；尚無來源文件，不自行套用 AndroidX 授權 |

### Attribution / Notice requirements

- **A — Apache-2.0**：散布時附授權全文、保留相關 Copyright／專利／商標／Attribution 聲明；上游有 NOTICE 時保留相關內容；修改上游檔案時標示修改。允許商用，無非商用限制。依據：[Apache 官方條款第 2–4、6 節](https://www.apache.org/licenses/LICENSE-2.0)。專案採 MIT 也不會免除這些第三方義務。
- **M — MIT**：在軟體副本或重要部分保留 Copyright 與許可聲明。ONNX Runtime 的 Copyright 為 Microsoft Corporation，依據：[v1.20.0 LICENSE](https://github.com/microsoft/onnxruntime/blob/v1.20.0/LICENSE)。允許商用。
- **E — EPL-1.0**：保留原 Copyright；散布 EPL 程式原始碼時附 EPL，散布其物件碼時提供取得對應原始碼的方式，修改部分依 EPL 義務處理；允許商用但有商業散布責任條款。依據：[JUnit 4.13.2 LICENSE](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt)。目前用於測試，未見修改 JUnit 原始碼；不能因此推論 App 原創碼必須改成 EPL。
- **B — BSD-3-Clause**：原始碼保留 Copyright、條件與免責聲明；二進位散布在文件或其他附帶材料保留同樣內容；不可借作者名稱背書。允許商用。依據為 S28 的 JAR 內 LICENSE。

### Native and transitive licensing boundary

ONNX Runtime 1.20.0 的 [ThirdPartyNotices.txt](https://github.com/microsoft/onnxruntime/blob/v1.20.0/ThirdPartyNotices.txt) 含多個原生／建置元件及個別條款，包括 Intel 自訂條款與 LGPL 相關文字。本次下載並查看 Android AAR 及其 `classes.jar`，未找到 LICENSE／NOTICE entry；不能直接把上游跨平台總清單內每個元件都判定為此 Android APK 已使用，也不能把整個原生套件簡化成只有 MIT。**Android ABI 實際包含哪些元件、各自授權與所需 notices：⚠️ License 待人工確認。** 上游提及 LGPL 不等於已確認本 App 連結 LGPL。

Gradle／Kotlin compiler／AGP 也可能包含另外授權的元件。完整間接依賴尚未解析，含 AndroidX 的額外間接元件、native dependencies 及 build/test dependencies；未查核者一律為 **⚠️ License 待人工確認**，不以父套件授權代替。

本文件是來源揭露索引，尚未把所有上游授權全文、Copyright 與 NOTICE 複製進 APK；發行二進位前需依實際元件補上相應材料。

## AI Models

| ID | 項目類型 | 專案／Name | 用途／Purpose | 來源／Source | License | 需要 Attribution／NOTICE | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| M01 | AI Models | 微調中文 BERT／emotion_int8.onnx | 七類文字情緒推論 | `BertEmotionClassifier.kt`、`.gitignore` 指向本機 `emotion_model/`；⚠️ 來源待人工確認 | ⚠️ License 待人工確認 | 待 Base Model、checkpoint、訓練與匯出工具授權確認 | 權重／config／訓練腳本均未提交；商用、再散布與改作權限待確認 |
| M02 | AI Models | BERT 中文 WordPiece 詞彙表 | 將回覆轉換成模型 token IDs；測試比對 | [google-bert/bert-base-chinese vocab.txt](https://huggingface.co/google-bert/bert-base-chinese/blob/main/vocab.txt)、[官方模型卡](https://huggingface.co/google-bert/bert-base-chinese/blob/main/README.md) | Apache-2.0（對應官方詞彙表／模型卡） | A；保留 Google BERT 來源與相關聲明 | 可商用；此比對不確認 M01 權重來源 |

M02 包含 `app/src/main/assets/emotion/vocab.txt` 與 `app/src/test/resources/vocab.txt`。兩份本機檔案 SHA-256 均為 `a3028b9473053a94325f6accc48082cf2c7475b305de708688d94b78ec9ed8b5`；查核時官方下載檔 SHA-256 為 `45bbac6b341c319adc98a532532882e91a9cefc0329aa57bac9ae761c27b291c`。逐行比較後 **21,128 個 token 與排列完全一致**，檔案位元組並非完全一致，因此不是以原始檔 hash 相同作結論。

團隊先前表示模型與詞彙表由 AI 生成；本次找到的詞彙表對應關係及 fine-tuned 註解仍需併同揭露。`google-bert/bert-base-chinese` 是**詞彙表匹配的候選 Base Model**，不是已證實的 M01 Base Model。不同模型可共用同一詞彙表；請提供 `_name_or_path`、model card、模型下載連結、revision、訓練腳本／紀錄，才能確認微調模型的完整授權鏈。不可將模型、權重與訓練資料一律視為「AI 生成所以無第三方授權」。

Gemini 為遠端模型服務，統一列在 External Services，未下載其權重，也不重複計數。

## Datasets

| ID | 項目類型 | 專案／Name | 用途／Purpose | 來源／Source | License | 需要 Attribution／NOTICE | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| D01 | Datasets | 團隊自行拍攝的資料 | 專案拍攝資料 | 團隊確認由成員自行拍攝；本次未在儲存庫找到獨立照片資料集 | MIT（團隊授權） | 保留 Copyright (c) 2026 AI_camera contributors 與 MIT 許可聲明 | 依團隊提供的來源與授權資訊記錄；未聲稱這些照片是 BERT 文字情緒訓練資料 |
| D02 | Datasets | Kotlin 測試文字與 token ID fixtures | 分詞、情緒、構圖與方向邏輯測試 | `app/src/test/java/`；WordPieceTokenizerTest 稱 token IDs 由 Hugging Face tokenizer 產生；⚠️ 來源待人工確認 | ⚠️ License 待人工確認 | 確認測試句子是否團隊自寫，若引用外部語料則附來源 | 無獨立第三方資料集檔案；詞彙表已計入 M02；原創測試資料可在確認後依團隊授權 |

相機畫面、拍攝照片與 Gemini 回覆是執行時資料，未作為樣本資料庫提交。Gemini 輸入／輸出適用服務條款，不能由本專案 LICENSE 代替。

## Fonts and Icons

**Material Icons：S16**（已計數）。程式使用 `androidx.compose.material.icons.*`，來源由 Maven POM 與 [Google Material Design Icons LICENSE](https://github.com/google/material-design-icons/blob/master/LICENSE) 交叉核對為 Apache-2.0；用於操作圖示，遵循 A。這些圖示與 AI 生成的 Mochi 圖示是不同來源。

| ID | 項目類型 | 專案／Name | 用途／Purpose | 來源／Source | License | 需要 Attribution／NOTICE | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | Fonts / Icons | Android 系統預設字型 | UI 文字 | `ui/theme/Type.kt` 的 `FontFamily.Default`；實際字型由裝置提供 | 未散布字型檔；實際裝置字型 ⚠️ License 待人工確認 | 未附帶字型副本；若未來嵌入需逐檔確認 | 未找到 `.ttf`／`.otf`，不猜測一定使用 Roboto 或 Noto；無法替裝置字型授予商用再散布權 |

## Images and Media

| ID | 項目類型 | 專案／Name | 用途／Purpose | 來源／Source | License | 需要 Attribution／NOTICE | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| I01 | Images / Media | Mochi GIF、角色圖與 App 圖示 | 七種表情、助理頭像、啟動圖示 | 團隊使用 AI 製作 | MIT（團隊授權） | 保留 Copyright (c) 2026 AI_camera contributors 與 MIT 許可聲明 | 共 7 GIF、20 WebP；團隊明確指定 MIT，生成工具及參考素材條款未提供 |

檔案範圍：

- `app/src/main/assets/icon_gif/0.gif` 至 `6.gif`：7 個表情。
- `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_assistant_avatar.webp`：5 個解析度版本。
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp`、`ic_launcher_round.webp`、`ic_launcher_background.webp`：15 個解析度／形狀版本。
- Adaptive icon XML 引用上述圖檔與系統透明色，沒有另藏一份外部圖片。未發現背景音樂、音效、影片或 stock images 來源聲明。

來源「AI 製作」與 MIT 授權均依團隊明確聲明記錄。此授權涵蓋團隊有權授予的部分；生成工具與參考素材條款尚未提供，並未經本次查核確認。這些素材不繼承 BERT、Material Icons 或 ONNX Runtime 的授權。

## External Services

| ID | 項目類型 | 專案／Name | 用途／Purpose | 來源／Source | License | 需要 Attribution／NOTICE | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| E01 | External Services | Google Gemini API／Google AI Studio | 攝影問答、風格參數、構圖與姿勢影像分析；取得 API key | `GeminiClient.kt` 的 `generativelanguage.googleapis.com/v1beta/models`；[Gemini 條款](https://ai.google.dev/gemini-api/terms)、[Google APIs 條款](https://developers.google.com/terms) | 自訂服務條款，非開源模型授權 | 依 API／回傳內容要求保留適用 Attribution；不可將供應商權利聲明改成專案授權 | 可用於符合條款的業務／專業開發；有地區、年齡、用途及資料處理條件，非無條件商用 |

預設模型為 `.env.example`／BuildConfig 指定的 `gemini-2.5-flash`，可由設定更換。模型權重不在儲存庫內。查核的 Gemini 附加條款生效日為 2026-03-23；免費／付費服務的資料處理條件不同，生成內容也可能有適用的 attribution 要求。未發現 Google Search／Maps grounding 呼叫，因此未將其列為已使用元件。

沒有找到 OpenAI、ElevenLabs、Firebase、Supabase 或其他 SaaS 的依賴／呼叫證據。Hugging Face 是分詞／模型來源線索，非此 App 實際呼叫的雲端推論服務。

## Project license decision and outstanding checks

團隊已選擇 [MIT License](LICENSE)，適用於團隊可授權的原創程式碼、文件、自行拍攝資料及 AI 製作素材。Copyright 署名為 `2026 AI_camera contributors`。第三方元件保留各自授權；本專案 MIT 不替代第三方模型、資料、素材或外部服務的條款，亦不代表未確認項目已完成授權查核。

提交／發行前待確認：

1. 確認 `2026 AI_camera contributors` 的集體署名涵蓋實際權利人，以及團隊對原創貢獻具有授權權限。MIT 已依團隊選擇建立。
2. M01 的確切 Base Model、權重、文字情緒訓練資料及微調授權；D02 測試資料來源。團隊已確認 D01 為自行拍攝資料，不能以此推定 BERT 文字訓練資料的來源。
3. I01 的生成工具條款、參考圖來源與商用／再散布權；S30、S31 的外部程式／範本來源。
4. S25 Wrapper JAR 的來源／版本，以及最終 Gradle 解析後全部間接元件。本機 Wrapper JAR SHA-256 為 `e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637`，與官方 v8.10.2 Repository 的 Wrapper JAR（`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`）不同；這可能是舊版本 Wrapper，不能據此認定異常或確定版本。
5. S18 原生元件與上游 ThirdPartyNotices 的實際 Android 適用範圍。未確認本專案直接依賴含 GPL、AGPL、LGPL 或 CC BY-NC；但未完成 native／transitive 查核，不能作全面排除。EPL-1.0 已確認存在於 JUnit 測試依賴。
6. 自訂條款已確認存在於 Gemini 與 Android SDK；ONNX 上游 NOTICE 另有 Intel 自訂條款，是否適用本 Android 發行檔待查。
7. 依實際發行範圍附 LICENSE 全文、Copyright 與 NOTICE。這份索引的連結不能取代散布時必須隨附的完整聲明。

README 的既有第三方來源及 License 章節已整合本次查核結果；其他章節保持不變。
