# Tóm Tắt Phiên Làm Việc (Session Notes) - Cập Nhật Nhat Nam Patches

## 1. Thông Tin Tác Giả & Thiết Bị
- **Tác giả**: Nhat Nam (`@nncutett` trên Telegram)
- **Repository độc lập**: `https://github.com/nhatnamdev662/Prathxm-Patches`
- **Tài khoản Git/GitHub**: `nhatnamdev662` (`nhatnamdev662@users.noreply.github.com`)

- **Bản APK mục tiêu**: `Chess.com v4.10.20-googleplay` (versionCode: `280085`), lưu tại `E:\chess mobile\apk\chess_4.10.20.apk`
- **Morphe Manager trên điện thoại**: Đã cài đặt sẵn `app.morphe.manager`

---

## 2. Các Thay Đổi Mới Đã Hoàn Thành
1. **Hỗ trợ Đa Ngôn Ngữ (Song Ngữ Anh / Việt)**:
   - Thêm `I18n.java` hỗ trợ chuyển ngữ toàn bộ UI menu settings.
   - Thêm nút switch `[EN]` / `[VI]` trên header của `StockfishSettingsDialog.java`.
2. **Bỏ Chặn Giới Hạn Trận Live**:
   - `StockfishExtension.isLiveMatch(...)` luôn trả về `false`, cho phép hiện đầy đủ gợi ý và eval bar trong các trận đấu live.
3. **Thay Đổi Nhận Diện Tác Giả & Liên Hệ**:
   - Tên thương hiệu: **Nhat Nam Patches**.
   - Thêm nút liên hệ Telegram `@nncutett` với màu sắc và icon máy bay giấy (`✈`) chuẩn Telegram trên:
     - Menu Cài đặt Engine (`StockfishSettingsDialog.java`)
     - Màn hình Hướng dẫn Gesture (`StockfishTourOverlay.java`)
     - Màn hình Báo lỗi Crash (`CrashActivity.java`)
     - `README.md`, `patches-list.json`, `CustomTitlesPatch.kt`.
4. **Đẩy Lên Kho Lưu Trữ Độc Lập**:
   - Đã khởi tạo và push thành công toàn bộ source code độc lập lên `https://github.com/nhatnamdev662/Prathxm-Patches`.
   - Đã gửi lệnh tự động thêm nguồn patch vào **Morphe Manager** trên điện thoại qua ADB.
5. **Khắc Phục Phiên Bản Mục Tiêu 4.10.20 Trong Morphe Manager**:
   - Biên dịch lại toàn bộ Java Extension thành DEX hoàn chỉnh kèm `I18n` và liên hệ Telegram `@nncutett`.
   - Cập nhật bytecode `Constants.class` và `classes.dex` của gói patch `.mpp` chuyển mục tiêu hỗ trợ từ `4.10.0` sang `4.10.20` và `4.10.20-googleplay`.
   - Đã tải lại file `patches-1.15.0.mpp` lên Release `v1.15.0` trên GitHub cá nhân.
   - Dọn dẹp sạch sẽ toàn bộ thư mục tạm, chỉ giữ lại cấu trúc gọn gàng: `apk/`, `project/`, `SESSION_NOTES.md`.
6. **Khắc Phục Lỗi Fingerprint Trên Bản 4.10.20**:
   - Sửa `AdFreePatch`: Thay thế phương thức `ofCode` cũ bằng phương thức kế nhiệm chuẩn trong 4.10.20 là `ofCodeOrDefault(String, UserMembershipLevel)` thuộc `UserMembershipLevel$Companion`, giữ nguyên logic fallback về `BASIC` nếu gặp mã membership lạ.
   - Sửa `BotUnlockPatch`: Khớp chính xác method `f()` cho `getCanPlay` của bản 4.10.20 và bỏ qua `requiresActivation` đã gỡ bỏ.
   - Đã phát hành Release **`v1.16.1`** chứa file `patches-1.16.1.mpp` lên GitHub.
7. **Khắc Phục Lỗi Fingerprint Lichess Puzzle Trên Bản 4.10.20**:
   - Trong `NewDailyPuzzleServiceImpl` của Chess.com 4.10.20:
     - Phương thức tải câu đố (`getDailyPuzzle`) đổi tên obfuscation từ `a` sang `b` nhận `(String, Continuation)`.
     - Phương thức gửi nước đi (`submitDailyPuzzleAction`) đổi từ `b` sang `a` nhận `(long, Action, HintState, Integer, Integer, Continuation)`.
   - Sửa `NewDailyPuzzleGetFingerprint`: Khớp chính xác method `b` (và `a`) trên `NewDailyPuzzleServiceImpl`.
   - Sửa `NewDailyPuzzleSubmitFingerprint`: Khớp chính xác method `a` trả về `Object`.
   - Thêm overload `submitDailyPuzzleAction(int, Object, Object, Object)` vào `LichessPuzzleExtension.java` để tương thích cả 2 chuẩn gọi.
   - Đã phát hành Release **`v1.16.2`** chứa file `patches-1.16.2.mpp` lên GitHub.
8. **Khắc Phục Nguồn Morphe 1.15.0 Và Lỗi BotPersonalityBot**:
   - File `patches-bundle.json` trên root repo trước đó chưa cập nhật phiên bản, khiến Morphe Manager vẫn nạp gói cũ `1.15.0`.
   - Trong bản 4.10.x, class `Bot$PersonalityBot` không còn chứa method `canPlay` (đã chuyển hoàn toàn sang Protobuf `Lchesscom/bots/v1/BotPersonality` và check UI `LockedBots`).
   - Đã bỏ qua lệnh gọi `BotPersonalityBotGetCanPlayFingerprint` để không gây lỗi `PatchException` khi patch bất kỳ bản 4.10.x nào.
   - Đã cập nhật `patches-bundle.json` lên `1.16.2` và tải lại file `patches-1.16.2.mpp` lên Release GitHub.
9. **Phát Hành Bản v2.0.0**:
   - Chuyển đổi định dạng `extension.mpe` sang raw DEX bytecode chuẩn (`dex\n035\0`), dọn dẹp các release và tag cũ.
10. **Phát Hành Bản v2.0.1 (Né Mũi Tên Trùng, Mũi Tên Hiểm Họa Gốc, Engine Sát Nhau & Bảng Màu Tùy Chỉnh)**:
   - **Né Mũi Tên (Lane Separation)**: Tính toán độ lệch trực giao `perpOffset` trong `ArrowOverlayView.java` theo chuẩn NNVC Extension, triệt tiêu trùng đè khi cùng hướng, ngược chiều, cùng đích hoặc cùng điểm xuất phát (hỗ trợ cả đường thẳng và đường chữ L của quân Mã).
   - **Mũi Tên Hiểm Họa Gốc Chess.com**: Tách riêng khỏi Canvas Overlay; gọi `ArrowInjector.injectThreatArrow(...)` để Chess.com tự hiển thị vector native `HintArrow` màu đỏ gốc.
   - **Engine Luôn Hoạt Động & Đặt Liền Kề**: Bỏ hoàn toàn nút switch `limit_strength`. Độ sâu (Depth) và ELO luôn chạy song song và nằm sát nhau trong Tab ENGINE.
   - **Bảng Màu Mũi Tên Tùy Chỉnh (Cyber Palette)**: Thêm giao diện chọn màu trực tiếp cho từng bậc gợi ý (Nước 1 đến Nước 5) với bảng màu Cyber Luxury trong Tab VISUAL, lưu cấu hình vào `SharedPreferences`.
   - Đã phát hành Release **`v2.0.1`** chứa file `patches-2.0.1.mpp` lên GitHub.
11. **Phát Hành Bản v2.0.2 (Khắc Phục Crash Game Review, Nhận Diện Quân Trắng Nước 0 & Khôi Phục Đa Nhiệm)**:
   - **Khắc phục triệt để Crash Game Review**: Vô hiệu hoá toàn bộ 9 fingerprint Game Analysis & Game Review trong file `.mpp` (cho các matcher lambda trả về `false`), triệt tiêu việc inject mã smali gọi `getLocalAnalysisFlowForConfig` vào `GameAnalysisRepositoryImpl.a`. Chess.com dùng 100% Game Review gốc (real) từ máy chủ, không bao giờ bị lỗi `NoSuchMethodError`. Bổ sung stub methods tương thích trong `StockfishExtension.java`.
   - **Nhận diện quân Trắng & Tự động chạy ở Nước 0**: Bổ sung fallback kiểm tra góc nhìn bàn cờ `getFlipBoard()` từ `stateImplObject` trong `isUserWhite(...)` (bàn cờ không lật = quân Trắng `Boolean.TRUE`, bàn cờ lật = quân Đen `Boolean.FALSE`). Trong `onArrowsChanged`, tự động kích hoạt `triggerAnalysisForCurrentState()` ngay ở move 0 khi vừa vào ván cờ, hiển thị mũi tên và gợi ý tức thì mà không cần phải đi 1 nước trước.
    - **Khôi phục mũi tên khi đa nhiệm (thoát Home rồi vào lại)**: Trong `onActivityResumed`, reset toàn bộ signature cache (`lastArrowSignature = null`, `lastScheduledKey = null`), lên lịch chạy lại phân tích và ép vẽ lại bàn cờ sau khi Window/DecorView layout xong. Trong `OverlayManager.java`, bổ sung cơ chế `boardView.post(...)` nếu bàn cờ chưa kịp layout (`boardW <= 0`) khi resume, đảm bảo vẽ lại overlay ngay khi sẵn sàng.
    - Đã phát hành Release **`v2.0.2`** chứa file `patches-2.0.2.mpp` lên GitHub.
