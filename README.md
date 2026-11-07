# YouTube KTV — Android 手機版 + 電視版原始碼

## 已實作
- YouTube Data API v3 關鍵字搜尋，顯示最多 20 個結果
- YouTube IFrame 官方嵌入式播放器播放搜尋結果
- Android 手機 Launcher 與 Android TV Leanback Launcher
- 遙控器可聚焦的原生搜尋及結果按鈕
- 透過 Android 文件選擇器播放本機音訊
- 本機音訊左聲道 / 右聲道 / 立體聲切換
- 本機雙聲道音訊中央聲道抵銷（簡易降低人聲，非 AI 分離）

## 重要限制
YouTube 官方 IFrame 播放器不開放原始音訊 PCM，因此無法將聲道切換或消除人聲套用於 YouTube 影片。
這些控制 **只作用於本機音訊**，APP 不下載、擷取或繞過 YouTube 串流保護。
中央聲道抵銷可能同時移除低音、鼓或其他中央定位的樂器，也不保證去除原唱。
本機音訊目前支援裝置解碼器輸出 PCM16 的單/雙聲道格式。
電視版目前是可用遙控器聚焦的基礎介面，尚未進行實機 TV UX 測試。

## 編譯步驟
1. 使用 Android Studio（支援 AGP 8.7.3、JDK 17）。
2. Google Cloud Console 啟用 YouTube Data API v3，建立 API Key。
3. 在專案根目錄的 `gradle.properties` 末尾加入：
   `YOUTUBE_API_KEY=你的API金鑰`
4. Gradle Sync 後，選擇 Build Variants：
   - `mobileDebug`：手機版 APK
   - `tvDebug`：電視版 APK
5. Build > Build APK(s)。
6. 產生路徑：
   `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk`
   `app/build/outputs/apk/tv/debug/app-tv-debug.apk`

## 上線前建議
- 將 API 金鑰限制在 Android 應用程式與適當 API（正式環境可改用受控後端代理，避免金鑰濫用）。
- 在手機與 Android TV 真機驗證 YouTube IFrame 播放、遙控器焦點、編解碼器相容性。
- 增加搜尋分頁、播放清單、錯誤回報、播放暫停按鈕、TV 專用大字體介面。
- 若需真正 AI 人聲分離，應只對具有合法處理權限的本機音訊加入專門分離模型。

## V1.1 更新
- 搜尋結果下一頁（YouTube Data API nextPageToken）
- 影片收藏、查看收藏及移除收藏（SharedPreferences）
- YouTube 影片暫停與繼續播放按鈕
- 手機與電視兩種 flavor 版本號更新為 1.1

## 尚未完成 / 待驗證
- 未提供已編譯 APK：目前環境沒有 Android SDK / Gradle，無法執行編譯及實機測試。
- YouTube 官方嵌入式播放器是否在特定 Android TV 裝置正常播放，需實機測試。
- YouTube 音訊無法透過本專案做左右聲道切換及人聲消除。
- 本機音訊播放器尚未支援所有編碼格式與完整錯誤訊息回報。
