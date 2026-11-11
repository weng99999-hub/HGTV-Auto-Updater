# HGTV 更新助手（Android 8+）

這是一個獨立 Android 更新助手專案，不修改 HGTV 本體。它會查詢 `pan8664716/HGTV` 的 GitHub 最新 Release，尋找 APK 附件，並在版本較新時自動下載。

## 功能
- 啟動時立即檢查 GitHub 最新正式 Release。
- 使用 Android JobScheduler 每 24 小時背景檢查一次；開機後重新排程。
- 找到新版後以 Android DownloadManager 自動下載。
- 以你提供的 HGTV 1.3.5.1 與 1.3.6.2 APK 所提取的簽章 SHA-256 指紋驗證下載檔。
- 驗證 APK 套件名稱與目前已安裝、簽章相符的 HGTV 一致。
- 由使用者按「安裝已下載更新」後，透過 Android PackageInstaller 安裝；系統仍會要求使用者確認。

## 安全指紋
`27DA90725C2BCEC6C334096EE438877B480875DFAA1339B5DBEDC5FD9FCEE019`

此指紋是從使用者提供的兩個 APK 的 `META-INF/CERT.RSA` 實際擷取。若上游更換簽章，更新助手會拒絕安裝；需人工審核新憑證後才可更新常數。

## 建置
1. 使用 Android Studio（建議近期版本）開啟本資料夾。
2. 使用 JDK 17、Android SDK Platform 35、Android Gradle Plugin 8.6.1 / Gradle 8.7。
3. 等待 Gradle Sync 完成。
4. 選擇 `Build > Build Bundle(s) / APK(s) > Build APK(s)`。
5. Debug APK 預設輸出於 `app/build/outputs/apk/debug/app-debug.apk`。
6. 將 APK 安裝到 Android 8 電視盒，首次開啟後允許網路與「安裝未知應用程式」權限。

## 使用
- 開啟助手會立即檢查更新；每天也會背景檢查一次。
- 發現新版會自動下載並通知。
- 下載完成後開啟助手，按「安裝已下載更新」。
- 首次使用需在 Android 設定中允許「HGTV 更新助手」安裝未知應用程式。
- Android 系統仍會顯示安裝確認；本工具不嘗試繞過系統確認。

## 限制與注意
- Android 8 的 JobScheduler 不保證精確在每天固定時間執行；省電策略、盒子待機或廠商限制可能延後背景檢查。開啟助手時會立即補檢。
- GitHub API 或網路不可用時會保留原有 HGTV，不會解除安裝或清除資料。
- 本專案不會修改 HGTV 本體、不會取得 HGTV 私鑰，也不會繞過 Android 安裝安全機制。
- 目前這份交付是可建置的 Android Studio 原始碼專案；目前執行環境沒有 Android SDK / Gradle，也無法連線下載建置工具，因此尚未產生或測試可安裝 APK。請勿把原始碼 ZIP 當成 APK。

## 不安裝 Android Studio 的建置方式（GitHub Actions）
專案已附 `.github/workflows/build.yml`。將整個專案資料夾上傳到你自己的 GitHub Repository（預設分支 `main` 或 `master`），開啟該 Repo 的 Actions，執行 **Build Android APK** 工作流程。成功後到該次 workflow 的 Artifacts 下載 `HGTVAutoUpdater-Android8-debug`，解壓後即可取得 `app-debug.apk`。這是更新助手自己的 Debug 簽章，不會改變 HGTV 官方 APK 的簽章。