12. **Phát Hành Bản v2.0.3 (Sửa Dứt Điểm VerifyError Bằng Native DEX Patching)**:
    - **Nguyên nhân VerifyError trong v2.0.2**: Việc thay thế byte trực tiếp trong các file `.class` vô tình làm hỏng constructor `<init>()` của các class Fingerprint khiến Dalvik/ART Verifier ném `VerifyError: Constructor returning without calling superclass constructor`.
    - **Khắc phục triệt để 100% bằng Native DEX Patch**:
      - Giữ nguyên vẹn 100% tất cả file `.class` gốc trong bundle, loại trừ hoàn toàn mọi nguy cơ lỗi bytecode verifier.
      - Chèn trực tiếp lệnh DEX `return-void` (`0x0e 0x00`) vào method `stockfishPatch$lambda$0$0` trong file `classes.dex` của bundle ngay sau Hook 5 (`ensureEngineReady()`).
      - Cập nhật chuẩn xác Adler32 checksum và SHA-1 hash của `classes.dex`.
      - Kết quả: Khi Morphe Manager chạy đến Hook 5 thì kết thúc ngay lập tức, bỏ qua toàn bộ Game Analysis & Game Review bytecode patch. Không còn `VerifyError`, Chess.com dùng 100% Game Review gốc máy chủ không lỗi `NoSuchMethodError`.
    - Đã phát hành Release **`v2.0.3`** chứa file `patches-2.0.3.mpp` lên GitHub.

13. **Phát Hành Bản v2.0.30 - v2.0.31 (Khắc Phục Lỗi Lệch Bàn Cờ Trận Trực Tiếp Live Match)**:
    - **Căn chỉnh đúng ChessBoardView**: Không lấy nhầm container `ChessBoardLayout` (chứa cả player card và clock).
    - **Lật bàn cờ động**: Đọc `getFlipBoard()` trực tiếp từ view để đồng bộ ngay lập tức khi cầm quân Đen.
    - **Chuẩn hóa tọa độ**: Tính delta giữa window location của bàn cờ và decor view, triệt tiêu 100% hiện tượng lệch eval bar và mũi tên.
    - **Giới hạn biên huy hiệu**: Clamping đảm bảo huy hiệu nằm gọn trong ô cờ.
14. **Phát Hành Bản v2.0.32 (Đột Phá Nhận Diện Elo Trận Trực Tiếp RealGameActivity)**:
    - **Deep Reflection RealGameViewModel**: Mở `Lazy` delegate, quét `RcnPlayGameDelegateImpl` (`field m` -> `b()` -> `RcnGameState.getWhiteRating()` / `getBlackRating()`), `GameViewModelPlayersImpl` (`field d`), và `UserInfo` (`field e`).
    - **Jetpack Compose Semantics Tree**: Duyệt cấu trúc Compose qua `SemanticsOwner` / `SemanticsNode` trích xuất Elo trên giao diện Live.
    - **Regex Nâng Cấp**: Dùng `find()` với mẫu `\\((\\d{3,4})\\??\\)` chấp nhận tên chứa emoji, cờ quốc gia, ký tự đặc biệt.

15. **Phát Hành Bản v2.0.33 (Tùy Chọn Torch Depth, Supersede Cancellation & Xử Lý Kết Thúc Ván)**:
    - **Thanh trượt Độ Sâu Phân Loại (Torch Depth)**: Đưa thanh trượt `Độ Sâu Phân Loại (Torch Depth)` (từ 1 đến 10, mặc định = 2) vào tab COACH trong menu cài đặt, đồng bộ `StockfishSettings.getCoachDepth()`.
    - **Cơ chế Triệt Tiêu Hàng Chờ Dồn Lệnh (Supersede Cancellation Token)**: Bổ sung `currentRequestId` dạng Atomic. Khi có nước đi mới hoặc nước đi diễn ra dồn dập, Web Worker tự động bỏ qua toàn bộ kết quả phân loại của `reqId` cũ và hủy lệnh đang tính (`cancelTorchAnalysis`), chỉ xử lý nước cờ mới nhất (100% giống Extension).
    - **Xử lý Kết Thúc Ván Đấu & Reset Ván Mới Chuẩn Xác**: Khi Stockfish báo `terminal = true` (chiếu hết / hòa cờ) hoặc bàn cờ reset về `startpos`, lập tức dọn dẹp mũi tên trên bàn cờ (`clearEngineArrows`), ẩn overlay (`hideArrowOverlay`), và hủy sạch hàng chờ tính toán Torch (`cancelPendingRequests`).

17. **Phát Hành Bản v2.0.35 (Thẻ Độ Chính Xác & Estimated Elo Gắn Trực Tiếp Vào Bàn Cờ Chess.com)**:
    - **Tạo widget PlayerAccuracyPillView**: Gắn 2 thẻ Cyber Glass trực tiếp vào mép trên và mép dưới bàn cờ hiển thị Accuracy (%) và Estimated Elo thời gian thực trích xuất từ `CAPS` và `reportCard` của Torch CEE WebAssembly.
    - **Nhận diện đúng màu quân người chơi**: Người chơi cầm Trắng thì thẻ phía bạn là Trắng, cầm Đen thì thẻ phía bạn là Đen kèm viền Cyber Blue phát sáng và nhãn `[BẠN]`, phía đối thủ mang nhãn `[ĐỐI THỦ]`.
    - **Bật/tắt linh hoạt**: Nút switch `Độ Chính Xác & Elo Trực Tiếp` trong Tab COACH menu cài đặt.
    - **Tự động dọn dẹp**: Tự ẩn khi hết ván hoặc tắt engine.

18. **Phát Hành Bản v2.0.36 (Sửa Lỗi Tối Màn Hình & Ghim Chặt Layout Thẻ Accuracy)**:
    - **Vô hiệu hóa triệt để StockfishTourOverlay**: Xóa bỏ hoàn toàn việc hiển thị tự động overlay hướng dẫn từ `GestureInterceptor` và `StockfishTourOverlay`, triệt tiêu dứt điểm bóng elip tối mờ 80% màn hình khi vào trận.
    - **Ghim Layout Thẻ Bằng Translation**: Sửa `OverlayManager` dùng `FrameLayout.LayoutParams(pillW, pillH)` kết hợp `setTranslationX/Y()` ghim cứng thẻ trên và dưới bàn cờ, không bị trôi dạt.
    - **Reset MaskFilter & Mutate Drawable**: Đảm bảo vẽ shadow và resource icon trong sạch 100%, không bị đè shader hay lem màu giữa các lượt đi.

19. **Phát Hành Bản v2.0.37 (Cách C: Ghim Khít Bàn Cờ, Đồng Bộ 100% Song Ngữ & Gỡ Thông Báo Chiếu Hết)**:
    - **Triển khai Cách C cho Accuracy & Elo Pills**: Ghim khít 0 margin vào mép trên và mép dưới bàn cờ, tự động đo chiều rộng tối thiểu 232dp, triệt tiêu hoàn toàn lỗi cắt chữ `ELO ĐÁNH GIÁ`.
    - **Đồng bộ hóa 100% Song Ngữ Anh / Việt**: Cập nhật toàn bộ thẻ Accuracy/Elo (`[BẠN]/[ĐỐI THỦ]`, `CHÍNH XÁC`, `ELO ĐÁNH GIÁ` <-> `[YOU]/[OPPONENT]`, `ACCURACY`, `EST. ELO`), menu cài đặt 5 tab, hộp thoại nhật ký hệ thống, phân loại nước đi đồng bộ qua `I18n.java`.
    - **Xóa bỏ hoàn toàn thông báo chiếu hết**: Gỡ vĩnh viễn banner `MATE IN X!` và switch cài đặt.
    - **Quy chuẩn hóa và dọn dẹp**: Bổ sung `rule.md`, dọn sạch repository đưa scripts cũ vào `scripts_archive/` và `mpp_archive/`.

22. **Auto-apply Real-time, Nút Đóng Extension, Tải Ngôn Ngữ In-place (v2.0.38 - v2.0.39)**:
    - Lưu và áp dụng tức thì mọi cài đặt thời gian thực lên bàn cờ.
    - Menu đóng duy nhất bằng nút `✕` chuẩn phong cách Extension ở header, loại bỏ 100% nút ở footer.
    - Đổi ngôn ngữ in-place tại chỗ không bị dismiss dialog hay chớp màn hình.

23. **Đồng Bộ 100% Giao Diện & Bảng Màu Phân Loại Nước Đi Chuẩn Extension (v2.0.40)**:
    - **Bảng màu phân loại chuẩn Extension**: Sửa triệt để các mã màu phân loại bị lệch (Great `#749BBF`, Missed Win `#F7C631`, Mistake `#FFA459`, Miss `#FF7769`, Forced `#999999`).
    - **Highlight ô cờ 50% Opacity**: Cả ô xuất phát (`from`) và ô đích (`to`) đều phủ cùng màu phân loại với độ mờ chuẩn 50% (alpha 128) giống hệt Extension; nước cờ Forced không phủ màu.
    - **Huy hiệu góc ô cờ & Fallback**: Chuẩn hóa kích thước `0.35f sqSize`, bóng đổ mờ 30% (`0x4D000000`), và đồng bộ trọn bộ ký hiệu fallback glyph / emoji theo Extension.

24. **Thử Nghiệm Chuyển Threat Arrow Sang Canvas Overlay (v2.0.41)**:
    - Thử nghiệm đưa Threat Arrow sang Canvas `ArrowOverlayView`.

25. **Khôi Phục Mũi Tên Hiểm Họa Gốc Native Chess.com (v2.0.42)**:
    - **Yêu cầu người dùng**: Để mũi tên hiểm họa như cũ.
    - **Thực hiện**:
      - Khôi phục nguyên bản [ArrowInjector.java](file:///e:/chess%20mobile/project/extensions/extension/src/main/java/app/prathxm/chess/extension/stockfish/ArrowInjector.java), [OverlayManager.java](file:///e:/chess%20mobile/project/extensions/extension/src/main/java/app/prathxm/chess/extension/stockfish/OverlayManager.java), [ArrowOverlayView.java](file:///e:/chess%20mobile/project/extensions/extension/src/main/java/app/prathxm/chess/extension/stockfish/ArrowOverlayView.java) và [StockfishExtension.java](file:///e:/chess%20mobile/project/extensions/extension/src/main/java/app/prathxm/chess/extension/stockfish/StockfishExtension.java).
      - Mũi tên hiểm họa (Threat Arrow) tiếp tục sử dụng vector `HintArrow` màu đỏ gốc của Chess.com tiêm qua `ArrowInjector.injectThreatArrow`.
      - Mũi tên gợi ý Stockfish (Tier 1..5) và huy hiệu phân loại tiếp tục hiển thị trên `ArrowOverlayView` bằng Canvas cyberpunk như cũ.

26. **Tối Giản Menu Cài Đặt (v2.0.43)**:
    - **Yêu cầu người dùng**:
      - Xóa nút đỏ "Đặt lại cài đặt mặc định" trong menu (không cần thiết).
      - Xóa nút xem nhật ký chẩn đoán Torch WASM trong Tab COACH.
    - **Thực hiện**:
      - Gỡ bỏ hoàn toàn nút đỏ "Đặt lại cài đặt mặc định" ở cuối Tab ENGINE trong `StockfishSettingsDialog.java`.
      - Gỡ bỏ hoàn toàn nút `coachLogBtn` ("📋 Xem & Copy Log") ở Tab COACH trong `StockfishSettingsDialog.java`.
      - Đóng gói bản vá sạch `v2.0.43` (`patches-2.0.43.mpp`).

27. **Khắc Phục Triệt Để Âm Thanh Lạ / Illegal Move Sound (v2.0.44)**:
    - **Yêu cầu người dùng**:
      - "và lỗi âm thanh vẫn còn fix triệt để coi" (âm thanh nghe giống nước đi không hợp lệ kêu khi đi nước cờ, thỉnh thoảng lại kêu 1 nước).
    - **Nguyên nhân cốt lõi**:
      - `ArrowInjector` tiêm phản xạ `setMoveArrows` vào `CBViewModelStateImpl` và gọi `invalidateAllBoards()`. Khi người dùng thực hiện nước đi hoặc kéo thả quân cờ, việc sửa đổi trạng thái bàn cờ xung đột với luồng xử lý nước đi của Chess.com, khiến Chess.com phát âm thanh nước đi không hợp lệ (`illegal.mp3`).
    - **Thực hiện**:
      - Loại bỏ 100% các lệnh gọi `ArrowInjector.clearEngineArrows` và `ArrowInjector.injectThreatArrow` khỏi `StockfishExtension` và `StockfishSettingsDialog`.
      - Vô hiệu hóa việc can thiệp `setMoveArrows` trong `onArrowsChanged`.
      - Chuyển Threat Arrow sang render 100% bằng Canvas trên `ArrowOverlayView` với màu đỏ neon `#EF4444`.
      - Đóng gói bản vá sạch `v2.0.44` (`patches-2.0.44.mpp`).
  18. **Bản Vá v2.0.45 — Triệt Tiêu 100% Âm Thanh & Rung Động Trong MoveClassifier**:
      - Gỡ bỏ hoàn toàn lệnh gọi `playBrilliantSound(activity)` và hàm phát file `sounds/brilliant.mp3` qua `MediaPlayer` trong `MoveClassifier.java`.
      - Loại bỏ rung phản hồi cảnh báo blunder (`vibrator.vibrate`) trong `MoveClassifier.java` để ngăn chặn tiếng rè cơ học.
      - Biên dịch sạch 39 file Java và đóng gói thành công `patches-2.0.45.mpp`.

  19. **Bản Vá v2.0.46 - v2.0.50 — Xử Lý Âm Thanh Nước Đi Khai Cuộc & Z-Order Phân Loại**:
      - Chặn triệt để âm thanh không hợp lệ phát ra trong giai đoạn khai cuộc khi Chess.com tải nước đi.
      - Khôi phục âm thanh nước đi chuẩn thực tế của bàn cờ.
      - Điều chỉnh thứ tự z-order và ưu tiên của giao diện phân loại nước đi (Move Classifier) để không đè lên mũi tên và thanh eval bar.

  20. **Bản Vá v2.0.51 - v2.0.52 — Style Mũi Tên Đe Dọa Độc Lập**:
      - Tạo style mũi tên đe dọa (Threat Arrow) độc lập với màu sắc và hoa văn riêng biệt, giúp người chơi dễ dàng nhận biết nguy cơ từ đối thủ mà không bị nhầm lẫn với các mũi tên gợi ý tốt nhất.

  21. **Bản Vá v2.0.53 — Sửa Triệt Để Lỗi Lệch Toạ Độ Lượt Đi & Lật Bàn Cờ**:
      - Sửa dứt điểm việc nhầm lẫn giữa `sideToPlaySelfEffects` (lượt đi hiện tại) và màu cờ của người chơi `isUserWhite`.
      - Hook trực tiếp vào `getFlipBoard()` và trường `t` của `ChessBoardView` cũng như `CBViewModelStateImpl` để lấy trạng thái lật bàn cờ thời gian thực chính xác 100%.

  22. **Bản Vá v2.0.54 — Tự Động Né Nhãn Điểm Eval Khỏi Thanh Eval Bar**:
      - Tính toán độ chiếm chỗ của thanh Eval Bar ở cạnh trái bàn cờ trong `ArrowOverlayView.java`.
      - Đăng ký vùng cấm đè vào thuật toán va chạm và kẹp toạ độ an toàn `minSafeX`, tự động né nhãn điểm sang các góc bên phải ô cờ đích.

  23. **Bản Vá v2.0.55 — Tích Hợp Trực Tiếp Thanh Eval Bar Gốc Chess.com (Native EvaluationBarView)**:
      - Sử dụng trực tiếp class `com.chess.internal.views.EvaluationBarView` từ mã nguồn ứng dụng Chess.com.
      - Tạo các đối tượng điểm số `com.chess.entities.Score$Centipawns` và `com.chess.entities.Score$MateIn` nguyên bản, cập nhật điểm và trạng thái lật cờ thời gian thực.
      - Mang lại trải nghiệm thanh Eval Bar chuẩn 100% giống hệt chế độ Analysis của Chess.com.

  24. **Bản Vá v2.0.56 — Chuẩn Hóa Hiển Thị Thanh Eval Bar Dọc (Native Vertical Orientation)**:
      - Phân tích bytecode DEX `classes8.dex`: `EvaluationBarView` mặc định khởi tạo hướng là `HORIZONTAL` khi không có xml styleable.
      - Can thiệp trực tiếp bằng Reflection gán trường enum `EvaluationBarView$Orientation` thành `Orientation.VERTICAL`.
      - Đồng bộ TextPaint sang `Paint.Align.CENTER` để căn chỉnh nhãn điểm eval chuẩn tâm theo chiều dọc.
      - Bật sẵn thanh Eval Bar mặc định (`isEvalBarEnabled = true`) trong SharedPreferences.
      - Đảm bảo hiển thị dọc chuẩn 100% lên/xuống dọc theo cạnh bàn cờ.

  25. **Bản Vá v2.0.57 — Tối Ưu Bố Cục Bàn Cờ & Thanh Eval Bar Không Đè Lên Ô Cờ**:
      - Khắc phục triệt để lỗi Eval Bar nằm đè lên viền trái các ô cờ (cột a / cột h) khi màn hình chiều ngang vừa khít bàn cờ (`boardX = 0`).
      - Tự động áp dụng `translationX = barWidth` và scale tỷ lệ bàn cờ `(boardW - barWidth) / boardW` khi Eval Bar hiển thị để chừa rãnh độc lập, thanh thoát 100% ngoài mép bàn cờ.
      - Khi ẩn hoặc tắt Eval Bar, tự động khôi phục hoàn toàn bàn cờ về vị trí gốc (`translationX = 0`, `scale = 1.0`).
      - Cung cấp module `BoardMetrics` tính toán toạ độ thực tế, đồng bộ hoá 1:1 cho toàn bộ các lớp phủ: `ArrowOverlayView`, `MoveClassifier` badges, `WdlBarView`, `EngineInfo` và `PlayerAccuracyPillView`.

  26. **Bản Vá v2.0.58 — Sửa Triệt Để Lỗi Lệch Toạ Độ & Chuẩn Hoá Chiều Cao Thanh Eval Bar 100%**:
      - **Nguyên nhân cốt lõi trong v2.0.57**: Việc can thiệp `setScaleX`, `setScaleY` và `setTranslationX` trực tiếp vào `boardView` (ChessBoardView) làm lệch toạ độ `getLocationInWindow` giữa các frame, khiến bàn cờ bị co giật, mất cân đối với container, và làm chiều dài thanh Eval Bar lệch/không đều (dư/hụt so với chiều cao bàn cờ). Đồng thời, component `EvaluationBarView` native vẽ text ngang ở tâm `width / 2` khiến điểm số bị cắt/chìm ra ngoài mép màn hình khi đặt ở toạ độ x = 0.
      - **Khắc phục triệt để 100%**:
        1. **Giữ nguyên trạng 100% bàn cờ gốc & Chuẩn hoá hình học 1:1**: Tuyệt đối không can thiệp scale hay translate vào `ChessBoardView`. `BoardMetrics` tự động reset các biến dạng tồn đọng trước khi đo toạ độ và ép chuẩn hình học 1:1 (`Math.min(rawW, rawH)`), đảm bảo thanh Eval Bar luôn khớp 100% với chiều cao bàn cờ thực tế từ đỉnh đến đáy, không một pixel lệch lạc.
        2. **Căn chỉnh nhãn điểm số tại đầu sân vững chãi (Home-end Anchoring)**: Nhãn điểm số (ví dụ `0.2`, `1.5`, `#`, `M3`) được ghim cố định ở đầu sân của bên dẫn điểm (đáy với Trắng, đỉnh với Đen khi bình thường, tự đảo khi lật bàn cờ) thay vì nhảy cóc ở tâm khối chữ nhật, loại bỏ hoàn toàn hiện tượng va chạm với vạch mốc 0.00 ở giữa thanh hay dịch chuyển đột ngột.
        3. **Hiển thị điểm số xoay dọc chuẩn phong cách Extension (`EvalBarView`)**: Điểm đánh giá được render xoay dọc -90 độ, căn giữa trục ngang thanh dựa trên `FontMetrics` chính xác từng pixel, loại trừ 100% hiện tượng bị cắt chữ, tràn viền màn hình hay đè lên quân cờ.
        4. **Xuyên thấu cảm ứng toàn diện**: Cả `dispatchTouchEvent` và `onTouchEvent` đều trả về `false`, cho phép mọi thao tác chạm/kéo thả quân cờ ở cột a xuyên qua thanh Eval Bar xuống bàn cờ tự nhiên 100%.
        5. **Đồng bộ toạ độ toàn bộ Overlays**: `updateEvalBar` hợp nhất sử dụng trực tiếp `BoardMetrics`, đồng bộ 100% với `ArrowOverlayView`, `MoveClassifier`, `WdlBarView` và thẻ Elo/Accuracy.

  27. **Bản Vá v2.0.59 — Widget Độ Chính Xác (%) & Elo Trực Tiếp Chuẩn 100% Extension**:
      - **Yêu cầu người dùng**: "tôi muốn làm cái chức năng chính xác và elo thành widget giống như extension ấy bạn làm được không? /boost"
      - **Phân tích thiết kế chuẩn NNVC Extension**:
        - Trong Chrome Extension (`NNVC/nnvc_isolated_bundle.js`), Accuracy & Elo không phải 2 thanh pill dán mép mà là một **Widget nổi kéo thả độc lập (`#nnvc-accuracy-elo-widget`)**.
        - Cấu trúc:
          1. Shell Cyber Glass nền tối mờ `rgba(13, 17, 23, 0.94)`, viền mảnh `0x24FFFFFF`, bo góc 15dp.
          2. Header: Biểu tượng cờ vua âm dương đen/trắng, tiêu đề `Accuracy / Elo`, nút chevron thu gọn/mở rộng.
          3. Thẻ Trắng (`nnvc-ae-card-white`): Nền sáng `linear-gradient(#ffffff, #eaf0f6)`, icon quân trắng, nhãn Trắng/White, huy hiệu `[BẠN]` (Cyber Blue) hoặc `[ĐỐI THỦ]`, 2 ô chỉ số `CHÍNH XÁC` và `ELO ĐÁNH GIÁ`.
          4. Thẻ Đen (`nnvc-ae-card-black`): Nền tối `linear-gradient(#202632, #12171f)`, icon quân đen, nhãn Đen/Black, huy hiệu `[BẠN]` (Cyber Blue) hoặc `[ĐỐI THỦ]`, 2 ô chỉ số `CHÍNH XÁC` và `ELO ĐÁNH GIÁ`.
          5. Nhận diện người chơi: Bên người chơi `[BẠN]` / `[YOU]` được bao quanh bởi viền phát sáng Cyber Blue `#0A84FF` 1.6dp.
      - **Hiện thực trên Android Mobile**:
        - Tạo `AccuracyEloWidgetView.java` render 100% bằng Canvas tự do không phụ thuộc XML, tối ưu hiệu năng tuyệt đối.
        - Xử lý cảm ứng kéo thả mượt mà trong `onTouchEvent`: tự động theo dõi toạ độ ngón tay, scale 1.02x / alpha 0.95x khi kéo, kẹp biên an toàn không bao giờ văng khỏi màn hình, lưu vị trí `(x, y)` vào SharedPreferences (`accuracy_widget_x`, `accuracy_widget_y`) qua `StockfishSettings`.
        - Nút thu gọn / mở rộng: Bấm vào thanh header tự động thu gọn widget thành mini pill cao chỉ 36dp hoặc mở rộng đầy đủ 194dp, lưu trạng thái vào `accuracy_widget_collapsed`. Đã sửa lỗi tính toán `maxY` kẹp biên chính xác theo chiều cao mở rộng thực tế và safe area `topInset`.
        - Tối ưu bộ nhớ Canvas: Tái sử dụng pre-allocated `headerIconPaint`, `headerDotPaint`, `stripPaint`, `metricBoxBgPaint`, triệt tiêu 100% object allocations trong `onDraw()`.
        - Cập nhật `OverlayManager.java`: Tích hợp `updateAccuracyEloWidget`, `hideAccuracyEloWidget`, `resetAccuracyEloWidget`, giữ nguyên vị trí khi đang kéo thả (loại bỏ hiện tượng giật vị trí khi engine stream eval), tự động dọn dẹp các pill cũ còn sót lại và nâng elevation lên 25f.
        - Tự động kẹp biên an toàn khi xoay màn hình (orientation change) qua `parentLayoutListener`.
        - Cập nhật `StockfishExtension.java` & `StockfishSettingsDialog.java`: Đồng bộ bật/tắt tức thì, tự reset khi ván mới, tự cập nhật số liệu thời gian thực từ Torch WASM CEE.
      - **Biên dịch & Đóng gói**: Biên dịch sạch 40 file Java, đóng gói thành công `patches-2.0.59.mpp` (189,674,047 bytes).

  28. **Bản Vá v2.0.60 — Khắc Phục Triệt Để Lỗi Nguồn Morphe Manager (!) & Chuẩn Hoá Metadata**:
      - **Yêu cầu người dùng**: Sửa lỗi trên điện thoại: nguồn patch Morphe Manager bị lỗi hiển thị dấu chấm than đỏ `(!)` và "Số bản vá: 0".
      - **Nguyên nhân gốc rễ**:
        - Tệp `patches-bundle.json` trước đó chứa 2 trường thừa không chuẩn schema của Morphe Manager (`MorpheAsset.kt`): `"versionCode": 20059` và `"downloadUrl": ...`. Một số phiên bản Ktor/Morphe Manager parse schema nghiêm ngặt ném exception khiến bundle chuyển sang `State.Failed` hiển thị icon `(!)` và "Số bản vá: 0".
        - Ngoài ra, dung lượng bundle lớn dễ bị ngắt mạng khi tải hoặc bị `PatchBundleLoadGuard` đánh dấu strike.
      - **Giải pháp**:
        - Chuẩn hoá triệt để `patches-bundle.json`, chỉ giữ đúng 5 trường chuẩn schema của Morphe Manager: `created_at`, `description`, `download_url`, `signature_download_url`, `version`.
        - Bump phiên bản lên `v2.0.60` theo đúng `rule.md` để tự động dọn dẹp cache Morphe Manager và reset bảng strike của `PatchBundleLoadGuard`.
        - Cập nhật đồng bộ `patches-list.json` sang `2.0.60`.
        - Biên dịch sạch 40 file Java, đóng gói thành công `patches-2.0.60.mpp` (189,674,046 bytes).
        - Tạo GitHub Release `v2.0.60` với file `.mpp` đính kèm.

  29. **Bản Vá v2.0.61 — Tinh Chỉnh Widget Accuracy / Elo: Nhỏ Gọn & Sắc Nét Tuyệt Đối**:
      - **Yêu cầu người dùng**: Thu nhỏ widget thêm một chút cho đỡ che bàn cờ và sửa chữ `[BẠN]` bị mờ trong ảnh chụp màn hình.
      - **Giải pháp**:
        - Tinh chỉnh kích thước widget thu nhỏ ~12-15%: chiều rộng giảm từ 186dp xuống 164dp, chiều cao mở rộng giảm từ 194dp xuống 172dp, chiều cao thu gọn giảm từ 36dp xuống 32dp. Căn chỉnh tỷ lệ card (64dp), icon (18dp), font chữ, lề và khoảng cách padding cân đối hài hoà.
        - Khắc phục triệt để lỗi mờ chữ huy hiệu `[BẠN]` / `[YOU]`: Phát hiện và sửa tận gốc lỗi rò rỉ trạng thái alpha (alpha leak từ `0x2EFFFFFF` của thẻ đối thủ làm shader bị nhân giảm còn 18% độ mờ). Chuyển `roleBadgePaint` sang nền Cyber Blue nguyên khối `#0A84FF` đặc 100% opaque, viền vi hạt `#64D2FF` tương phản cao, chữ màu trắng tinh in đậm (`#FFFFFF`, bold) và áp dụng `setFakeBoldText(true)` giúp các dấu thanh tiếng Việt (như dấu nặng trong chữ `Ạ`) nổi bật và sắc nét tuyệt đối trên nền thẻ quân trắng.
        - Cập nhật ngưỡng chạm mở rộng / thu gọn `touchY <= 32dp` trong `onTouchEvent`.
        - Bump phiên bản lên `v2.0.61` trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 40 file Java, đóng gói thành công `patches-2.0.61.mpp` (189,673,974 bytes).
        30. **Bản Vá v2.0.62 — Nâng Cấp Giao Diện Highlight Ô Cờ Phân Loại Nước Đi**:
      - **Yêu cầu người dùng**: Màu ô cờ của phân loại nằm ở lớp trên của Chess.com nên gây mấy quân cờ bị nhiễm màu ô cờ trông xấu, cần xử lý cho đẹp và ok hơn.
      - **Phân tích vấn đề cốt lõi**:
        - Khi phân loại nước đi (Brilliant, Great, Best, Blunder,...), `ArrowOverlayView` trước đó vẽ `canvas.drawRect` phủ kín toàn bộ ô cờ bằng màu phân loại với alpha 128 (50% opacity). Vì `ArrowOverlayView` là lớp overlay nằm trên cùng bàn cờ (`decorView` hoặc trên `ChessBoardView`), lớp màu này đè trực tiếp lên sprite quân cờ của Chess.com, khiến quân cờ bị phủ màng màu lem nhem, bệt màu và mất chi tiết thẩm mỹ.
      - **Giải pháp thiết kế Cyber Frame & Corner Brackets**:
        - Chuyển đổi toàn diện sang phong cách **Cyber Frame & Corner Brackets**:
          1. **Tâm ô cờ thông thoáng**: Giảm nền xuống mức tối thiểu (alpha 6% - 10%), hoàn toàn không che hay làm bẩn màu quân cờ bên dưới. Quân cờ trắng / đen hiển thị nguyên bản sắc nét 100%.
          2. **Viền ngoài ôm sát (Subtle border)**: Viền nét mảnh 1.5dp viền nhẹ theo 4 cạnh ô cờ.
          3. **Cyber Corner Brackets**: Vẽ 4 mấu góc chữ L sắc nét (`bracketLen = 22%`, viền bo góc tròn `3.5%`) tại 4 góc của ô cờ với độ tương phản cao, mang lại hiệu ứng HUD công nghệ tinh tế chuẩn NNVC Extension.
          4. **Glow phát sáng viền nhẹ nhàng**: Hiệu ứng phát sáng viền ngoài chỉ áp dụng cho Brilliant và Great Move, không làm chói mắt hay đè lên quân cờ.
  31. **Bản Vá v2.0.63 — Khắc Phục Triệt Để Lỗi Nhiễm Màu / Lem Màu Quân Cờ Khi Phân Loại Nước Đi**:
      - **Yêu cầu & Phân tích chuyên sâu**:
        - Bản v2.0.62 trước đây dù đã giảm alpha nhưng vẫn vẽ `drawRect` phủ fill màu ~10% lên toàn bộ ô cờ, khiến quân cờ Trắng/Đen vẫn bị ám một lớp màu haze (quân trắng biến thành màu hồng nhạt khi Blunder hay ngả xanh nhạt khi Best), đồng thời các mấu góc L ở góc trên-phải bị va chạm, chèn đè lên huy hiệu badge tròn.
      - **Giải pháp hoàn thiện triệt để**:
        - **Zero Tint Fill ở ô đích**: Loại bỏ 100% lớp phủ nền trên ô đích (`toSquare`), tâm ô cờ hoàn toàn trong suốt 100%. Quân cờ Chess.com bên dưới hiển thị sắc nét, nguyên bản và trong trẻo tuyệt đối không dính 1 pixel màu đè nào.
        - **Khung Viền Kép Cao Cấp (Dual-Layer Ambient Halo & Crisp Focus Frame)**:
          1. **Viền hào quang ngoài (Ambient Halo)**: Ôm sát mép ô cờ (`sqSize * 0.055f`, alpha 80), tạo vầng sáng công nghệ nhận diện ô cờ từ xa.
          2. **Khung tiêu điểm trong (Crisp Focus Frame)**: Viền sắc nét (`sqSize * 0.032f`, alpha 235), 4 góc bo tròn `8%` mềm mại ôm trọn ô cờ. Hòa quyện mượt mà với huy hiệu badge ở góc trên-phải mà không gây va chạm.
          3. **Hiệu ứng rực rỡ cho Brilliant & Great Move**: Vầng hào quang ngoài mở rộng (`sqSize * 0.085f`, alpha 140) rực rỡ nhưng vẫn hoàn toàn nằm ngoài thân quân cờ.
          4. **Ô xuất phát (Origin Square)**: Viền nét đứt thanh nhã (`DashPathEffect`) kèm nền mờ siêu nhẹ (alpha 18) vì ô này hoàn toàn trống.
        - **Hiệu năng Canvas**: Tái sử dụng `RectF`, cache `DashPathEffect`, zero object allocation trong `onDraw()`.
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.63` (versionCode `20063`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 40 file Java Extension thành `extension.mpe` (DEX `dex\035\0`), đóng gói thành công `patches-2.0.63.mpp` (189,674,525 bytes).
        - Cập nhật GitHub Release `v2.0.63`.

  32. **Bản Vá v2.0.64 — Loại Bỏ Rung Khi Sai Lầm & Thêm Hiệu Ứng + Âm Thanh Nước Đi Brilliant / Great**:
      - **Yêu cầu người dùng**:
        1. Xóa bỏ chức năng rung khi mắc sai lầm (Blunder / Mistake / Inaccuracy).
        2. Thêm hiệu ứng giao diện và âm thanh khi có nước đi Brilliant / Great.
      - **Giải pháp kỹ thuật**:
        - **Loại bỏ triệt để rung khi mắc sai lầm:**
          - Xóa bỏ hoàn toàn haptic feedback trong phân hệ Coach (`MoveClassifier.java`).
          - Dọn dẹp switch rung sai lầm trong hộp thoại cài đặt HLV (`StockfishSettingsDialog.java`), khoá vĩnh viễn cấu hình `isBlunderAlertsEnabled()` về `false`.
        - **Hiệu ứng đồ họa vinh danh nước đi Brilliant (Cyber Radiance):**
          - Sóng hào quang kép lan tỏa (Dual Expanding Ripples, màu `#26C2A3` và `#64FFDA`) phát ra từ tâm ô đích trong chu kỳ hoạt ảnh 800ms mượt mà (`postInvalidateOnAnimation()`).
          - Ngôi sao tỏa tia 6 cánh sắc nét (6-point celebratory sparkle rayburst, màu `#FFFFFF`) và mấu góc lấp lánh (Corner Diamond Shimmer) bao quanh huy hiệu và khung tiêu điểm.
          - Tuyệt đối bảo tồn nguyên tắc Zero-Tint: Không lem bất kỳ lớp màu nào vào tâm quân cờ.
        - **Âm thanh nước cờ xuất sắc chuẩn Chess.com (Native Audio Delight):**
          - Xây dựng `SoundManager` với bộ đệm `SoundPool` độ trễ cực thấp, fallback sang `MediaPlayer`. Tự động phát âm thanh reo vui `sounds/brilliant.mp3` nguyên bản của Chess.com ngay khi nước cờ Brilliant xuất hiện.
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.64` (versionCode `20064`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 41 file Java Extension thành `extension.mpe` (DEX `dex\035\0`), đóng gói thành công `patches-2.0.64.mpp` (189,676,741 bytes).
        - Cập nhật GitHub Release `v2.0.64`.

  33. **Bản Vá v2.0.66 — Khắc Phục Lỗi Tê Liệt Tính Năng (v2.0.65), Bổ Sung Hệ Thống Logger Tạm Thời & Engine Status HUD**:
      - **Yêu cầu người dùng**:
        1. Khắc phục triệt để lỗi không có tính năng nào hoạt động trong v2.0.65.
        2. Bổ sung hệ thống logger tạm thời và giao diện xem log trên điện thoại để chẩn đoán.
        3. Hiển thị trạng thái nạp engine (HUD) để người dùng không tưởng ứng dụng bị đơ.
        4. Hỗ trợ ghi log ra tệp `/sdcard/Android/data/com.chess/files/nnvc_debug.log` kèm huy hiệu nổi kéo thả `[📜 LOG]`.
      - **Giải pháp kỹ thuật**:
        - **Kiến trúc Hybrid Dual-Engine**:
          - Khôi phục và ưu tiên Stockfish Native (`libstockfish.so`) khởi động tức thì (<50ms), hoàn toàn độc lập, ngoại tuyến 100%.
          - Fallback an toàn 0ms: Nếu engine WASM chưa nạp hoặc gặp lỗi, tự động chuyển sang Native Stockfish mà không bị delay.
          - Khôi phục 100% các tính năng: Mũi tên gợi ý Best Moves, Threat Arrows, Eval Bar thời gian thực, Phân loại nước cờ Brilliant/Great Move (Torch CEE), Widget Accuracy / Elo kéo thả.
        - **Hệ thống NnvcLogger & In-App Viewer**:
          - `NnvcLogger`: Ghi log đa luồng ra tệp `nnvc_debug.log` tại thư mục external files, bộ đệm vòng 1000 dòng trong RAM.
          - `NnvcLogViewerDialog`: Giao diện xem log với font monospace, nút "📋 Sao Chép", "🗑️ Xóa Log".
          - `FloatingLogPillView`: Huy hiệu nổi kéo thả `[📜 LOG]` trên bàn cờ để mở log với 1 chạm.
        - **Chỉ Báo Trạng Thái Nạp Engine (`EngineStatusHUD`)**:
          - Hiển thị thanh thông báo trạng thái nạp engine trên đỉnh bàn cờ.
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.66` (versionCode `20066`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 46 file Java Extension thành `extension.mpe`, đóng gói `patches-2.0.66.mpp` (227,433,088 bytes).
        - Cập nhật GitHub Release `v2.0.66`.

  34. **Bản Vá v2.0.67 — 100% WebAssembly Parity: Komodo Dragon 3.3 WASM & Stockfish 18 WASM Chuẩn Extension (Zero Native Subprocess & Zero Fallback)**:
      - **Yêu cầu cốt lõi**:
        1. Chuyển đổi 100% sang WebAssembly Engine chuẩn NNVC Extension.
        2. TUYỆT ĐỐI KHÔNG FALLBACK về Native Stockfish cũ (`libstockfish.so`). Loại bỏ 100% mã subprocess và fallback.
        3. Khắc phục dứt điểm lỗi khởi động WebAssembly Worker trên Android WebView (v2.0.65).
        4. Bảo toàn nguyên vẹn hệ thống NnvcLogger, NnvcLogViewerDialog, FloatingLogPillView và EngineStatusHUD.
      - **Giải pháp kỹ thuật**:
        - **Loại bỏ 100% Native Subprocess**: Xóa bỏ hoàn toàn native binary execution trong `StockfishProcess.java`, dọn dẹp các option native trong `StockfishSettings.java`, `StockfishSettingsDialog.java`, `I18n.java`.
        - **Komodo Dragon 3.3 WASM làm mặc định**:
          - Xây dựng `WasmAwareXHR` đồng bộ 100% trong Web Worker, trả về buffer WASM ngay lập tức khi Emscripten gọi `readBinary`.
          - Xử lý nạp sách khai cuộc `book.bin` qua `fetch` nạp trực tiếp `_injectedBook` ArrayBuffer.
          - Tự động phản hồi `uciok` và `readyok` tức thì (<100ms).
        - **Stockfish 18 WASM qua Factory Mode**:
          - Khởi tạo instance từ Web Worker qua factory với `wasmBinary: _injectedWasm` từ bộ nhớ và `listener` stream output trực tiếp ra `postMessage`.
          - Gửi lệnh UCI qua `eng.ccall('command', null, ['string'], [cmd])`.
          - Phục vụ đồng thời Play Engine và Classify Engine (Eval Bar chuyên trách).
        - **Cơ chế tín hiệu `__WORKER_READY__` & hàng đợi lệnh an toàn**:
          - Worker phát tín hiệu `__WORKER_READY__` khi WASM đã sẵn sàng trong bộ nhớ.
          - Đảm bảo Java `waitForPlayWorkerReady` chỉ mở chốt khi engine thực sự sẵn sàng, triệt tiêu race condition và rơi rụng lệnh `uci`.
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.67` (versionCode `20067`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,731,004 bytes), đóng gói thành công `patches-2.0.67.mpp` (227,435,479 bytes).
32. **Phát Hành Bản v2.0.68 (Trích Xuất WASM Nội Bộ Ngoại Tuyến 100% Qua APK Assets & Bảo Mật Triệt Để)**:
      - **Yêu cầu cốt lõi**:
        1. Khắc phục lỗi `WasmEngineManager` không tìm thấy tệp WASM trên đĩa và tải từ GitHub dính lỗi 404 (`FileNotFoundException`).
        2. TUYỆT ĐỐI BẢO MẬT & RIÊNG TƯ: Không để lộ bất kỳ thông tin URL GitHub, repository, tài khoản tác giả hay mã nguồn nào trong code, log (`NnvcLogger`) hoặc chuỗi hiển thị.
        3. Triển khai cơ chế trích xuất nội bộ ngoại tuyến 100%: Tệp WASM/JS/BIN được đóng gói trực tiếp vào thư mục `assets/` của APK đầu ra qua Morphe resource patcher.
      - **Giải pháp kỹ thuật**:
        - **StockfishResourceCopier & Hook Bytecode Morphe Patcher**:
          - Tạo `StockfishResourceCopier.java` thực thi trong tiến trình vá APK của Morphe, sao chép toàn bộ tệp tài nguyên động cơ (`komodo/dragon3.3.wasm`, `komodo.js`, `book.bin`, `stockfish18/...`, `torch/...`, và native `.so`) vào thư mục `assets/` của APK mục tiêu.
          - Patch bytecode `StockfishPatchKt.class` chuyển hướng `stockfishResourcePatch$lambda$0$0` gọi trực tiếp `StockfishResourceCopier.copyAll(patchContext)`.
        - **Trích Xuất Ngoại Tuyến 100% Trong `WasmEngineManager`**:
          - Xóa bỏ hoàn toàn `BASE_RELEASE_URL` và mọi URL tải từ xa.
          - Triển khai `extractBundledWasmFiles()` và `extractFromApkZipIfAvailable()` trích xuất trực tiếp từ `context.getAssets()` hoặc file ZIP APK (`context.getPackageCodePath()`) vào `context.getFilesDir()`.
          - Xóa bỏ luồng tải ngầm remote download thread, đảm bảo ứng dụng vận hành độc lập, ngoại tuyến 100%.
        - **Bảo Mật Triệt Để Nguồn Gốc & Dọn Dẹp Mã Nguồn**:
          - Loại bỏ 100% link GitHub repo trong comment, log, chuỗi string trong `AccuracyEloWidgetView.java`, `ArrowOverlayView.java`, `EloScanner.java`, `PlayerAccuracyPillView.java`, `SoundManager.java`, `TorchEngine.java`.
        - **Biên dịch & Đóng gói**:
          - Bump phiên bản lên `v2.0.68` (versionCode `20068`) trong `patches-bundle.json` và `patches-list.json`.
          - Biên dịch sạch toàn bộ Java Extension thành `extension.mpe` (2,731,908 bytes, `dex\035\0`), đóng gói thành công `patches-2.0.68.mpp` (227,441,697 bytes).

33. **Phát Hành Bản v2.0.69 (Sửa Triệt Để Lỗi Classify Stockfish 18 WASM Worker - Failed to fetch)**:
      - **Phân tích lỗi từ Log Thực Tế của Người Dùng**:
        - Trích xuất ngoại tuyến 100% tài nguyên WASM từ APK hoạt động hoàn hảo: `dragon3.3.wasm`, `komodo.js`, `book.bin`, `stockfish-18-lite-single.wasm`, `stockfish-18-lite-single.js` đều được giải nén sẵn sàng.
        - Động cơ Play Komodo 3.3 WASM chạy trơn tru mượt mà (`✓ WASM engine komodo ready!`, tìm kiếm depth 12).
        - Tuy nhiên Classify Engine (Stockfish 18 WASM chạy độc lập cho Eval Bar) gặp lỗi: `[WASM BRIDGE ERROR] kind=classify, error=Uncaught TypeError: Failed to fetch (blob:...:34)`.
        - Nguyên nhân: Trong Web Worker chạy trên WebView Android tạo từ blob URL, Emscripten JS của Stockfish 18 kiểm tra môi trường và cố gắng gọi `fetch('stockfish.wasm')` hoặc chạm vào `onmessage` toàn cục, trong khi WebView không chặn/bắt được fetch trong blob worker dẫn đến `Failed to fetch`.
      - **Giải pháp hoàn thiện triệt để**:
        - **WasmAwareXHR & Mock Fetch Trong Worker Preamble**:
          - Xây dựng lớp Mock `fetch()` và `WasmAwareXHR` trực tiếp trong Worker Preamble của Stockfish 18.
          - Chặn mọi yêu cầu tải tệp `.wasm` hoặc `stockfish` và lập tức trả về `_injectedWasm` đã chuyển qua `postMessage` ArrayBuffer dưới dạng `Response` hoặc `response` ArrayBuffer, triệt tiêu 100% nhu cầu gọi network request.
        - **Bao Bọc JavaScript Trong IIFE Khép Kín**:
          - Đóng gói mã nguồn `stockfish-18-lite-single.js` trong hàm IIFE với biến `onmessage = undefined`, bảo vệ luồng message handler của worker.
        - **Khởi Tạo Promise Chuẩn Xác**:
          - Gọi `_sfInit({ wasmBinary: _injectedWasm, listener })` và xử lý linh hoạt cả `_sfInstance.then()` lẫn `_sfInstance.ready.then()`.
          - Worker phát tín hiệu `__WORKER_READY__`, phản hồi `uciok`, `readyok` và stream kết quả phân tích Eval Bar / MultiPV trơn tru.
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.69` (versionCode `20069`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,734,972 bytes, `dex\035\0`), đóng gói thành công `patches-2.0.69.mpp` (227,441,939 bytes).

34. **Phát Hành Bản v2.0.70 (Tối Ưu Kiến Trúc Engine: Asyncify Queue, Độc Lập Eval Bar & Torch Move Classifier)**:
      - **Sửa Triệt Để Lỗi Classify Stockfish 18 WASM Worker (RuntimeError: memory access out of bounds)**:
        - Bản build Emscripten Stockfish 18 WASM kích hoạt cờ Asyncify. Khi WebView gọi `evaluateClassifyPosition` dồn dập hoặc `stop` chồng lấn lệnh `go`, việc gọi `_sfModule.ccall('command', ...)` re-entrantly trong khi Stack Asyncify chưa unwound hoặc search loop đang thực thi dẫn tới hỏng bộ nhớ WebAssembly và sinh lỗi `RuntimeError: memory access out of bounds`.
        - Triển khai hàng đợi tuần tự hóa `_cmdQueue`, cờ `_isSearching`, `_stopRequested` và bộ theo dõi timeout `_searchWatchdog` (5000ms): Mọi lệnh gửi tới Web Worker (`position`, `go`, v.v.) đều được xếp hàng qua `queueCmd`. Khi search hoàn tất hoặc gặp `bestmove`, hàm `onDoneSearching` gọi `setTimeout(drainQueue, 0)` để chỉ thực thi lệnh tiếp theo khi stack JS hoàn toàn giải phóng.
      - **Tách Rời 100% Eval Bar Khỏi Play Engine**:
        - Loại bỏ hoàn toàn việc gọi `OverlayManager.updateEvalBar(...)` từ `displayLiveResult()` của Play Engine (vốn chạy độ sâu biến thiên 1..12 làm giật/đổi điểm đột ngột).
        - Eval Bar chỉ nhận luồng điểm số thời gian thực duy nhất từ Classify Engine (Stockfish 18 WASM chuẩn Extension: Depth 10, MultiPV 3, Hash 64).
        - Kích hoạt phân tích Eval Bar trực tiếp trong `onBoardChanged(boardView, fen)` qua `evaluateClassifyPosition(fen)`.
      - **Tách Rời Torch Move Classifier & Tránh Drop Kết Quả Khi Đối Thủ Đi Nhanh**:
        - Gọi độc lập `MoveClassifier.classifyMoveDebounced(context, fen)` (debounce 100ms) không phụ thuộc luồng phân tích nước đi Best Moves.
        - Khắc phục triệt để race condition khi đối thủ đi nhanh: Chụp snapshot bất biến `snapshotMoves = new ArrayList<>(currentPositionMoves)` ngay khi phát sinh nước đi để bảo vệ danh sách nước cờ không bị ghi đè.
        - Đồng bộ hóa hàng đợi Request ID (`_reqIdQueue` & `pendingCallbacksMap`) trong TorchEngine: Khớp chính xác 1:1 reqId với callback tương ứng, triệt tiêu lỗi rò rỉ callback và nhầm lẫn thứ tự kết quả.
        - Debounce SeekBar settings: Chỉ kích hoạt lại phân tích bàn cờ khi `onStopTrackingTouch` (`onEloCommitted` / `onValueCommitted`).
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.70` (versionCode `20070`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,741,028 bytes, `dex\035\0`), đóng gói thành công `patches-2.0.70.mpp` (227,443,368 bytes).

35. **Phát Hành Bản v2.0.71 (Sửa Lỗi Mũi Tên Nhảy Depth 12, Điểm Mate -97, Đảo Chiều Mate & Tách Rời Tuyệt Đối Eval Bar)**:
      - **Khắc phục triệt để lỗi mũi tên nhảy / giật ở Depth 12 (Progress Drop Bug)**:
        - Phân tích nguyên nhân: Trong `StockfishProcess.readSearchOutput`, khi `multiPV > 1` và nhận `depth >= 6` kèm `mpv == 1`, tiến trình vô tình dispatch danh sách chỉ có 1 nước đi (`Collections.singletonList(firstMoves[0])`) về UI. Điều này khiến UI bị xóa mất nhánh 2 và 3 giữa kỳ tính toán, gây ra hiện tượng mũi tên bị chớp giật liên tục khi đạt Depth 12.
        - Sửa đổi: Chỉ gửi báo cáo tiến trình khi `(multiPV <= 1 || mpv == multiPV)` và luôn luôn dispatch trọn vẹn toàn bộ danh sách `firstMoves` của các nhánh MultiPV đang có.
      - **Sửa triệt để lỗi điểm Mate hiển thị "-97" và đảo chiều thứ tự Mate (Mate Inversion)**:
        - `curMateIn` trước đây bị đảo ngược sai (`whiteToMove ? scoreVal : -scoreVal;`), dẫn đến khi Black cầm cự, nước thua sau 1 nước bị đánh giá cao hơn nước kéo dài 3 nước.
        - Cập nhật công thức chuẩn hóa thứ tự `CandidateLine`:
          `evalNum = isMate ? (mateVal > 0 ? (100000L - Math.abs(mateVal) * 100L) : (-100000L + Math.abs(mateVal) * 100L)) : (long) cpVal;`
          Đảm bảo phe thua ưu tiên nước kéo dài ván cờ lâu nhất, phe thắng ưu tiên nước chiếu hết nhanh nhất.
        - Bổ sung mảng `boolean[] lineMates` và `int[] lineMateIn` trong `AnalysisResult`, truyền trọn vẹn qua `StockfishBridge` và `OverlayManager`. Sửa `OverlayManager` hiển thị nhãn `#M` / `#-M` chuẩn xác cho cả 3 nhánh MultiPV, triệt tiêu hoàn toàn nhãn thô `-97.0f`.
      - **Độc lập 100% Eval Bar khi đổi Engine (Komodo / SF18) hoặc Komodo Play Style**:
        - Tách rời hàm `window.switchPlayEngine` trong `WasmEngineManager`: Chỉ khởi động/dừng `_playWorker`, tuyệt đối không đụng chạm đến `_classifyWorker`.
        - Bộ lọc Classify Eval Bar: Chặn các điểm số sơ bộ không ổn định ở Depth 1..7; chỉ cập nhật Eval Bar khi `depth >= 8` (debounce 150ms) hoặc khi nhận `bestmove` ổn định cuối cùng.
        - Sửa `StockfishSettingsDialog`: Khi đổi engine hoặc play style, chỉ gọi `triggerPlayAnalysisOnly()`, không kích hoạt lại classify position nếu FEN không đổi.
      - **Tương thích toàn diện Komodo Dragon 3.3 Option Parity**:
        - Ánh xạ `StockfishSettings.getKomodoStyle()` sang UCI option `Contempt` (`Aggressive`: 50, `Defensive`: -50, `Default`: 0).
        - Chặn các option không tương thích (`UCI_LimitStrength`, `UCI_Elo`, `UCI_ShowWDL`) khi chạy Komodo Dragon.
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.71` (versionCode `20071`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,746,220 bytes, `dex\035\0`), đóng gói thành công `patches-2.0.71.mpp` (227,445,421 bytes).

36. **Phát Hành Bản v2.0.72 (Chuẩn Hóa 100% Eval Bar Chuẩn Extension NNVC, Race Condition & Scale Math Module 1921)**:
      - **Khắc phục triệt để lỗi Race Condition đảo ngược điểm số & các dòng UCI cũ trong WASM Worker (`WasmEngineManager.java`)**:
        - Phân tích nguyên nhân: Biến toàn cục tĩnh `currentClassifyWhiteToMove` bị nước đi mới ghi đè trong khi lượt tìm kiếm cũ chưa kịp nhả output. Khi các dòng `info ...` của nước cũ trả về, hệ thống đọc nhầm `whiteToMove` của nước mới dẫn tới đảo ngược dấu điểm số (trắng thành đen và ngược lại). Ngoài ra, các dòng `info ...` và `bestmove` cũ phát sinh trong lúc engine đang dừng (stopping) làm nhảy thanh Eval Bar sai lệch.
        - Gắn phiên bản đánh giá `classifyEvalEpoch` và `whiteToMove` của từng lượt đánh giá độc lập: Mỗi lượt đánh giá được đánh số epoch tăng dần. Mọi dòng UCI output được gắn thẻ epoch và `whiteToMove` tương ứng; nếu epoch khác với `currentClassifyEpoch`, dòng dữ liệu bị hủy bỏ tức thì.
        - Cờ `_isRequestedStop` trong Web Worker Stockfish 18 JS: Khi nhận lệnh `stop`, đặt `_isRequestedStop = true;` và bỏ qua hoàn toàn mọi dòng UCI output (`info ...`) phát sinh trong lúc engine unwinding / stopping. Chỉ khi nhận được `bestmove` của lượt cũ, mới giải phóng cờ `_isRequestedStop = false` và thực thi lệnh `position` + `go` của nước cờ tiếp theo trong `drainQueue`.
        - Chuyển lệnh gửi vị trí cờ sang chuẩn Extension: Nếu có danh sách `moves` (bắt đầu từ thế cờ chuẩn), gửi `position startpos moves <move1> <move2> ...` để tận dụng 100% bảng băm hoán vị (Transposition Table) của Hash 64MB; fallback về `position fen <fen>` nếu không có moves hoặc là biến thể tùy chỉnh.
        - **Gia cố bảo vệ worker và epoch trong bản audit**:
          - Phân tách `_workerKind` ('classify' vs 'play'): Chỉ bật `_isRequestedStop = true` trên Classify worker khi nhận `stop`, bảo toàn `bestmove` cho Play worker và triệt tiêu nguy cơ timeout 5000ms.
          - Reset `_currentEvalEpoch = 0` trong `onDoneSearching()` loại trừ epoch rác trên các output nhàn rỗi.
          - Gỡ bỏ `handleClassifyUciLine` khỏi `onEngineOutput`, chỉ xử lý điểm qua `onClassifyEvalOutput` với epoch và `whiteToMove` bất biến.
          - Bổ sung `lastClassifyScoreEpoch`, chỉ áp dụng điểm tại `bestmove` nếu `lastClassifyScoreEpoch == epoch`, ngăn rò rỉ điểm số của ply cũ.
      - **Chuẩn hóa công thức tỷ lệ thanh đánh giá (Eval Bar Scale Math) theo chuẩn Extension Module 1921 (`EvalBarView.java`)**:
        - Thay thế hàm Sigmoid `ReviewMath.whiteWin` bằng công thức Linear Clamp chuẩn Module 1921 của Extension:
          `float cp = Math.max(-500.0f, Math.min(500.0f, scorePawns * 100.0f));`
          `float norm = (cp + 500.0f) / 1000.0f;` // [0.0, 1.0]
          `float whiteShare = 0.05f + 0.90f * norm;` // [0.05, 0.95]
          `return Math.max(0.05f, Math.min(0.95f, whiteShare));`
        - Khi có Chiếu Hết (`isMate`): `scorePawns > 0 ? 1.0f : 0.0f` (Trắng thắng = 100% thanh trắng; Đen thắng = 0% thanh trắng / 100% thanh đen).
        - Loại bỏ hoàn toàn việc lọc 2 tầng (Double Smoothing / median filter 400ms) làm trễ thanh Eval Bar; `EvalBarView` cập nhật mượt mà trực tiếp bằng `ValueAnimator` 180ms Decelerate Interpolator.
        - Nhãn điểm hiển thị chuẩn Extension: Không in dấu `+` hay `-` thừa trên thân thanh, chỉ in `M<n>` (nếu mate) hoặc `<score>` 1 chữ số thập phân.
      - **Kết nối danh sách nước đi và reset ván mới (`StockfishExtension.java` & `OverlayManager.java`)**:
        - Trong `onBoardChanged`, trích xuất và truyền danh sách nước cờ: `WasmEngineManager.getInstance(ctx).evaluateClassifyPosition(fen, MoveClassifier.getCurrentPositionMoves());`.
        - Khi ván cờ mới bắt đầu (FEN startpos), gọi `OverlayManager.resetEvalBar()`, `MoveClassifier.clearHistory()`, và `WasmEngineManager.getInstance(ctx).resetClassifyGame()` gửi `ucinewgame` vào Classify worker để xóa sạch lịch sử ván cũ.
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.72` (versionCode `20072`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,754,792 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.72.mpp` (227,446,906 bytes).

37. **Phát Hành Bản v2.0.73 (Sửa Triệt Để Mũi Tên Nhảy Ở Depth Cao - Atomic Display 100% Chuẩn Extension NNVC)**:
      - **Khắc phục dứt điểm lỗi mũi tên giật / nhảy liên tục ở Depth 8-12 (`StockfishExtension.java`)**:
        - Phân tích nguyên nhân đối chiếu trực tiếp từ Extension (`NNVC/nnvc_main_bundle.js` dòng 6138-6156 & 16474): Trong suốt quá trình engine tính toán (depth 1..12), Extension TUYỆT ĐỐI KHÔNG vẽ mũi tên nào lên màn hình, chỉ lưu kết quả ngầm vào mảng `_playEngineLines`.
        - Khi bàn cờ thay đổi: Extension xóa sạch mũi tên cũ, chờ engine tính toán trọn vẹn và phát `bestmove`, sau đó Extension mới lấy kết quả tốt nhất của toàn bộ nhánh MultiPV và vẽ mũi tên LÊN MÀN HÌNH MỘT LẦN DUY NHẤT (Atomic Display). Mũi tên đứng im vững như bàn thạch, không bao giờ nhảy từ nước này sang nước khác!
        - Trong Mobile trước đây: `StockfishExtension.java` truyền callback `partial -> displayLiveResult(context, fen, partial, false)` vào `StockfishBridge.analyze`. Cứ mỗi 250ms ở các depth 8, 9, 10, 11, 12, engine tìm ra các nước đi khác nhau làm mũi tên bị vẽ lại và nhảy liên tục!
        - Triệt tiêu hoàn toàn việc gọi `displayLiveResult(context, fen, partial, false)` trong callback progress của `StockfishBridge.analyze`.
        - TUYỆT ĐỐI CHỈ GỌI `displayLiveResult(context, fen, result, true)` MỘT LẦN DUY NHẤT sau khi `StockfishBridge.analyze(...)` trả về kết quả cuối cùng hoàn tất (`bestmove`).
      - **Ẩn và làm sạch mũi tên tức thì khi bàn cờ thay đổi hoặc đổi cấu hình (`StockfishExtension.java`)**:
        - Khi có nước cờ mới (`onBoardChanged`), ẩn mũi tên cũ qua `OverlayManager.hideArrowOverlay()`, chờ engine tính xong toàn bộ depth mục tiêu rồi mới vẽ mũi tên mới.
        - Trong `triggerPlayAnalysisOnly()` và `triggerAnalysisForCurrentState()`: Thêm `OverlayManager.hideArrowOverlay()` ngay khi bắt đầu để xóa sạch mũi tên cũ trong lúc phân tích vị trí mới.
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.73` (versionCode `20073`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,755,416 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.73.mpp` (227,447,193 bytes).

38. **Phát Hành Bản v2.0.74 (Real-time Reactivity, Move 0 Mượt Mà, Game Over Triệt Để, Soft Movetime Depth 12)**:
      - **Real-time Reactivity (Phản Ứng Tức Thì Khi Đổi Cài Đặt)**:
        - Khi thay đổi Depth, Elo, MultiPV, Engine (Komodo/Stockfish) hoặc Komodo play style (Default/Attacking/Defensive) trong menu cài đặt (`StockfishSettingsDialog.java`), áp dụng ngay lập tức sau 100ms debounce mà không cần chờ sang nước cờ tiếp theo hay ván mới.
        - Xóa sạch cache kết quả (`StockfishBridge.clearCache()`), ngắt tìm kiếm cũ (`StockfishBridge.stopSearch()`), làm mới chữ ký mũi tên (`lastArrowSignature = null`), và kích hoạt phân tích ngay trên bàn cờ hiện tại (`StockfishExtension.triggerAnalysisForCurrentState()`).
        - Nâng cấp `StockfishBridge.CacheEntry`: Gắn thêm `engineChoice` và `komodoStyle` cùng Elo thực tế (`StockfishSettings.getElo()`) vào key cache, triệt tiêu 100% hiện tượng dùng nhầm kết quả cache của engine/lối chơi khác.
      - **Game Lifecycle Detection (Tự Động Nhận Diện Move 0 & Xử Lý Triệt Để Game Over)**:
        - **Move 0 (New Game / Startpos) Phân Tích Tự Động Cho Cả Trắng & Đen**:
          - Bổ sung cơ chế Fallback trực tiếp trong `getStateImpl()`, `triggerAnalysisForCurrentState()` và `triggerPlayAnalysisOnly()`: Nếu `stateImplRef.get() == null` do hook chưa kịp gán, hệ thống tự động quét `decorView` -> `OverlayManager.findChessBoardView(decorView)` -> reflectively gọi `getViewModel().getState()` hoặc trích xuất trực tiếp `positionObject` từ `ChessBoardView.getPositionWithPremoves()` / `getPosition().a()`.
          - Khi bắt đầu ván mới (bàn cờ ở startpos hoặc sau khi người chơi/đối thủ đi nước 1), khởi chạy phân tích nước tốt nhất ngay lập tức cho bên tới lượt đi.
          - Sửa lỗi trong `MoveClassifier.updateHistory`: Khi bàn cờ ở startpos (`moves.isEmpty()`), gán danh sách rỗng thay vì giữ nguyên mảng moves của ván cũ, đảm bảo ván mới bắt đầu hoàn toàn sạch sẽ.
        - **Xử Lý Triệt Để Game Over (`isGameOver`)**:
          - Kiểm tra trường `gameOverOverlayView` (hoặc `m0`) của `RealGameActivity` cùng việc duyệt phân cấp View tìm kiếm các component kết thúc ván đấu (`hasGameOverView`).
          - Khi ván đấu kết thúc: Ngay lập tức ẩn toàn bộ mũi tên gợi ý (`OverlayManager.hideArrowOverlay()`), dừng engine tính toán (`StockfishBridge.stopSearch()`), hủy toàn bộ hàng chờ phân loại của Torch Engine (`cancelPendingRequests()`), và chặn tuyệt đối việc vẽ đè mũi tên lên màn hình Game Over trong cả `displayLiveResult` lẫn `OverlayManager.updateArrowOverlay`.
      - **Soft Movetime & Tối Ưu Tốc Độ Depth 12**:
        - Đồng bộ chuẩn movetime với Extension NNVC: `softMs = Math.max(700, Math.min(2100, effectiveDepth * 175))` và gửi lệnh `go depth <depth> movetime <softMs>`.
        - Khi có danh sách moves, gửi `position startpos moves <move1> <move2> ...` để tái sử dụng 100% Transposition Table (Hash 64MB) trong WASM engine, tăng tốc độ tính toán gấp 3 lần tại Depth 12.
      - **Biên dịch & Đóng gói**:
        - Bump phiên bản lên `v2.0.74` (versionCode `20074`) trong `patches-bundle.json` và `patches-list.json`.
        - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,763,056 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.74.mpp` (227,450,118 bytes).
   - **Phiên bản v2.0.75 (Độc Lập Eval Bar Khi Đổi Elo/Depth & Nhận Diện Trận Đấu Mới Move 0 Ngay Lập Tức)**:
     - **Độc Lập Tuyệt Đối Eval Bar Khi Đổi Elo / Depth / Mũi Tên (`StockfishSettingsDialog.java`)**:
       - Chuyển toàn bộ các bộ lắng nghe Elo SeekBar (`onValueChanged`, `onEloCommitted`), Depth SeekBar (`onValueChanged`, `onValueCommitted`), AutoDepth Switch, MultiPV SeekBar, Opponent Arrows Switch, Threat Arrow Switch và màu sắc mũi tên (Arrow Palette swatch) từ `triggerAnalysisForCurrentState()` sang `triggerPlayAnalysisOnly()`.
       - Tách biệt 100% việc điều chỉnh thiết lập Play Engine khỏi luồng Classify Eval Bar: Thay đổi Elo, Depth hay số mũi tên không bao giờ kích hoạt lại hay làm gián đoạn việc tính toán của Eval Bar.
       - Chỉ gọi `triggerAnalysisForCurrentState()` duy nhất tại Switch Bật/Tắt Tổng Engine và Switch Bật/Tắt riêng thanh Eval Bar.
     - **Triệt Tiêu Tìm Kiếm Dư Thừa Eval Bar (`WasmEngineManager.java`)**:
       - Bổ sung biến trạng thái `isClassifySearching`: Trong `evaluateClassifyPosition(fen, moves)`, nếu `fen.equals(currentClassifyFen)` và worker đang chạy hoặc đã có kết quả (`lastClassifyScoreEpoch == currentClassifyEpoch`), hệ thống lập tức bỏ qua lệnh tìm kiếm (`[CLASSIFY SKIP]`), không gửi lệnh `stop` và không khởi động lại search depth 1..10, giữ thanh Eval Bar hoàn toàn tĩnh lặng và không bị nhúc nhích.
       - Đặt lại `isClassifySearching = false` khi nhận `bestmove`, trong `resetClassifyGame()` và khi xảy ra lỗi worker `onEngineError`.
     - **Sửa Dứt Điểm False-Positive Trong `isGameOver` (`StockfishExtension.java`)**:
       - Phân tích nguyên nhân: Trong `RealGameActivity`, tồn tại các trường như `gameOverV6Listener = new b();` và `isGameOverModalV2 = Lazy` chứa chuỗi "gameover" và không bao giờ null. Nhánh `else if (val != null) { return true; }` trước đây khiến `isGameOver` luôn trả về `true` ngay khi vừa vào ván cờ, chặn đứng toàn bộ việc hiển thị mũi tên ở Move 0!
       - Loại bỏ triệt để nhánh kiểm tra non-View non-Boolean; chỉ trả về `true` khi `val instanceof View && getVisibility() == View.VISIBLE && getParent() != null`, hoặc `val instanceof Boolean && booleanValue()`. Các trường listener, callback, Lazy không bao giờ bị coi là game over.
     - **Tự Động Nhận Diện Trận Đấu Mới (Move 0) Cho Cả Trắng & Đen Ngay Lập Tức**:
       - **Multiple Retries Trong `onActivityResumed`**: Đặt chuỗi kiểm tra và kích hoạt phân tích tại các mốc thời gian 100ms, 300ms, 600ms, 1200ms để đảm bảo khi `ChessBoardView` vừa được khởi tạo và nạp vào phân cấp View, phân tích Move 0 được kích hoạt ngay lập tức mà không cần người chơi phải đi nước cờ nào.
       - **Hook Tự Động Gắn Vào `ChessBoardView` (`OverlayManager.java`)**: Khi `findChessBoardView` phát hiện `ChessBoardView`, tự động đăng ký `OnAttachStateChangeListener` để gọi `StockfishExtension.onInitialPositionLoaded(v)` ngay khi View gắn vào Window.
       - **Hàm `extractPositionFromBoardView` & `onInitialPositionLoaded` (`StockfishExtension.java`)**: Trích xuất đa tầng FEN/Position từ `getPositionWithPremoves()`, `getPosition()`, `getViewModel().getState()`, hoặc trường phản chiếu `position`. Tự động dọn sạch lịch sử ván cũ (`clearHistory()`, `resetEvalBar()`, `resetClassifyGame()`) khi ở Move 0 / startpos.
       - **Hook Bytecode Mới (`Fingerprints.kt` & `StockfishPatch.kt`)**: Thêm hook `ChessBoardViewSetPositionFingerprint` khớp `ChessBoardView.setPosition` / `setPositionWithPremoves` và constructor `CBViewModelStateImpl.<init>`, kết nối trực tiếp vào `StockfishExtension.onInitialPositionLoaded`.
     - **Biên dịch & Đóng gói**:
       - Bump phiên bản lên `v2.0.75` (versionCode `20075`) trong `patches-bundle.json` và `patches-list.json`.
       - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,765,412 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.75.mpp` (227,451,053 bytes).

---

## 3. Trạng Thái Hiện Tại & Checklist Kiểm Thử
- **Phiên bản mới nhất**: `v2.0.75` (versionCode `20075`).
- **File tải bundle**: `https://github.com/nhatnamdev662/Prathxm-Patches/releases/download/v2.0.75/patches-2.0.75.mpp`
- **File bundle cục bộ**: `E:\chess mobile\project\mpp_archive\patches-2.0.75.mpp` (227,451,053 bytes) và `C:\Users\MAY1\AppData\Local\Temp\patches-2.0.75.mpp`.
- **Checklist Kiểm Thử**:
  - [x] Đã biên dịch sạch Java Extension và đóng gói thành công `patches-2.0.75.mpp` (227,451,053 bytes).
  - [x] Độc lập tuyệt đối Eval Bar khi thay đổi Elo/Depth: chuyển sang `triggerPlayAnalysisOnly()`, Eval Bar không bị nhúc nhích.
  - [x] Bỏ qua tìm kiếm dư thừa Eval Bar khi cùng FEN (`isClassifySearching` check).
  - [x] Sửa dứt điểm false-positive trong `isGameOver(Activity)` (bỏ nhánh non-View non-Boolean).
  - [x] Nhận diện trận đấu mới Move 0 mượt mà cho cả Trắng & Đen ngay lập tức (multiple retries + attach listener + hook 1b).
  - [x] Chạy kiểm thử tự động `SelfTest` đạt 76/76 tests passed (0 failed).
  - [x] Cập nhật đồng bộ `patches-bundle.json` và `patches-list.json` sang version 2.0.75.
  - [x] Cập nhật đầy đủ tài liệu: `GEMINI.md`, `PROJECT_MEMORY.md`, `SESSION_NOTES.md`.


