quy tắc làm việc

mỗi task xong phải cập nhật đầy đủ file md

làm việc nghiêm túc đây là projet nghiêm trọng

khi không có đầy đủ dữ liệu để làm hoặc cải thiện tính năng thì phân tích kĩ apk để hook coi có làm được gì không

---

## Nhật ký cập nhật phiên bản
### v2.0.93 (2026-10-13)
- **Cập Nhật Phiên Bản Mới Tránh Cache (Xóa Triệt Để Cache Cũ, Danh Hiệu Real-Time & Extension Style Selector)**:
  - **Bump Phiên Bản Chính Thức v2.0.93 (versionCode 20093)**: Đẩy phiên bản lên `2.0.93` nhằm kích hoạt Morphe Patcher và Chess.com tải bản mới nhất, tránh lỗi nuốt cache cũ của v2.0.92.
  - **Giao Diện Chọn Danh Hiệu Phong Cách Extension Cực Gọn & Đẹp (`StockfishSettingsDialog.java`)**:
    - Thay thế cụm 6 nút chip to chiếm 2 dòng bằng 1 hàng duy nhất: `Chọn Danh Hiệu` + selector `[ GM ▾ ]` viền đỏ cờ vua.
    - Popup Modal 10 danh hiệu chuẩn FIDE/Chess.com với preview badge đỏ rực rỡ và dấu tick xanh `✓`.
  - **Real-Time 100% & Offline Tuyệt Đối Không Tải File Mạng (`CustomTitlesExtension.java`)**:
    - Loại bỏ hoàn toàn link tải JSON từ GitHub, xử lý 100% local trong RAM/SharedPreferences.
    - Tự động invalidate DecorView và khi đóng settings tự động `activity.recreate()` để áp dụng ngay lập tức danh hiệu mới trên toàn bộ giao diện app.
  - **Dọn Dẹp Dead Code**: Xóa bỏ hoàn toàn hàm dư thừa `getAnalysisPermission` trong `StockfishExtension.java`.
  - **Xóa Bỏ Triệt Để Icon Kim Cương**: Loại bỏ 100% icon kim cương trên toàn bộ ứng dụng.
  - Bộ kiểm thử tự động `SelfTest`: 167/167 test cases vượt qua thành công (100% passed).
  - Biên dịch sạch 45 file Java Extension thành `extension.mpe` (2,803,132 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (94,860 bytes), đóng gói thành công `patches-2.0.93.mpp` (227,467,505 bytes).

### v2.0.92 (2026-10-13)
- **Ra Mắt Danh Hiệu Người Dùng Native (Fake Title Real-Time & Extension Style Selector) & Xóa Bỏ Triệt Để Icon Kim Cương**:
  - **Giao Diện Chọn Danh Hiệu Phong Cách Extension Cực Gọn & Đẹp (`StockfishSettingsDialog.java`)**:
    - **Nút Selector Tinh Tế**: Thay thế hoàn toàn cụm 6 nút chip to chiếm 2 dòng cũ bằng 1 hàng ngang duy nhất: `Chọn Danh Hiệu` kèm nút Selector `[ GM ▾ ]` phong cách luxury bo góc viền đỏ cờ vua sang trọng.
    - **Hộp Thoại Chọn Danh Hiệu Chuyên Nghiệp (Title Picker Modal)**: Khi bấm vào selector, hiển thị danh sách 10 danh hiệu chuẩn FIDE/Chess.com (GM, IM, FM, CM, NM, WGM, WIM, WFM, WCM, WNM) với badge đỏ preview rực rỡ, tên đầy đủ và dấu tick xanh `✓` báo hiệu danh hiệu đang chọn.
  - **Áp Dụng Tức Thì Real-Time & 100% Offline Không Tải File Mạng (`CustomTitlesExtension.java`, `StockfishSettingsDialog.java`)**:
    - **Triệt tiêu tải file mạng**: Xóa bỏ hoàn toàn URL fetch JSON từ GitHub trong `CustomTitlesExtension.java`, mọi xử lý chuyển sang 100% local trong RAM và SharedPreferences với 0ms độ trễ.
    - **Real-Time UI Recreate**: Khi người dùng đổi danh hiệu hoặc bật/tắt trong cài đặt, hệ thống lập tức invalidate DecorView và khi đóng dialog tự động trigger `activity.recreate()` để áp dụng danh hiệu mới trên toàn bộ giao diện app ngay lập tức (không cần thoát app hay load lại trang).
  - **Dọn Dẹp Triệt Để Dead Code (`StockfishExtension.java`)**:
    - Xóa bỏ hoàn toàn hàm dư thừa `getAnalysisPermission` không còn được sử dụng, đảm bảo codebase sạch sẽ và không gây lỗi tiềm ẩn.
  - **Xóa Bỏ Triệt Để 100% Icon Kim Cương (Diamond Membership Badge Removal - `AdFreePatchKt`, `StockfishExtension.java`)**:
    - NOP triệt để các bytecode instructions hook `LoginDataGetPremiumStatusFingerprint` và `UserDataGetPremiumStatusFingerprint` trong `AdFreePatchKt.class`.
    - `StockfishExtension.getDiamondStatus()` trả về `null` và `getPremiumStatusObject(defaultValue)` trả về nguyên vẹn `defaultValue`.
    - Huy hiệu kim cương biến mất hoàn toàn trên toàn bộ ứng dụng (bàn cờ, chat, trang cá nhân, menu).
    - Bảo toàn 100% các tính năng Ad-Free, Bot Unlock, Analysis, Chat và Engine hoạt động ổn định.
  - Bộ kiểm thử tự động `SelfTest`: 167/167 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.92` (versionCode `20092`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 45 file Java Extension thành `extension.mpe` (2,803,132 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (94,860 bytes), đóng gói thành công `patches-2.0.92.mpp` (227,467,505 bytes).

### v2.0.91 (2026-10-13)
- **Nâng Cấp Mũi Tên Premove Hỗ Trợ Thế Cờ Chiếu Tướng Ép Nước (Only Move Premove Architecture)**:
  - **Nhận Diện Trạng Thái Chiếu Tướng & Đếm Nước Đi Hợp Lệ Siêu Tốc (`BoardUtil.java`)**:
    - Bổ sung `isSquareAttacked(char[] b, int sq, boolean byWhite)`: Kiểm tra một ô cờ có đang bị quân của đối phương (Tốt, Mã, Tượng, Xe, Hậu, Vua) kiểm soát hay không.
    - Bổ sung `isKingInCheck(char[] b, boolean white)`: Xác định chuẩn xác 100% Vua Trắng hoặc Đen có đang trong tình trạng bị chiếu tướng hay không.
    - Bổ sung `countLegalMoves(char[] b, boolean white, int maxCount)` và `isOnlyLegalMove(char[] b, boolean white)`: Sinh nước đi giả định và lọc nước đi hợp lệ với cơ chế short-circuiting siêu tốc (< 0.5ms), trả về `true` khi và chỉ khi bên bị chiếu có đúng 1 nước đi hợp lệ duy nhất để giải chiếu (Only Move).
  - **Tích Hợp 2 Tình Huống Only Move Vào Mũi Tên Premove Cam Neon (`StockfishExtension.java`)**:
    - **Tình huống 1 (Chiếu đối thủ & Đối thủ chỉ có 1 nước đi duy nhất)**: Người chơi vừa đi nước chiếu tướng và đối thủ chỉ có duy nhất 1 cách thoát chiếu (`BoardUtil.isKingInCheck(currentBoard, oppWhite) && BoardUtil.isOnlyLegalMove(currentBoard, oppWhite)`) -> Hệ thống lập tức hiển thị mũi tên Premove (màu cam hổ phách neon) chỉ nước đi tiếp theo của người chơi (`userReply`) ngay trong lượt đối thủ, giúp người chơi an tâm premove trước mà không lo đổi thế trận.
    - **Tình huống 2 (Đối thủ chuẩn bị chiếu & Người chơi chỉ có 1 nước thoát chiếu duy nhất)**: Đối thủ sắp đi nước `oppMove` đưa người chơi vào thế chiếu và người chơi chỉ có đúng 1 nước thoát chiếu duy nhất (`BoardUtil.isKingInCheck(boardAfterOpp, userIsWhiteSide) && BoardUtil.isOnlyLegalMove(boardAfterOpp, userIsWhiteSide)`) -> Hệ thống hiển thị mũi tên Premove chỉ nước thoát chiếu duy nhất đó ngay khi đối thủ còn đang suy nghĩ.
    - Duy trì bảo toàn 100% cơ chế Premove Recapture (ăn lại quân trong các tình huống trao đổi quân hiển nhiên).
  - Bộ kiểm thử tự động `SelfTest`: 161/161 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.91` (versionCode `20091`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 45 file Java Extension thành `extension.mpe` (2,794,228 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.91.mpp` (227,463,881 bytes).

### v2.0.90 (2026-10-13)
- **Cơ Chế Quét Move 0 Êm Dịu & Triệt Tiêu 100% Giật Chớp Tắt (Smooth Move 0 Scan & Anti-Flicker Architecture)**:
  - **Cơ Chế Quét Move 0 Êm Dịu (Smooth Move 0 Scan - `StockfishExtension.java`)**:
    - **Nguyên nhân gốc rễ**: Khi vừa vào trận đấu Live, bàn cờ cần thời gian ghép trận (matchmaking). Khi người chơi mở menu cài đặt và thay đổi Elo/Depth, hàm `triggerPlayAnalysisOnly()` được gọi và mũi tên lập tức xuất hiện mượt mà vì hàm này chỉ chạy tìm kiếm nước cờ (`scheduleAnalysis(fen, true)`) mà KHÔNG reset Eval Bar, KHÔNG reset Widget, KHÔNG dọn dẹp lịch sử nước đi. Ngược lại, hàm watchdog cũ gọi `tryLoadMove0` dẫn tới `onBoardChanged`, kích hoạt hàng loạt lệnh xóa và reset giao diện, gây hiện tượng chớp giật (flicker) liên tục.
    - **Khắc phục triệt để**:
      - Tái cấu trúc hàm `checkAndTriggerMove0Watchdog` thành cơ chế quét êm dịu (Smooth Move 0 Scan): Chỉ kích hoạt phân tích khi bàn cờ đã đo đạc kích thước thực tế trên màn hình (`BoardMetrics bm != null`).
      - Sử dụng cơ chế gọi phân tích độc lập (như khi đổi Elo/Depth) với `scheduleAnalysis(fen, true)` mà KHÔNG gọi `onBoardChanged`, bảo toàn 100% Eval Bar và Widget không bị reset hay nhấp nháy.
  - **Triệt Tiêu 100% Hiện Tượng Giật / Chớp Tắt (Anti-Flicker & Timing Guards - `StockfishExtension.java`, `OverlayManager.java`)**:
    - **Anti-Flicker Guard**: Khi trên màn hình ĐÃ CÓ mũi tên hiển thị (`OverlayManager.hasArrows() == true`), scanner lập tức dừng lại và không bao giờ gọi lại engine hay vẽ lại giao diện.
    - **Timing Guard**: Khi bàn cờ chưa layout xong (kích thước 0x0 hoặc đang hiện giao diện tìm đối thủ), scanner kiên nhẫn bỏ qua, không kích hoạt phân tích sớm gây lỗi.
    - **Layout Change Hook**: Trong `OverlayManager.java`, khi kích thước bàn cờ thay đổi, chuyển từ `tryLoadMove0` sang `checkAndTriggerMove0Watchdog` để đồng bộ cơ chế êm dịu.
    - **Bảo toàn hàng đợi retry**: Trong `onBoardChanged`, không hủy bỏ vô cớ hàng đợi retry `getMove0RetryHandler` trừ khi mũi tên đã thực sự được vẽ lên màn hình.
  - **Bổ Sung Fallback Toàn Diện Cho `triggerPlayAnalysisOnly`**:
    - Bổ sung fallback trích xuất thế cờ trực tiếp từ Activity (`extractPositionFromActivity(act)`) khi truy xuất qua view thất bại.
  - Bộ kiểm thử tự động `SelfTest`: 155/155 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.90` (versionCode `20090`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 45 file Java Extension thành `extension.mpe` (2,791,308 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.90.mpp` (227,460,728 bytes).

### v2.0.89 (2026-10-13)
- **Khắc Phục Toàn Diện & Triệt Để Move 0 Trận Đấu Trực Tuyến (Live Game Move 0 Arrow & UserSide Orientation Fix)**:
  - **Trích Xuất Thế Cờ Trực Tiếp Từ Activity, ViewModel & Starting FEN (`StockfishExtension.java`)**:
    - **Nguyên nhân gốc rễ**: Trong `RealGameActivity` (trận Live trực tuyến), `ChessBoardView.viewModel` là thuộc tính Kotlin `lateinit var viewModel` và ném `UninitializedPropertyAccessException` nếu truy xuất trước khi hoàn tất đăng ký sự kiện mạng (`b4.Q4` và `subscribeToEvents$1$1`). Hơn nữa, việc truy xuất `CBViewModel` trực tiếp không trích xuất được FEN nếu không bóc tách qua `getState() -> getPosition()`, khiến `resolveVariantPosition` trả về `null`.
    - **Khắc phục triệt để**:
      - Bổ sung `extractPositionFromViewModel(Object vm)` bóc tách qua chuỗi `vm.getState() -> state.getPosition()`.
      - Bổ sung kiểm tra chuỗi `position instanceof String` trực tiếp trong `extractFen(Object position)` để chấp nhận mọi chuỗi FEN.
      - Bổ sung fallback trích xuất `startingFen` từ `RealGameActivity.N6() -> RealGameUiSetup.getStartingFen()`: nếu rỗng tự động trả về Move 0 standard FEN `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1`.
      - Bổ sung fallback trong `getStateImpl()` truy xuất `RealGameActivity.X6() -> q().get() -> getState()` ngay cả khi `boardView.getViewModel()` chưa được gắn.
  - **Đảo Ngược Thứ Tự Ưu Tiên Nhận Diện Màu Quân (Priority 1 Inversion Fix - `isUserWhite - StockfishExtension.java`)**:
    - **Nguyên nhân gốc rễ**: Tại Move 0 trong trận Live, cờ lật bàn cờ `ChessBoardView.getFlipBoard()` ban đầu có giá trị mặc định là `false` (Trắng) trước khi hoàn tất layout giao diện. Khi prior attempt đặt kiểm tra `N6() -> getUserSide()` ở Priority 4 sau Priority 1 (`getFlipBoard()`), code kiểm tra `userSide` bị dead code hoàn toàn, khiến người chơi cầm Đen bị gán nhầm thành Trắng (`cachedUserIsWhite = true`) và mũi tên bị tính toán sai chiều hoặc không bao giờ vẽ lại!
    - **Khắc phục triệt để**: Đưa việc kiểm tra `RealGameActivity.N6() -> RealGameUiSetup.getUserSide()` và `RealGameActivity.X6() -> RealGameViewModel.userSide` lên **Priority 1** trong `isUserWhite`, nhận diện chuẩn xác 100% vai trò Trắng/Đen ngay từ lúc Activity vừa mở ra.
    - Cập nhật `OverlayManager.isBoardFlipped` kiểm tra `isUserWhite(stateImpl) == Boolean.FALSE` (Đen) để lật bàn cờ chuẩn xác ngay từ Move 0.
  - Bộ kiểm thử tự động `SelfTest`: 152/152 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.89` (versionCode `20089`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 45 file Java Extension thành `extension.mpe` (2,790,356 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.89.mpp` (227,460,169 bytes).

### v2.0.88 (2026-10-13)
- **Xử Lý Triệt Để Hàng Đợi Nạp Thế Cờ Move 0 Khi Khởi Động Engine & Bổ Sung Active Watchdog**:
  - **Bộ Nhớ Đệm Thế Cờ Chờ Xử Lý (Pending Position Caching - `StockfishExtension.java`)**:
    - **Nguyên nhân gốc rễ**: Khi vào ván đấu trước lúc Stockfish hoàn tất nạp nhị phân native (`!engineReady`), lệnh `onBoardChanged` bị trả về sớm (`return;`), khiến thế cờ Move 0 bị bỏ rơi hoàn toàn nếu lúc engine khởi tạo xong `ChessBoardView` chưa kịp gắn vào cây giao diện.
    - **Khắc phục triệt để**: Khi `!engineReady`, lưu trữ thế cờ vào `pendingStateImpl` và `pendingPositionObject`. Ngay khi engine sẵn sàng trên luồng nền, post lên Main Thread để tự động dispatch `onBoardChanged(pendingStateImpl, pendingPositionObject)`. Bổ sung cơ chế dọn dẹp an toàn khi khởi tạo engine thất bại hoặc ngoại lệ để không bao giờ treo vĩnh viễn watchdog.
  - **Move 0 Watchdog Chủ Động (Active Watchdog - `StockfishExtension.java`)**:
    - Tích hợp kiểm tra tự động định kỳ vào `gameOverWatchdogRunnable` (mỗi 300ms).
    - Nếu ván cờ đang diễn ra bình thường (`!isGameOver && !isReviewOrAnalysisActivity`) và bàn cờ đang ở Move 0 nhưng chưa xuất hiện mũi tên (`!OverlayManager.hasArrows()`) và chưa hoàn tất phân tích (`!isMove0AnalysisCompleted`), watchdog tự động kích hoạt `tryLoadMove0(act)` để khởi chạy engine. Có guard bảo vệ không ngắt quãng nếu engine đang trong tiến trình tính toán (`currentJob != null && !currentJob.isDone()`) và chủ động kích hoạt `ensureEngineReady()` nếu engine chưa chạy.
  - **Chuẩn Hóa Trạng Thái Hoàn Tất Move 0 (`isMove0AnalysisCompleted - StockfishExtension.java`, `OverlayManager.java`)**:
    - Ngăn chặn lỗi kết thúc sớm (premature completion): Cờ `isMove0AnalysisCompleted` chỉ được đánh dấu `true` khi mũi tên thực sự đã vẽ lên màn hình (`OverlayManager.updateArrowOverlay`), hoặc khi mũi tên bị chủ động ẩn do thiết lập người dùng (quân Đen với `isMySideOnly = true` hoặc tắt mũi tên).
    - Khi `BoardMetrics` chưa sẵn sàng (`bm == null`), `OverlayManager.updateArrowOverlay` tự động lên lịch retry kiên định sau 150ms qua `Handler(Looper.getMainLooper())` thay vì bỏ rơi.
    - Reset cờ `isMove0AnalysisCompleted = false` chuẩn xác tại mọi điểm chuyển giao trạng thái (bắt đầu ván mới, Game Over, `onActivityResumed`, `onActivityPaused`, `onActivityDestroyed`, khi bàn cờ sang Move 1+).
  - Bộ kiểm thử tự động `SelfTest`: 143/143 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.88` (versionCode `20088`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 45 file Java Extension thành `extension.mpe` (2,785,572 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.88.mpp` (227,458,175 bytes).

### v2.0.87 (2026-10-13)
- **Sửa Triệt Để Lỗi Không Hiện Mũi Tên Khi Vào Trận Đấu Mới (Move 0 Initial Arrow Fix)**:
  - **Dỡ Bỏ Deduplication Block Cứng Khi Chưa Có Mũi Tên (`StockfishExtension.java`)**:
    - **Nguyên nhân gốc rễ**: Khi vừa vào ván cờ mới, cờ `isMove0AnalysisScheduled` bị gán `true` ngay đợt gọi đầu tiên. Nếu `ChessBoardView` chưa kịp đo đạc xong kích thước (`bm == null`) hoặc chưa kịp vẽ, toàn bộ các lần gọi `tryLoadMove0()`, `onBoardChanged()`, và `onInitialPositionLoaded()` tiếp theo đều bị chặn đứng bởi điều kiện `isMove0 && isMove0AnalysisScheduled && fen.equals(lastLoadedMove0Fen)` dù trên màn hình không có bất kỳ mũi tên nào!
    - **Khắc phục triệt để**: Bổ sung điều kiện kiểm tra thực tế `OverlayManager.hasArrows()` vào các điểm chặn: Hệ thống CHỈ bỏ qua Move 0 khi và chỉ khi trên màn hình ĐÃ THỰC SỰ CÓ mũi tên hiển thị (`OverlayManager.hasArrows() == true`). Nếu chưa có mũi tên, hệ thống cho phép tiếp tục chạy phân tích và vẽ mũi tên ngay lập tức.
  - **Khắc Phục Rớt Vẽ Do Layout Timing & Gán Nhầm Chữ Ký (`OverlayManager.java`)**:
    - Khi engine tính xong nhưng bàn cờ chưa kịp layout kích thước (`bm == null`), `lastArrowSignature` từng bị gán giá trị mới trong `displayLiveResult`, khiến lần gọi sau tưởng đã vẽ rồi và không vẽ lại.
    - Trong `updateArrowOverlay`, khi `bm == null`, gọi ngay `StockfishExtension.resetArrowSignature()` trước khi post bất đồng bộ để đảm bảo lần thử lại tiếp theo không bao giờ bị bỏ qua do trùng signature.
  - **Lắng Nghe Sự Kiện Layout Kích Thước Bàn Cờ (`OverlayManager.java`)**:
    - Trong listener `addOnLayoutChangeListener` của `ChessBoardView`, nếu phát hiện bàn cờ thay đổi kích thước (`w > 0 && h > 0`) mà màn hình vẫn chưa có mũi tên (`!hasArrows()`), gọi trực tiếp `StockfishExtension.tryLoadMove0(act)` để vẽ mũi tên tức thì.
  - **Bảo Toàn Hàng Đợi Retry Khi Mũi Tên Chưa Xuất Hiện (`StockfishExtension.java`)**:
    - Trong `onActivityResumed`: Chỉ hủy hàng đợi retry khi và chỉ khi `loaded && OverlayManager.hasArrows()`. Các mốc thời gian retry (100ms, 300ms, 600ms, 1200ms, 2400ms, 4000ms) sẽ tiếp tục kiên trì thực hiện cho đến khi mũi tên thực sự đã xuất hiện trên bàn cờ.
  - Bộ kiểm thử tự động `SelfTest`: 134/134 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.87` (versionCode `20087`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 45 file Java Extension thành `extension.mpe` (2,782,572 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.87.mpp` (227,457,336 bytes).

### v2.0.86 (2026-10-12)
- **Tách Biệt Độc Lập 100% Mũi Tên Đe Dọa (Threat Arrow) & Mũi Tên Premove Trao Đổi Quân (Premove Arrow)**:
  - **Tách Riêng Hoàn Toàn 2 Tính Năng Độc Lập (`StockfishSettings.java`, `StockfishSettingsDialog.java`, `I18n.java`)**:
    - Phục hồi lại đầy đủ tính năng **Mũi Tên Đe Dọa (Threat Arrow)** bên cạnh **Mũi Tên Premove (Premove Arrow)**.
    - Cung cấp 2 switch điều khiển riêng biệt trong Tab Cài Đặt Mũi Tên: `threatSwitch` (`show_threat_arrows`) và `premoveSwitch` (`show_premove_arrows`). Người dùng có thể bật/tắt độc lập hoặc dùng cùng lúc cả hai.
  - **Định Hình Phong Cách Đồ Họa Riêng Biệt Cho Từng Loại Mũi Tên (`ArrowOverlayView.java`)**:
    - **Mũi Tên Đe Dọa (Threat Arrow)**: Dải màu Đỏ Cảnh Báo Nguy Hiểm (`#EF4444` phối `#B91C1C`), viền đỏ thẫm sắc nét (`#7F1D1D`), glow viền đỏ rực (`rgba(239, 68, 68, 0.45)`).
    - **Mũi Tên Premove (Premove Arrow)**: Dải màu Cam Hổ Phách Neon (`#F59E0B` phối `#EA580C`), viền than hổ phách tương phản (`#78350F`), glow viền hổ phách dịu mát (`rgba(245, 158, 11, 0.35)`).
    - **Mũi Tên Gợi Ý Play**: Giữ nguyên dải màu Tier 1-5 đặc trưng (Vàng, Xanh, Bạc, Tím, Lục).
  - **Chuẩn Hóa Tuyệt Đối Logic Mũi Tên Premove Chỉ Chỉ Vào Ô Có Quân Của Người Chơi (`StockfishExtension.java`)**:
    - Chỉ hiển thị sau khi người chơi vừa đi (`premoveEnabled && !isMyTurn`), đối thủ đang suy nghĩ chuẩn bị ăn quân.
    - **Điều kiện 1**: Ô xuất phát `replyFrom` BẮT BUỘC chứa quân của người chơi (`fromHasUserPiece = true`).
    - **Điều kiện 2**: Ô đích `replyTo` BẮT BUỘC đang chứa quân cờ của chính người chơi đang đứng trên bàn cờ (`targetHasUserPiece = true`).
    - **Điều kiện 3**: Nước đi trao đổi quân hiển nhiên (`replyTo.equals(oppTo)`): đối thủ sắp nhảy vào ăn quân người chơi tại ô đó, và mũi tên gợi ý người chơi chuẩn bị sẵn sàng premove ăn lại để không bị hớ hay đi nhầm nước chiến thuật phức tạp.
  - **Logic Mũi Tên Đe Dọa Độc Lập (`StockfishExtension.java`)**:
    - Khi tới lượt người chơi (`isMyTurn`): Dự đoán nước đe dọa tiếp theo của đối thủ (`result.ponder`).
    - Khi tới lượt đối thủ (`!isMyTurn`): Hiển thị nước cờ mạnh nhất của đối thủ (`result.moves.get(0)`).
  - Bộ kiểm thử tự động `SelfTest`: 132/132 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.86` (versionCode `20086`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 45 file Java Extension thành `extension.mpe` (2,781,900 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.86.mpp` (227,457,010 bytes).

### v2.0.85 (2026-10-12)
- **Dọn Dẹp Triệt Để Dead Code (Xóa Hoàn Toàn WdlBarView & Tối Ưu Hóa UCI Output)**:
  - **Xóa Bỏ Triệt Để Toàn Bộ Mã Nguồn WDL Còn Sót Lại**:
    - Xóa bỏ hoàn toàn lớp giao diện chết `WdlBarView.java`.
    - Dọn sạch các phương thức `updateWdlBar` và `hideWdlBar` trong `OverlayManager.java` cũng như tag `stockfish_wdl_bar` trong `ensureZOrder`.
    - Xóa bỏ cài đặt `KEY_SHOW_WDL`, `isWdlEnabled`, `setWdlEnabled` trong `StockfishSettings.java`.
    - Dọn sạch 100% các lệnh gọi `hideWdlBar()` trong `StockfishExtension.java` (tại các điểm xử lý Game Review, Engine Disabled, Game Over, Activity Resumed, Toggle Everything).
  - **Tối Ưu Hóa Hiệu Năng Engine UCI (`StockfishProcess.java`)**:
    - Loại bỏ lệnh gửi option `UCI_ShowWDL` khi khởi chạy engine, giảm tải xử lý chuỗi phân tích nội bộ và tối ưu hóa thời gian tính toán của Stockfish.
  - **Làm Sạch Toàn Diện Chú Thích & Header (`EvalBarView.java`, `StockfishExtension.java`)**:
    - Loại bỏ các comment và javadoc tàn dư về WdlBar.
  - Bộ kiểm thử tự động `SelfTest`: 128/128 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.85` (versionCode `20085`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 45 file Java Extension thành `extension.mpe` (2,779,560 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.85.mpp` (227,456,358 bytes).

### v2.0.84 (2026-10-12)
- **Ra Mắt Mũi Tên Premove Trao Đổi Quân (Premove Arrow) Chuẩn Sắc Với Style Neon Cam Hổ Phách**:
  - **Đổi Tên & Tái Cấu Trúc Tính Năng Thành 'Mũi Tên Premove' (Premove Arrow - `I18n.java`, `StockfishSettings.java`)**:
    - Chuyển đổi tên gọi trong toàn bộ UI cài đặt và chuỗi thông báo từ "Mũi Tên Đe Dọa" thành "Mũi Tên Premove" (`Premove Arrow`), phản ánh chính xác mục đích sử dụng.
  - **Hiển Thị Đúng Thời Điểm — Chỉ Hiện Sau Khi Người Chơi Vừa Đi (`StockfishExtension.java`)**:
    - Bổ sung điều kiện thời điểm `threatEnabled && !isMyTurn`: Tuyệt đối không hiển thị ở lượt của chính người chơi. Mũi tên premove chỉ kích hoạt khi tới lượt đối thủ đang suy nghĩ (sau khi người chơi vừa hoàn thành nước cờ của mình), chuẩn bị sẵn sàng cho thao tác premove kế tiếp.
  - **Thuật Toán Lọc Nước Đi Trao Đổi Quân Hiển Nhiên (Obvious Piece Exchange Recapture - `StockfishExtension.java`)**:
    - Bổ sung 2 phương thức tiện ích thuần túy: `getPieceAtSquare(fen, square)` đọc chính xác loại quân cờ từ chuỗi FEN và `isUserPiece(fen, square, userIsWhite)` kiểm tra quyền sở hữu quân cờ của người chơi.
    - **Điều kiện 1**: Ô xuất phát của mũi tên premove (`replyFrom`) BẮT BUỘC phải chứa quân cờ của chính người chơi (`isUserPiece = true`).
    - **Điều kiện 2**: Ô đích đến của mũi tên premove (`replyTo`) phải trùng với ô đối thủ nhảy vào (`oppTo`) HOẶC ô đích đang chứa quân của đối thủ chờ bị ăn lại (`targetHasOppPiece = true`).
    - Ngăn chặn triệt để tình trạng vẽ mũi tên cho các nước chiến thuật phức tạp khiến người chơi premove nhầm dẫn tới mất quân.
  - **Phong Cách Đồ Họa Riêng Biệt Phân Biệt Với Mũi Tên Gợi Ý Play (`ArrowOverlayView.java`)**:
    - Không dùng màu đỏ phẳng và không dùng màu của các tier mũi tên Play thông thường.
    - Áp dụng dải màu Cam Hổ Phách Neon (`#F59E0B` phối `#EA580C`) kết hợp viền sắc nét việt quất/than đậm (`#78350F`) và hiệu ứng Glow hổ phách tinh tế, giúp người chơi nhận biết ngay lập tức đây là nước premove trao đổi quân.
  - Bộ kiểm thử tự động `SelfTest`: 128/128 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.84` (versionCode `20084`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,782,888 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.84.mpp` (227,457,369 bytes).

### v2.0.83 (2026-10-12)
- **Tự Động Khởi Chạy Live Rematch, Khắc Phục Mũi Tên Đối Thủ Move 0 & Ẩn Hoàn Toàn Trình Xem Lại Ván Đấu (Game Review)**:
  - **Khắc Phục 100% Ẩn Mũi Tên Đối Thủ Khi Tắt Mũi Tên Đối Thủ Tại Move 0 (`StockfishExtension.java`)**:
    - Xóa bỏ điều kiện `!isStartPos` trong `displayLiveResult`: `if (showArrows && StockfishSettings.isMySideOnly(context) && !isMyTurn) showArrows = false;`. Khi người chơi tắt mũi tên đối thủ (`isMySideOnly = true`) và cầm quân Đen, mũi tên gợi ý của Trắng ở Move 0 bị chặn hoàn toàn, không còn hiện nhầm.
    - Cải tiến nhận diện màu quân người chơi `isUserWhite`: Ưu tiên đọc trực tiếp thuộc tính `getFlipBoard()` từ `ChessBoardView` đang hiển thị trên màn hình trước khi fallback, nhận diện chuẩn 100% vai trò người chơi (Trắng/Đen) ngay từ Move 0.
  - **Tự Động Nhận Diện & Tải Lại Chức Năng Khi Sang Ván Mới (Live Rematch / Chơi Tiếp - `StockfishExtension.java`)**:
    - Loại bỏ hoàn toàn việc đọc StateFlow `vm._gameOver` / `vm.oa()` trong `isGameOver` vì `RealGameViewModel` của Chess.com không bao giờ reset các trường này về null sau khi ván đấu kết thúc.
    - Thêm Startpos Guard ngay đầu `isGameOver`: Bàn cờ ở thế mở màn (`rnbqkbnr/pppppppp/...`) tuyệt đối không bị coi là Game Over, đảm bảo ván tiếp theo tải lại đầy đủ mũi tên, eval bar và phân loại tức thì mà không cần thoát app ra ngoài đa nhiệm.
  - **Ẩn Toàn Bộ Overlays Trong Giao Diện Phân Tích & Xem Lại Ván Đấu (Game Review - `StockfishExtension.java`, `OverlayManager.java`)**:
    - Bổ sung hàm nhận diện `isReviewOrAnalysisActivity(activity)` cho các Activity `GameReviewActivity`, `StandaloneAnalysisActivity`, `ArchivedLiveGameActivity`, `ArchiveSearchActivity`.
    - Chặn đứng 100% việc vẽ mũi tên, Eval Bar, WDL Bar, Engine Info và Widget Accuracy / Elo; tự động dọn dẹp sạch sẽ toàn bộ overlays khi người chơi mở giao diện Game Review hoặc phân tích ván đấu.
  - Bộ kiểm thử tự động `SelfTest`: 124/124 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.83` (versionCode `20083`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,781,308 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.83.mpp` (227,456,780 bytes).

### v2.0.82 (2026-10-12)
- **Khôi Phục Vị Trí Mặc Định Gốc Cho Accuracy & Elo Widget**:
  - Đưa vị trí mặc định ban đầu của Accuracy & Elo widget trong `OverlayManager.java` về nguyên bản (`defY = bm.boardY - widgetH - 6dp`).
  - Giữ nguyên cơ chế cho phép người dùng tự do kéo thả widget đến bất kỳ vị trí mong muốn nào trên màn hình.
  - Bộ kiểm thử tự động `SelfTest`: 120/120 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.82` (versionCode `20082`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,780,044 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.82.mpp` (227,456,498 bytes).

### v2.0.81 (2026-10-12)
- **Sửa Lỗi Hiển Thị Badge Phân Loại Nước Cờ Nhầm Sang Ô Hint Engine & Cân Chỉnh Vị Trí Accuracy Elo Widget**:
  - **Sửa Lỗi Gán Nhầm Tọa Độ Badge & Highlight Phân Loại Nước Cờ (`TorchEngine.java`, `MoveClassifier.java`)**:
    - **Nguyên nhân gốc rễ**: Khi phân tích thế cờ qua Torch CEE (WASM Engine), nếu kích hoạt `HandleContinuations = true`, mảng `positions` trả về chứa thêm các nước đi dự đoán tương lai (continuation plies). Trước đây, `TorchEngine.java` lấy mù quáng phần tử cuối mảng `positions.getJSONObject(positions.length() - 1)` (chứa nước gợi ý tương lai, ví dụ `g8f6`) thay vì nước cờ thực tế vừa đánh trên bàn cờ (`d2d4`). Đồng thời `MoveClassifier.java` ưu tiên `playedMoveLan` từ continuation khiến badge Book/Best Move và khung highlight cam bị vẽ nhầm lên ô đích của engine hint (`f6`) dù người chơi chưa đánh!
    - **Khắc phục triệt để**:
      - Trong `TorchEngine.java`: Duyệt ngược mảng `positions` để tìm đúng `targetPos` mang nhãn `classificationName` tương ứng với nước cờ thực tế.
      - Trong `MoveClassifier.java`: Hàm `displayTorchClassification` chỉ chấp nhận và sử dụng duy nhất `finalUci` (nước cờ thực tế hook từ bàn cờ).
      - Trong `StockfishExtension.java` và `MoveClassifier.java`: Bổ sung guard `!isStartPos`, ngăn chặn hoàn toàn việc phân loại ở Move 0 khi bàn cờ ở trạng thái xuất phát (`startpos`).
  - **Cân Chỉnh Vị Trí Mặc Định Widget Accuracy & Elo Tránh Đè Lên Khung Chat Bot (`OverlayManager.java`)**:
    - Chuyển tọa độ mặc định `defY` thành `64f * density` (nằm gọn gàng bên góc trên bên phải, ngay dưới Action Bar).
    - Giải phóng 100% không gian hiển thị của avatar, thông tin người chơi và bóng thoại hội thoại của Bot trong Bot Game, không còn bị widget che khuất.
  - Bộ kiểm thử tự động `SelfTest`: 120/120 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.81` (versionCode `20081`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,779,988 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.81.mpp` (227,456,581 bytes).

### v2.0.80 (2026-10-12)
- **Triệt Tiêu 100% Lỗi Crash Vào Trận Đấu (StackOverflowError) & Tối Ưu Hóa Move 0 Toàn Diện**:
  - **Khắc Phục Toàn Diện Sự Cố Crash Khi Vừa Mở Bàn Cờ (`OverlayManager.java`)**:
    - **Xóa bỏ hoàn toàn đệ quy vô hạn trong `findChessBoardView`**: Phát hiện nguyên nhân gốc rễ gây crash văng ứng dụng ngay khi vào trận trong v2.0.79: Bên trong `findChessBoardView`, nhánh `else if (!hasArrows())` và lệnh gọi `StockfishExtension.onInitialPositionLoaded(board)` đã tạo ra chuỗi gọi đệ quy lặp vô tận: `findChessBoardView -> onInitialPositionLoaded -> onBoardChanged -> isGameOver -> findChessBoardView`. Khi vừa vào ván, `hasArrows()` chưa kịp vẽ (bằng `false`) dẫn tới hàm tự gọi lại chính nó hàng nghìn lần làm tràn ngăn xếp `java.lang.StackOverflowError` gây crash tức thì!
    - **Đưa `findChessBoardView` về hàm tìm kiếm thuần túy (Pure Locator)**: Tuyệt đối không gọi bất kỳ hàm phân tích thế cờ nào bên trong `findChessBoardView`. Chỉ gắn listener `OnAttachStateChangeListener` và `OnLayoutChangeListener` duy nhất một lần qua hash set; trong đó `onViewAttachedToWindow` chuyển sang post bất đồng bộ qua `Handler(Looper.getMainLooper())` để không bao giờ can thiệp chuỗi thực thi hiện tại.
  - **Bảo Vệ An Toàn Tuyệt Đối Phân Giải Thế Cờ (`StockfishExtension.resolveVariantPosition`)**:
    - Ngăn chặn triệt để hành vi duyệt reflection gọi bừa mọi phương thức 0 tham số: Nếu đối tượng là `View`, `Activity`, hoặc `Context`, lập tức trả về `null`.
    - Chỉ cho phép gọi reflection trên các hàm getter có giá trị trả về (`returnType != void.class`) và tên bắt đầu bằng `get` hoặc ký tự rút gọn (`a`, `b`), loại bỏ hoàn toàn rủi ro kích hoạt nhầm các phương thức can thiệp giao diện như `performClick()`, `clearFocus()`, `requestLayout()`.
  - **Loại Bỏ Reflection `boardView.getMovesHistory()` Trong `isGameOver`**:
    - Xóa bỏ việc quét reflection trên `boardView` trong `isGameOver`. Thay vào đó, tin cậy tuyệt đối 4 tầng nguồn sự thật chuẩn của Chess.com (field `m0` / `gameOverOverlayView` của Activity, `StateFlow` kết thúc ván của ViewModel, các `DialogFragment` Game Over đang `isShowing()`, và các View Modal trong `DecorView`). Giúp triệt tiêu 100% false-positive kết thúc ván và giảm tối đa chi phí reflection mỗi 300ms.
  - **Khởi Động Move 0 Ổn Định & Không Bị Reset Giữa Chừng**:
    - Loại bỏ điều kiện `&& hasArrows()` trong deduplication của `tryLoadMove0` và `onBoardChanged`, đảm bảo khi engine đang tính toán nước đi mở màn (500ms - 1500ms) sẽ không bị các đợt retry ngắt quãng hay hủy tìm kiếm liên tục.
  - Bộ kiểm thử tự động `SelfTest`: 117/117 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.80` (versionCode `20080`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,779,500 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.80.mpp` (227,456,309 bytes).

### v2.0.79 (2026-10-12)
- **Tự Động Nhận Diện & Tải Lại Move 0 Khi Sang Ván Mới (Rematch / New Game) & Triệt Tiêu Chặn Chụp Màn Hình (`FLAG_SECURE`)**:
  - **Khắc Phục Toàn Diện Sự Cố Chuyển Sang Ván Mới Trong Cùng `RealGameActivity` (`StockfishExtension.java`, `OverlayManager.java`)**:
    - **Phát hiện & xử lý chuyển trạng thái Game Over sang Ván mới (`checkAndHandleGameOver`)**: Trong vòng lặp `GameOverWatchdog`, khi phát hiện trạng thái thay đổi từ `isGameOver == true` sang `isGameOver == false` (người chơi đóng modal kết thúc hoặc bấm Rematch/New Game):
      - Đặt lại `lastGameOverHandled = false`, xóa bỏ FEN Move 0 đã lưu (`lastLoadedMove0Fen = null`, `isMove0AnalysisScheduled = false`).
      - Làm mới chữ ký mũi tên (`lastArrowSignature = null`, `lastScheduledKey = null`).
      - Xóa sạch danh sách View bàn cờ đã hook (`OverlayManager.clearHookedBoardViews()`).
      - Giải phóng ngay cờ bảo mật `FLAG_SECURE` (`unlockScreenshots(activity)`).
      - Kích hoạt phân tích Move 0 tức thì (`tryLoadMove0(activity)`).
    - **Triệt tiêu False-Positive do lịch sử nước cờ cũ còn sót lại (`isGameOver` - Tầng 4)**: Sửa nguyên nhân cốt lõi khiến ván mới không khởi động: Khi người chơi bấm Rematch, bàn cờ quay về thế mở màn nhưng danh sách `getMovesHistory` của Chess.com chưa được dọn sạch ngay, vẫn còn lưu nước cờ Chiếu hết `#` hoặc kết quả `1-0`, `0-1` của ván trước. Hệ thống giờ đây kiểm tra FEN hiện tại của bàn cờ: Nếu đang ở `startpos` (`rnbqkbnr/pppppppp/...`), TUYỆT ĐỐI BỎ QUA kiểm tra `movesHistory`, ngăn chặn triệt để việc hiểu nhầm ván mới thành ván đã kết thúc.
    - **Cơ chế Fallback nạp Move 0 linh hoạt (`findChessBoardView`)**: Nếu View bàn cờ đã nằm trong tập `hookedBoardViews` nhưng màn hình hiện tại chưa có mũi tên (`!hasArrows()`), lập tức kích hoạt `StockfishExtension.onInitialPositionLoaded(board)`, đảm bảo mũi tên Move 0 hiển thị ngay lập tức mà người dùng không cần thoát app ra ngoài đa nhiệm.
  - **Triệt Tiêu Triệt Để Hành Vi Chặn Chụp Màn Hình (`FLAG_SECURE`)**:
    - Phân tích nguyên nhân: Trong `RealGameActivity`, coroutine lắng nghe sự kiện của Chess.com liên tục tự động bật lại cờ `FLAG_SECURE` khi có cập nhật UI hoặc sang ván mới.
    - Bổ sung lệnh gọi `unlockScreenshots(activity)` định kỳ bên trong vòng lặp `GameOverWatchdog` (mỗi 300ms) và khi phát hiện bắt đầu ván mới, bảo đảm quyền chụp màn hình của người dùng luôn được mở khóa liên tục và xuyên suốt.
  - Bộ kiểm thử tự động `SelfTest`: 117/117 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.79` (versionCode `20079`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,779,936 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.79.mpp` (227,456,471 bytes).

### v2.0.78 (2026-10-12)
- **Triệt Tiêu 100% False-Positive Game Over & Phục Hồi Toàn Diện Mũi Tên / Eval Bar / Badges**:
  - **Triệt Tiêu Toàn Bộ False-Positive Khi Đang Chơi Ván Đấu (`StockfishExtension.java`)**:
    - **Loại bỏ hoàn toàn kiểm tra `V6()` và trường `S` (`showGameOverModal`)**: Phát hiện nguyên nhân cốt lõi trong `RealGameActivity.java` (dòng 330 & 935), biến `showGameOverModal` được khởi tạo bằng `Boolean.TRUE` ngay từ khi Activity vừa tạo ra (`p0.j(Boolean.TRUE, ...)`). Trong v2.0.77, việc gọi `V6()` hoặc đọc field `S` khiến `isGameOver` luôn trả về `true` ngay mili-giây đầu tiên, chặn đứng toàn bộ `onBoardChanged`, `tryLoadMove0`, xóa sạch mũi tên và Eval Bar! Đã loại bỏ triệt để việc đọc `V6()` và field `S`.
    - **Xóa bỏ heuristic `ComposeView` toàn màn hình trong `hasGameOverView`**: Toàn bộ giao diện bàn cờ và quân cờ của Chess.com 4.10.x được xây dựng bằng Jetpack Compose. Việc coi `ComposeView` có kích thước bằng Activity là dialog kết thúc ván đã gây false-positive 100% ngay khi vừa vào trận. Đã xóa bỏ hoàn toàn kiểm tra này; chỉ nhận diện khi class name cụ thể chứa `"GameOverOverlay"`, `"GameOverDialog"` hoặc resource id mang `"game_over_overlay"`.
    - **Loại bỏ `isAdded()` đơn lẻ trong `isFragmentAttachedOrShowing`**: Các DialogFragment kết thúc ván có thể tồn tại trong FragmentManager ở trạng thái retained/cached khiến `isAdded()` trả về `true` dù dialog chưa bao giờ hiển thị. Hệ thống giờ đây CHỈ coi là kết thúc khi `getDialog().isShowing()` hoặc `getView().isShown()` thực sự hiển thị trên màn hình.
    - **Tinh chỉnh ViewModel Game Over Inspection**: Trong StateFlow của ViewModel (`oa()`, `_gameOver`, `_gameOverResult`), chỉ coi là Game Over khi giá trị khác null và nếu là Boolean thì phải là `Boolean.TRUE` (bảo đảm không dính `Boolean.FALSE`).
  - **Duy Trì Nhận Diện Game Over Chuẩn Xác Tuyệt Đối**:
    - Nhận diện tức thời khi `gameOverOverlayView` (field `m0` của `RealGameActivity`) khác null và có `visibility == View.VISIBLE`.
    - Nhận diện tức thì nước cờ chiếu hết `#` hoặc ký hiệu kết quả ván đấu (`1-0`, `0-1`, `1/2-1/2`) trong lịch sử nước đi.
    - Nhận diện 8 tag DialogFragment Game Over chuẩn của Chess.com khi dialog đang thực sự `isShowing()`.
    - Dọn sạch toàn bộ mũi tên, huy hiệu, hủy hàng chờ phân loại của Torch Engine và dừng tìm kiếm engine khi ván thực sự kết thúc.
  - Bộ kiểm thử tự động `SelfTest`: 116/116 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.78` (versionCode `20078`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,779,492 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.78.mpp` (227,456,394 bytes).

### v2.0.77 (2026-10-12)
- **Khắc Phục Triệt Để Hiển Thị Mũi Tên Move 0 (Cả Trắng & Đen) & Hệ Thống Nhận Diện Game Over 5 Tầng Toàn Diện**:
  - **Khắc Phục Triệt Để Mũi Tên Move 0 Cho Cả Người Chơi Cầm Trắng Và Đen (`StockfishSettings.java`, `StockfishExtension.java`, `OverlayManager.java`)**:
    - **Đổi giá trị mặc định của `KEY_MY_SIDE_ONLY` sang `false` (`StockfishSettings.java`)**: Khớp 100% với cấu hình gốc Extension NNVC (`showOppArrow = true`). Trong v2.0.76 trở về trước, `isMySideOnly` mặc định bằng `true` khiến khi người chơi cầm quân Đen, lượt mở màn của Trắng bị hệ thống coi là "nước của đối thủ" và chặn đứng hoàn toàn việc vẽ mũi tên Move 0.
    - **Miễn trừ Startpos Khỏi Điều Kiện `isMySideOnly` (`StockfishExtension.displayLiveResult`)**: Bổ sung điều kiện `!isStartPos` vào bộ lọc `if (isMySideOnly && !isStartPos && !isWhiteTurn) return;`. Ở thế cờ mở màn ván đấu (Move 0), mũi tên luôn luôn được hiển thị cho bên chuẩn bị đi bất kể cài đặt bên người chơi.
    - **Cơ chế chống chặn nhầm Move 0 (`hasArrows` check)**: Tinh chỉnh điều kiện bỏ qua deduplication trong `onBoardChanged`, `tryLoadMove0`, và `onInitialPositionLoaded` thành `if (isMove0 && isMove0AnalysisScheduled && fen.equals(lastLoadedMove0Fen) && OverlayManager.hasArrows())`. Hệ thống CHỈ bỏ qua khi trên màn hình ĐÃ CÓ mũi tên hiển thị thực tế. Nếu mũi tên chưa hiển thị (do layout view bị trễ, ván mới chuyển đổi), hệ thống lập tức kích hoạt phân tích lại mà không bị chặn nhầm.
    - **Kích hoạt Move 0 ngay đợt Layout đầu tiên (`OverlayManager.java`)**: Trong `OnLayoutChangeListener` của `ChessBoardView`, bổ sung kiểm tra: khi bàn cờ hoàn thành layout lần đầu (`v.getWidth() > 0 && v.getHeight() > 0`) mà màn hình chưa có mũi tên (`!hasArrows()`), gọi ngay `StockfishExtension.onInitialPositionLoaded(v)` để vẽ mũi tên tức thì.
  - **Hệ Thống Nhận Diện Game Over 5 Tầng Siêu Bảo Đảm (`StockfishExtension.java`)**:
    - **Tầng 1 (ViewModel StateFlow Direct Reading)**: Truy cập `RealGameViewModel`, `BotGameViewModel`, `PracticePlayGameViewModel` qua reflection `X6()` / `getViewModel()`. Đọc trực tiếp StateFlow kết thúc ván: `vm.oa().getValue() != null` cũng như các trường `_gameOver`, `gameOver`, `b1`, `a1`, `_gameOverResult`. Nhận diện ván đấu kết thúc ngay lập tức ở tầng dữ liệu lõi trước khi UI kịp render.
    - **Tầng 2 (Activity State & Modal View)**: Kiểm tra trực tiếp hàm `activity.V6() == true`, trường `S` / `showGameOverModal.getValue() == Boolean.TRUE`, và trường `gameOverOverlayView` / `m0`.
    - **Tầng 3 (DialogFragment Attached & Showing)**: Mở rộng nhận diện trên 8 tag dialog kết thúc ván chuẩn của Chess.com (`"BaseGameOverDialog"`, `"LiveGameOverDialog"`, `"LiveGameOverDialogV2"`, `"LiveArenaGameOverDialog"`, `"PracticePlayGameOverDialog"`, `"BotGameOverDialog"`, `"PassAndPlayGameOverDialog"`, `"VisionChallengeCompleteDialog"`). Kiểm tra `isAdded() && !isRemoving()` và `dialog.isShowing()` trong AndroidX FragmentManager, khắc phục triệt để tình trạng `isVisible()` trả về `false` do view nằm trong Dialog Window.
    - **Tầng 4 (Checkmate / Result Notation Parsing)**: Quét nước cờ cuối cùng trong `MoveClassifier.historyMoves` và `movesHistory`: Nếu kết thúc bằng ký tự `#` (Chiếu hết) hoặc mang chuỗi kết quả `1-0`, `0-1`, `1/2-1/2`, lập tức xác định Game Over.
    - **Tầng 5 (DecorView Top-level Modal Hierarchy)**: Quét cây View của DecorView tìm kiếm `ComposeView` hoặc view bao phủ toàn màn hình có chứa các resource ID hoặc thuộc tính dialog kết thúc.
    - **Dọn Dẹp Triệt Để Tức Thời Khi Game Over**: Ngay khi phát hiện Game Over, hệ thống gọi `OverlayManager.hideArrowOverlay(true)` xóa sạch cả mũi tên lẫn huy hiệu, đặt `ArrowOverlayView.setVisibility(View.GONE)`, dừng tìm kiếm engine (`StockfishBridge.stopSearch()`), hủy toàn bộ hàng chờ phân loại của Torch Engine (`cancelPendingRequests()`) và dừng watchdog.
  - Bộ kiểm thử tự động `SelfTest`: 117/117 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.77` (versionCode `20077`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,779,880 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.77.mpp` (227,456,536 bytes).

### v2.0.76 (2026-10-11)
- **Triệt Tiêu Nháy Move 0, Chuẩn Hóa Điểm Mũi Tên Đối Thủ (Side-to-move POV) & Active Game Over Watchdog**:
  - **Triệt Tiêu 100% Hiện Tượng Nháy / Tải Lại Giao Diện Move 0 (`OverlayManager.java` & `StockfishExtension.java`)**:
    - **Loại bỏ trigger lặp trong `OnLayoutChangeListener` (`OverlayManager.java`)**: Xóa bỏ hoàn toàn lệnh gọi `StockfishExtension.onInitialPositionLoaded(v)` mỗi khi bàn cờ layout lại. `OnLayoutChangeListener` giờ đây chỉ đơn thuần gọi `invalidateArrowOverlay()` khi bounds/kích thước bàn cờ thay đổi, triệt tiêu 100% hiện tượng reset và nháy mũi tên ở Move 0.
    - **Giữ kiên định Accuracy / Elo Pills (Persistent Mounting)**: Xóa bỏ lệnh `OverlayManager.hideAccuracyEloPills()` trong nhánh `isStartPos` của `StockfishExtension.onBoardChanged` và `triggerAnalysisForCurrentState`. Widget huy hiệu độ chính xác và Elo giữ nguyên trên màn hình, không bị chớp tắt hoặc ẩn đi rồi hiện lại khi sang ván mới.
    - **Deduplication Move 0 Retries & Hooks**: Sửa triệt để bug cờ `isMove0AnalysisScheduled` bị thiếu gán `true` trong phiên bản trước. Bổ sung kiểm tra deduplication trực tiếp trong điểm nút `onBoardChanged`: nếu Move 0 FEN đã được lên lịch, lập tức bỏ qua (`[MOVE 0 SKIP]`), triệt tiêu 100% các lệnh reset Eval Bar, reset Accuracy Widget và xóa mũi tên lặp lại từ các view attach listener / retries.
    - **Đồng bộ Lifecycle Reset**: Tự động reset `isMove0AnalysisScheduled = false` và `lastLoadedMove0Fen = null` khi Game Over hoặc khi Activity Resume / Pause / Destroy, đảm bảo ván cờ mới luôn được nhận diện và phân tích Move 0 tức thì mà không bị chặn nhầm.
  - **Sửa Triệt Để Thứ Tự Ưu Tiên & Nhãn Điểm Số Mũi Tên Đối Thủ (Side-to-move POV Chuẩn Extension NNVC)**:
    - **Sửa dứt điểm bug đảo lượt đi trong `StockfishProcess.java`**: Phân tích nguyên nhân: Trong `isWhiteToMove`, mã nguồn cũ có dòng `if (moves != null && (moves.size() & 1) == 1) white = !white;` tự ý đảo ngược lượt đi của FEN vốn đã chuẩn xác. Khi bên Đen tới lượt đi (FEN chứa 'b'), dòng code này khiến hệ thống hiểu nhầm thành lượt Trắng, làm đảo ngược dấu điểm số và thứ tự ưu tiên của các nhánh MultiPV! Đã loại bỏ triệt để dòng code này, chỉ đọc trực tiếp từ FEN; đồng thời chuẩn hóa `isWhiteTurnFromFen` trong `StockfishExtension` an toàn với mọi biến thể FEN.
    - **Bổ sung `sideToMoveScores` vào `AnalysisResult`**: Tính toán điểm số từ góc nhìn của bên đang tới lượt đi (`sideToMovePawns = whiteToMove ? pawns : -pawns`).
    - **Đồng bộ hàm định dạng nhãn mũi tên `OverlayManager.formatArrowEvalLabel`**: Chuẩn hóa 100% theo logic hàm `_formatArrowEvalLabel` của Extension NNVC:
      - Khi có Chiếu hết (`branchHasMate`): `branchMateIn > 0 ? ("#" + branchMateIn) : ("#-" + Math.abs(branchMateIn))`.
      - Khi tính điểm Centipawns thông thường: `(sideToMovePawns > 0 ? "+" : "") + String.format(Locale.US, "%.1f", sideToMovePawns)` (ví dụ: Đen ưu thế hơn 1.8 chốt sẽ hiển thị `+1.8`). Mũi tên bậc 1 luôn là nước cờ tối ưu nhất cho bên chuẩn bị đi.
  - **Nhận Diện Game Over Chủ Động Tức Thì (Active Game Over Watchdog & Dialog `isShowing()`)**:
    - **Triển khai `GameOverWatchdog` (`StockfishExtension.java`)**: Tạo vòng lặp watchdog định kỳ 300ms kết hợp `OnGlobalLayoutListener` gắn vào `DecorView` của Activity. Ngay khi có bất kỳ biến động nào trên cây View hoặc tối đa 300ms sau khi ván đấu kết thúc, hệ thống chủ động quét và phát hiện Game Over ngay lập tức, không còn phụ thuộc vào việc bàn cờ phải phát sinh sự kiện mới.
    - **Kiểm tra chuyên sâu `isFragmentVisibleOrShowing(frag)`**: Duyệt qua `Dialog` của DialogFragment kết thúc ván đấu và kiểm tra `dialog.isShowing()`, đồng thời bổ sung kiểm tra `frag.getView().getVisibility() == View.VISIBLE && getParent() != null` cho mọi Fragment kết thúc trận đấu.
    - **Dọn dẹp triệt để khi kết thúc ván**: Ngay khi phát hiện Game Over, hệ thống lập tức ẩn mũi tên và xóa huy hiệu (`OverlayManager.hideArrowOverlay(true)`), đặt `ArrowOverlayView.setVisibility(View.GONE)` vô điều kiện, dừng tìm kiếm engine (`StockfishBridge.stopSearch()`), hủy hàng chờ phân loại của Torch Engine (`cancelPendingRequests()`) và dừng watchdog.
  - Bộ kiểm thử tự động `SelfTest`: 113/113 test cases vượt qua thành công (100% passed).
  - Bump phiên bản lên `v2.0.76` (versionCode `20076`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,776,612 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.76.mpp` (227,455,389 bytes).

### v2.0.75 (2026-10-11)
- **Độc Lập Eval Bar Khi Đổi Elo/Depth & Nhận Diện Trận Đấu Mới (Move 0) Ngay Lập Tức**:
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
    - **Multiple Retries Trong `onActivityResumed`**: Mở rộng chuỗi kiểm tra và kích hoạt phân tích tại các mốc thời gian 100ms, 300ms, 600ms, 1200ms, 2400ms, 4000ms để đảm bảo khi `ChessBoardView` vừa được khởi tạo và nạp vào phân cấp View trên mọi thiết bị, phân tích Move 0 được kích hoạt ngay lập tức mà không cần người chơi phải đi nước cờ nào.
    - **Hook Tự Động Gắn Vào `ChessBoardView` (`OverlayManager.java`)**: Khi `findChessBoardView` phát hiện `ChessBoardView`, kiểm tra `getWindowToken() != null || getParent() != null` để gọi `StockfishExtension.onInitialPositionLoaded` ngay tức khắc; đồng thời đăng ký cả `OnAttachStateChangeListener` và `OnLayoutChangeListener` để kích hoạt Move 0 ngay đợt layout đầu tiên.
    - **Hàm `resolveVariantPosition`, `extractPositionFromBoardView` & Xử Lý An Toàn Null State (`StockfishExtension.java`)**:
      - Bổ sung `resolveVariantPosition(Object)`: Đệ quy giải mã đối tượng bàn cờ, tự động mở bọc `DisplayPosition` (`f0.a()`), gọi các phương thức getter `getPosition()`, `a()`, quét phản chiếu trường private để trích xuất đối tượng `d` (`extractFen`).
      - Xóa bỏ rào cản `if (stateImplObject == null) return;` trong `onBoardChanged`: Cho phép thế cờ Move 0 được nạp và phân tích hoàn toàn bình thường ngay cả khi `CBViewModelStateImpl` chưa kịp khởi tạo.
      - Tự động dọn sạch lịch sử ván cũ (`clearHistory()`, `resetEvalBar()`, `resetClassifyGame()`) khi ở Move 0 / startpos.
    - **An Toàn Tuyệt Đối Cho Dalvik ART Bytecode Verifier (`Fingerprints.kt`)**: Loại bỏ hook constructor `<init>` của `CBViewModelStateImpl` nhằm triệt tiêu hoàn toàn lỗi `VerifyError` khi bytecode truyền tham số chưa khởi tạo trước `super.<init>()`; sử dụng hook view và reflection an toàn 100%.
  - Bộ kiểm thử tự động `SelfTest`: 79/79 test cases vượt qua thành công (bao gồm kiểm tra chống false-positive isGameOver, classify skip khi trùng FEN, unwrap DisplayPosition bằng resolveVariantPosition, null state safety Move 0, soft movetime, move 0 startpos detection, scale math, classification thresholds, startpos vs fen command selection, signature palette color altered checks, gameover dialog tags).
  - Bump phiên bản lên `v2.0.75` (versionCode `20075`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,766,936 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.75.mpp` (227,451,437 bytes).

### v2.0.74 (2026-10-11)
- **Real-time Reactivity, Ván Đấu & Move 0 Mượt Mà, Game Over Triệt Để, Soft Movetime Tối Ưu Tốc Độ Depth 12**:
  - **Real-time Reactivity (Phản Ứng Tức Thì Khi Đổi Cài Đặt)**:
    - Khi thay đổi Depth, Elo, MultiPV, Engine (Komodo/Stockfish) hoặc Komodo play style (Default/Attacking/Defensive) trong menu cài đặt (`StockfishSettingsDialog.java`), áp dụng ngay lập tức sau 100ms debounce mà không cần chờ sang nước cờ tiếp theo hay ván mới.
    - Xóa sạch cache kết quả (`StockfishBridge.clearCache()`), ngắt tìm kiếm cũ (`StockfishBridge.stopSearch()`), làm mới chữ ký mũi tên (`lastArrowSignature = null`), và kích hoạt phân tích ngay trên bàn cờ hiện tại (`StockfishExtension.triggerAnalysisForCurrentState()`).
    - Nâng cấp `StockfishBridge.CacheEntry`: Gắn thêm `engineChoice` và `komodoStyle` cùng Elo thực tế (`StockfishSettings.getElo()`) vào key cache, triệt tiêu 100% hiện tượng dùng nhầm kết quả cache của engine/lối chơi khác.
    - **Reactivity Đổi Màu Mũi Tên (Arrow Palette)**: Khi đổi màu swatch trong palette màu mũi tên, hệ thống gọi `resetArrowSignature()` và `OverlayManager.invalidateArrowOverlay()`, đồng thời nhúng mã màu của 3 tier vào `lastArrowSignature`, đảm bảo mũi tên được vẽ lại ngay lập tức với màu sắc mới mà không bị chặn bởi bộ lọc signature.
  - **Game Lifecycle Detection (Tự Động Nhận Diện Move 0 & Xử Lý Triệt Để Game Over)**:
    - **Move 0 (New Game / Startpos) Phân Tích Tự Động Cho Cả Trắng & Đen**:
      - Bổ sung cơ chế Fallback trực tiếp trong `getStateImpl()`, `triggerAnalysisForCurrentState()` và `triggerPlayAnalysisOnly()`: Nếu `stateImplRef.get() == null` do hook chưa kịp gán, hệ thống tự động quét `decorView` -> `OverlayManager.findChessBoardView(decorView)` -> reflectively gọi `getViewModel().getState()` hoặc trích xuất trực tiếp `positionObject` từ `ChessBoardView.getPositionWithPremoves()` / `getPosition().a()`.
      - Khi bắt đầu ván mới (bàn cờ ở startpos hoặc sau khi người chơi/đối thủ đi nước 1), khởi chạy phân tích nước tốt nhất ngay lập tức cho bên tới lượt đi.
      - Sửa lỗi trong `MoveClassifier.updateHistory`: Khi bàn cờ ở startpos (`moves.isEmpty()`), gán danh sách rỗng thay vì giữ nguyên mảng moves của ván cũ, đảm bảo ván mới bắt đầu hoàn toàn sạch sẽ.
    - **Xử Lý Triệt Để Game Over (`isGameOver`)**:
      - Kiểm tra trường `gameOverOverlayView` (hoặc `m0`) của `RealGameActivity` cùng việc duyệt phân cấp View tìm kiếm các component kết thúc ván đấu (`hasGameOverView`).
      - **Bổ Sung Quét DialogFragment Game Over**: Quét `getSupportFragmentManager()` / `getFragmentManager()` để nhận diện triệt để các DialogFragment kết thúc ván đấu chuẩn của Chess.com: `"BaseGameOverDialog"`, `"LiveGameOverDialog"`, `"LiveGameOverDialogV2"`, `"LiveArenaGameOverDialog"`, `"PracticePlayGameOverDialog"`, `"BotGameOverDialog"`, `"PassAndPlayGameOverDialog"`, `"VisionChallengeCompleteDialog"`, cũng như mọi fragment chứa class name `"GameOver"`.
      - **Dọn Sạch Toàn Bộ Badges Khi Game Over (`hideArrowOverlay(true)`)**: Khi kết thúc ván hoặc ẩn mũi tên do Game Over, ép buộc dọn sạch cả huy hiệu phân loại (`clearClassificationBadge()`) và đặt `ArrowOverlayView.setVisibility(View.GONE)`, triệt tiêu hoàn toàn nguy cơ sót badge hoặc overlay hiển thị đè lên màn hình Game Over.
      - Khi ván đấu kết thúc: Ngay lập tức ẩn toàn bộ mũi tên gợi ý (`OverlayManager.hideArrowOverlay(true)`), dừng engine tính toán (`StockfishBridge.stopSearch()`), hủy toàn bộ hàng chờ phân loại của Torch Engine (`cancelPendingRequests()`), và chặn tuyệt đối việc vẽ đè mũi tên lên màn hình Game Over trong cả `displayLiveResult` lẫn `OverlayManager.updateArrowOverlay`.
  - **Soft Movetime & Tối Ưu Tốc Độ Depth 12**:
    - Đồng bộ chuẩn movetime với Extension NNVC: `softMs = Math.max(700, Math.min(2100, effectiveDepth * 175))` và gửi lệnh `go depth <depth> movetime <softMs>`.
    - Khi có danh sách moves và nước đi đầu tiên hợp lệ theo startpos (`WasmEngineManager.isStandardStartposMoves(uciMoves)`), gửi `position startpos moves <move1> <move2> ...` để tái sử dụng 100% Transposition Table (Hash 64MB) trong WASM engine, tăng tốc độ tính toán gấp 3 lần tại Depth 12; an toàn fallback về `position fen <fen>` nếu là biến thể Chess960, Setup position hoặc Odds game.
  - Bộ kiểm thử tự động `SelfTest`: 71/71 test cases vượt qua thành công (bao gồm soft movetime, move 0 startpos detection, scale math, classification thresholds, startpos vs fen command selection, signature palette color altered checks, gameover dialog tags).
  - Bump phiên bản lên `v2.0.74` (versionCode `20074`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,763,056 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.74.mpp` (227,450,118 bytes).

### v2.0.73 (2026-10-11)
- **Sửa Triệt Để Mũi Tên Bị Thay Đổi / Nhảy Liên Tục Ở Độ Sâu Cao (Atomic Display 100% Chuẩn Extension NNVC)**:
  - **Khắc phục dứt điểm lỗi mũi tên giật / nhảy liên tục ở Depth 8-12 (`StockfishExtension.java`)**:
    - Phân tích nguyên nhân đối chiếu trực tiếp từ Extension (`NNVC/nnvc_main_bundle.js` dòng 6138-6156 & 16474): Trong suốt quá trình engine tính toán (depth 1..12), Extension TUYỆT ĐỐI KHÔNG vẽ mũi tên nào lên màn hình, chỉ lưu kết quả ngầm vào mảng `_playEngineLines`.
    - Khi bàn cờ thay đổi: Extension xóa sạch mũi tên cũ, chờ engine tính toán trọn vẹn và phát `bestmove`, sau đó Extension mới lấy kết quả tốt nhất của toàn bộ nhánh MultiPV và vẽ mũi tên LÊN MÀN HÌNH MỘT LẦN DUY NHẤT (Atomic Display). Mũi tên đứng im vững như bàn thạch, không bao giờ nhảy từ nước này sang nước khác!
    - Trong Mobile trước đây: `StockfishExtension.java` truyền callback `partial -> displayLiveResult(context, fen, partial, false)` vào `StockfishBridge.analyze`. Cứ mỗi 250ms ở các depth 8, 9, 10, 11, 12, engine tìm ra các nước đi khác nhau làm mũi tên bị vẽ lại và nhảy liên tục!
    - Triệt tiêu hoàn toàn việc gọi `displayLiveResult(context, fen, partial, false)` trong callback progress của `StockfishBridge.analyze`.
    - TUYỆT ĐỐI CHỈ GỌI `displayLiveResult(context, fen, result, true)` MỘT LẦN DUY NHẤT sau khi `StockfishBridge.analyze(...)` trả về kết quả cuối cùng hoàn tất (`bestmove`).
  - **Ẩn và làm sạch mũi tên tức thì khi bàn cờ thay đổi hoặc đổi cấu hình (`StockfishExtension.java`)**:
    - Khi có nước cờ mới (`onBoardChanged`), ẩn mũi tên cũ qua `OverlayManager.hideArrowOverlay()`, chờ engine tính xong toàn bộ depth mục tiêu rồi mới vẽ mũi tên mới.
    - Trong `triggerPlayAnalysisOnly()` và `triggerAnalysisForCurrentState()`: Thêm `OverlayManager.hideArrowOverlay()` ngay khi bắt đầu để xóa sạch mũi tên cũ trong lúc phân tích vị trí mới.
  - Bổ sung bộ kiểm thử tự động `SelfTest`: 59/59 test cases vượt qua thành công, bao gồm kiểm tra định dạng nhãn điểm mũi tên mate/cp và scale math.
  - Bump phiên bản lên `v2.0.73` (versionCode `20073`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,755,416 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.73.mpp` (227,447,193 bytes).
  - Cập nhật GitHub Release `v2.0.73` chính thức đính kèm tệp bundle.

### v2.0.72 (2026-10-11)
- **Chuẩn Hóa 100% Eval Bar Chuẩn Extension NNVC, Race Condition & Scale Math Module 1921**:
  - **Khắc phục triệt để lỗi Race Condition đảo ngược điểm số & các dòng UCI cũ trong WASM Worker (`WasmEngineManager.java`)**:
    - Phân tích nguyên nhân: Biến toàn cục tĩnh `currentClassifyWhiteToMove` bị nước đi mới ghi đè trong khi lượt tìm kiếm cũ chưa kịp nhả output. Khi các dòng `info ...` của nước cũ trả về, hệ thống đọc nhầm `whiteToMove` của nước mới dẫn tới đảo ngược dấu điểm số (trắng thành đen và ngược lại). Ngoài ra, các dòng `info ...` và `bestmove` cũ phát sinh trong lúc engine đang dừng (stopping) làm nhảy thanh Eval Bar sai lệch.
    - Gắn phiên bản đánh giá `classifyEvalEpoch` và `whiteToMove` của từng lượt đánh giá độc lập: Mỗi lượt đánh giá được đánh số epoch tăng dần. Mọi dòng UCI output được gắn thẻ epoch và `whiteToMove` tương ứng; nếu epoch khác với `currentClassifyEpoch`, dòng dữ liệu bị hủy bỏ tức thì.
    - Cờ `_isRequestedStop` phân biệt `_workerKind` ('classify' vs 'play'): Trong Web Worker Stockfish 18 JS, chỉ thiết lập `_isRequestedStop = true` cho Classify worker khi nhận lệnh `stop` hoặc khi có lệnh `evaluate` mới. Với Play worker, tuyệt đối không triệt tiêu `bestmove`, ngăn chặn triệt để nguy cơ timeout 5000ms trong `StockfishProcess`.
    - Bổ sung `_currentEvalEpoch = 0` trong `onDoneSearching()`: Triệt tiêu nguy cơ các dòng output sau khi dừng mang epoch cũ.
    - Khóa chặt `lastClassifyScoreEpoch`: `bestmove` chỉ cập nhật Eval Bar khi đã thực sự parse được điểm số thuộc đúng epoch hiện tại, triệt tiêu 100% việc áp dụng điểm số cũ của epoch trước.
    - Loại bỏ `handleClassifyUciLine` khỏi `onEngineOutput`: Chỉ xử lý dòng đánh giá qua `onClassifyEvalOutput` với ngữ cảnh epoch và whiteToMove bất biến.
    - Chuyển lệnh gửi vị trí cờ sang chuẩn Extension: Nếu có danh sách `moves` (bắt đầu từ thế cờ chuẩn), gửi `position startpos moves <move1> <move2> ...` để tận dụng 100% bảng băm hoán vị (Transposition Table) của Hash 64MB; fallback về `position fen <fen>` nếu không có moves hoặc là biến thể tùy chỉnh.
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
    - Khi ván cờ mới bắt đầu (FEN startpos), gọi `MoveClassifier.clearHistory()`, `OverlayManager.resetEvalBar()` và `WasmEngineManager.getInstance(ctx).resetClassifyGame()` gửi `ucinewgame` vào Classify worker để xóa sạch lịch sử ván cũ.
  - Bổ sung bộ kiểm thử tự động `SelfTest`: 54/54 test cases vượt qua thành công, bao gồm kiểm tra công thức Module 1921 scale math, formatScore, và phân tích nước đi startpos hợp lệ.
  - Bump phiên bản lên `v2.0.72` (versionCode `20072`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,754,792 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.72.mpp` (227,446,906 bytes).
  - Cập nhật GitHub Release `v2.0.72` chính thức đính kèm tệp bundle.

### v2.0.71 (2026-10-11)
- **Sửa Triệt Để Mũi Tên Nhảy Depth 12, Điểm Chiếu Hết (#mate thay vì -97), Inverted Mate Line & Độc Lập Eval Bar 100%**:
  - **Khắc phục dứt điểm lỗi mũi tên giật / nhảy liên tục ở Depth 12 (`StockfishProcess.java`)**:
    - Phân tích nguyên nhân: Trong `readSearchOutput`, khi phát progress mỗi 250ms, mã nguồn cũ gọi `new AnalysisResult(Collections.singletonList(firstMoves[0]), ...)` chỉ truyền 1 nước cờ duy nhất, làm xóa sạch nhánh 2 và 3 trong MultiPV và khiến màn hình giật qua lại giữa 1 và 3 mũi tên.
    - Xóa bỏ việc phát progress chỉ có 1 phần tử khi MultiPV > 1. Chỉ phát progress khi `mpv == multiPV` (đã thu thập đủ tất cả các nhánh của độ sâu đó).
    - Signature check trong `StockfishExtension.java`: So khớp danh sách nước cờ và threat move trước khi cập nhật `OverlayManager.updateArrowOverlay`, giữ nguyên mũi tên không bị vẽ lại hay nhấp nháy khi danh sách nước không đổi.
  - **Sửa triệt để lỗi điểm số "-97" và "Mũi tên mạnh nhất lại cho ra đường bị chiếu hết nhanh nhất" (`StockfishProcess.java` & `OverlayManager.java`)**:
    - Nâng cấp `AnalysisResult`: Bổ sung `boolean[] lineMates` và `int[] lineMateIn` lưu đầy đủ trạng thái chiếu hết và khoảng cách mate (từ góc nhìn bên đi cờ) cho toàn bộ các nhánh MultiPV.
    - Áp dụng công thức tính điểm `evalNum` chuẩn Extension để xếp hạng các nhánh MultiPV:
      `evalNum = isMate ? (mateVal > 0 ? (100000L - Math.abs(mateVal) * 100L) : (-100000L + Math.abs(mateVal) * 100L)) : cpVal;`
      Đảm bảo nước chống đỡ được lâu nhất (`m = -3` -> `-99,700`) luôn có điểm cao hơn nước chết ngay (`m = -1` -> `-99,900`), triệt tiêu 100% hiện tượng mũi tên 1 đề xuất nước tự sát.
    - Định dạng nhãn mũi tên chuẩn `_formatArrowEvalLabel` của Extension cho TẤT CẢ các nhánh `i = 0, 1, 2`: Nếu có mate: `mateIn > 0 ? ("#" + mateIn) : ("#-" + Math.abs(mateIn))`. Triệt tiêu hoàn toàn việc hiển thị điểm `-97` hoặc `+97`.
  - **Tách rời 100% Eval Bar khi đổi Engine / Lối chơi Komodo (`WasmEngineManager.java` & `StockfishSettingsDialog.java`)**:
    - Trong JS runtime của `WasmEngineManager`, phân tách `startPlayWorker` và `startClassifyWorker`. Hàm `window.switchPlayEngine` chỉ khởi tạo lại `_playWorker`, tuyệt đối không đụng tới hoặc terminate `_classifyWorker`.
    - Trong `handleClassifyUciLine`, chỉ cập nhật `OverlayManager.updateEvalBar` khi tìm kiếm hoàn tất (`bestmove`) hoặc khi đạt `depth >= 8` với debounce 150ms, triệt tiêu hoàn toàn tình trạng nhảy điểm số ở các độ sâu thấp 1..7.
    - Trong `StockfishSettingsDialog`: Khi người dùng chuyển đổi giữa Komodo 3.3 và Stockfish 18 hoặc thay đổi lối chơi của Komodo (Mặc định / Tấn công / Phòng thủ), gọi `triggerPlayAnalysisOnly()`, không gọi `evaluateClassifyPosition` trên Eval Bar.
    - Cấu hình Option chuẩn cho Komodo Dragon 3.3: Gửi các options được hỗ trợ (`Skill Level`, `Skill Level Maximum Error`, `Skill Level Probability`, `Contempt`, `Hash`, `MultiPV`), không gửi các options không hỗ trợ của Stockfish (`UCI_LimitStrength`, `UCI_Elo`, `UCI_ShowWDL`).
  - Bump phiên bản lên `v2.0.71` (versionCode `20071`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,746,220 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.71.mpp` (227,445,421 bytes).
  - Cập nhật GitHub Release `v2.0.71` chính thức đính kèm tệp bundle.

### v2.0.70 (2026-10-11)
- **Tối Ưu & Sửa Triệt Để 3 Lỗi Kiến Trúc Engine (Stockfish 18 Asyncify, Độc Lập Eval Bar & Move Classifier)**:
  - **Sửa triệt để ngoại lệ Stockfish 18 Classify `RuntimeError: memory access out of bounds` (Asyncify queue)**:
    - Phân tích nguyên nhân: Bản build Emscripten Stockfish 18 WASM kích hoạt cờ Asyncify. Khi WebView gọi `evaluateClassifyPosition` dồn dập (hoặc `stop` chồng lấn lệnh `go`), việc gọi `_sfModule.ccall('command', ...)` re-entrantly trong khi Stack Asyncify chưa unwound hoặc search loop đang thực thi dẫn tới hỏng bộ nhớ WebAssembly và sinh lỗi `RuntimeError: memory access out of bounds`.
    - Triển khai hàng đợi tuần tự hóa `_cmdQueue`, cờ `_isSearching`, `_stopRequested` và bộ theo dõi timeout `_searchWatchdog` (5000ms): Mọi lệnh gửi tới Web Worker (`position`, `go`, v.v.) đều được xếp hàng qua `queueCmd`. Khi search hoàn tất hoặc gặp `bestmove`, hàm `onDoneSearching` gọi `setTimeout(drainQueue, 0)` để chỉ thực thi lệnh tiếp theo khi stack JS hoàn toàn giải phóng.
  - **Tách rời 100% Eval Bar khỏi Play Engine (Độc lập tuyệt đối)**:
    - Loại bỏ hoàn toàn việc gọi `OverlayManager.updateEvalBar(...)` từ `displayLiveResult()` của Play Engine (vốn chạy độ sâu biến thiên 1..12 làm giật/đổi điểm đột ngột).
    - Eval Bar chỉ nhận luồng điểm số thời gian thực duy nhất từ Classify Engine (Stockfish 18 WASM chuẩn Extension: Depth 10, MultiPV 3, Hash 64).
    - Kích hoạt phân tích Eval Bar trực tiếp trong `onBoardChanged(boardView, fen)` qua `evaluateClassifyPosition(fen)`.
  - **Tách rời Phân Loại Nước Đi (Torch CEE Move Classifier) & Chống Drop Kết Quả Khi Đối Thủ Đi Nhanh**:
    - Chạy độc lập phân loại nước cờ `classifyMoveDebounced(context, fen)` với bộ đệm Handler 100ms ngay khi bàn cờ thay đổi, không còn bị xích hay chờ đợi luồng `scheduleAnalysis` / `StockfishBridge.analyze`.
    - **Khắc phục race condition chụp snapshot nước đi (`snapshotMoves`)**: Khi người chơi đi cờ và đối thủ phản hồi siêu tốc (<100ms), `classifyMoveDebounced` lập tức chụp bản sao bất biến `snapshotMoves = new ArrayList<>(currentPositionMoves)` ngay thời điểm phát sinh nước cờ. Khi Runnable thực thi sau 100ms, nó sử dụng chính xác danh sách nước cờ đã snapshot, triệt tiêu nguy cơ bị nước cờ của đối thủ ghi đè và làm mất phân loại (Brilliant/Great Move) của người chơi.
    - **Đồng bộ hóa hàng đợi Request ID (`_reqIdQueue` & `pendingCallbacksMap`) trong TorchEngine**: Bổ sung hàng đợi `_reqIdQueue` trong JavaScript và `ConcurrentHashMap<Long, TorchClassificationCallback> pendingCallbacksMap` trong Java. Đảm bảo mỗi kết quả `json ...` trả về từ Torch Worker ánh xạ chính xác 1:1 với đúng yêu cầu phân loại tương ứng, triệt tiêu tình trạng lệch thứ tự hoặc rò rỉ callback khi mảng positions rỗng hoặc xảy ra lỗi parse.
    - Debounce điều khiển độ sâu SeekBar trong `StockfishSettingsDialog`: Chuyển kích hoạt phân tích bàn cờ từ `onProgressChanged` sang `onStopTrackingTouch` (`onEloCommitted` / `onValueCommitted`), ngăn chặn hoàn toàn việc gửi hàng chục lệnh phân tích dồn dập khi người dùng kéo thanh trượt.
  - Bump phiên bản lên `v2.0.70` (versionCode `20070`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,741,028 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.70.mpp` (227,443,368 bytes).
  - Cập nhật GitHub Release `v2.0.70` chính thức đính kèm tệp bundle.

### v2.0.69 (2026-10-11)
- **Sửa Triệt Để Lỗi Classify Stockfish 18 WASM Worker (Failed to Fetch)**:
  - **Khắc phục dứt điểm ngoại lệ `TypeError: Failed to fetch` trong Classify Worker:**
    - Phân tích nguyên nhân: Trong Web Worker chạy trên WebView Android (tạo từ blob URL), khi khởi tạo Stockfish 18 WASM cho luồng Classify (Eval Bar và phân tích phụ), mã nguồn JavaScript Emscripten của Stockfish tự động gọi hàm `fetch('stockfish.wasm')` hoặc kiểm tra `typeof onmessage`, gây ra ngoại lệ `TypeError: Failed to fetch` và làm sập tiến trình worker.
    - Triển khai bộ đệm hoàn chỉnh `WasmAwareXHR` và Mock `fetch()` đồng bộ ngay bên trong Worker Preamble: Khi Emscripten hoặc Web Worker cố gắng tải tệp `.wasm`, hệ thống lập tức hoàn trả dữ liệu nhị phân `_injectedWasm` đã được nạp sẵn từ bộ nhớ (ArrayBuffer) dưới dạng `Response` hoặc XHR `response`, triệt tiêu hoàn toàn nhu cầu gọi network request.
    - Đóng gói mã nguồn Stockfish 18 JavaScript trong IIFE khép kín với tham số che giấu biến `onmessage`, bảo vệ luồng `self.onmessage` không bị ghi đè ngoài ý muốn.
    - Khởi tạo đúng chuẩn `_sfInit({ wasmBinary: _injectedWasm })`, xử lý mượt mà cả đối tượng Promise trả về hoặc thuộc tính `.ready`, phát tín hiệu `__WORKER_READY__` và kết nối liền mạch các lệnh `uci`, `isready`.
  - Bump phiên bản lên `v2.0.69` (versionCode `20069`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,734,972 bytes, header `dex\035\0`) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.69.mpp` (227,441,939 bytes).

### v2.0.68 (2026-10-11)
- **100% Offline Local Engine Extraction & Bảo Mật Tuyệt Đối (Không Lộ Mã Nguồn / URL Repo)**:
  - **Đóng Gói Trực Tiếp Toàn Bộ File WebAssembly Vào APK Qua Resource Patcher:**
    - Khắc phục triệt để lỗi "No WASM binaries present yet on disk": Xây dựng `StockfishResourceCopier` tích hợp trực tiếp vào bytecode của `stockfishResourcePatch`. Khi Morphe Manager thực hiện vá APK, toàn bộ tài nguyên WebAssembly (`komodo/dragon3.3.wasm`, `komodo/komodo.js`, `komodo/book.bin`, `stockfish18/stockfish-18-lite-single.wasm`, `stockfish18/stockfish-18-lite-single.js`, `torch/torch.wasm`, `torch/torch.js`) được sao chép thẳng vào thư mục `assets/` của APK Chess.com.
    - Cải tiến `WasmEngineManager`: Bổ sung cơ chế quét đa tầng `extractBundledWasmFiles` (quét trực tiếp các mục trong tệp APK qua `context.getPackageResourcePath()`, mở trực tiếp qua `context.getAssets().open()`, và quét bộ nhớ máy nếu có). Động cơ được giải nén sẵn sàng ngay trong lần đầu khởi chạy mà không cần tải thêm bất kỳ file nào từ internet.
  - **Bảo Mật Tuyệt Đối & Loại Bỏ Hoàn Toàn Nguy Cơ Rò Rỉ Mã Nguồn:**
    - Xóa bỏ 100% các URL GitHub cá nhân / kho lưu trữ mã nguồn trong toàn bộ mã nguồn extension (`WasmEngineManager`, `TorchEngine`, `AccuracyEloWidgetView`, `ArrowOverlayView`, `EloScanner`, `PlayerAccuracyPillView`, `SoundManager`), không còn xuất hiện trong chuỗi string, file log debug `nnvc_debug.log` hay màn hình hiển thị HUD.
    - Loại bỏ tiến trình tải nền qua mạng gây ra lỗi HTTP 404 FileNotFoundException; chuyển sang kiến trúc 100% ngoại tuyến (offline-first).
  - Bump phiên bản lên `v2.0.68` (versionCode `20068`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,731,908 bytes) và DEX gốc `classes.dex` (95,736 bytes), đóng gói thành công `patches-2.0.68.mpp` (227,441,697 bytes).

### v2.0.67 (2026-10-10)
- **100% WebAssembly Chess Engine Chuẩn NNVC Extension (Zero Native Subprocess & Zero Fallback)**:
  - **Khắc phục dứt điểm lỗi khởi động WebAssembly Engine trên Mobile:**
    - Tuyệt đối tuân thủ chỉ thị người dùng: Loại bỏ 100% subprocess native Stockfish cũ (`libstockfish.so`) và triệt tiêu hoàn toàn mọi cơ chế fallback.
    - **Komodo Dragon 3.3 WASM làm mặc định:** Tự động nạp `dragon3.3.wasm` (16.27 MB), `komodo.js` (177 KB) và sách khai cuộc `book.bin` (2.8 MB). Xây dựng cơ chế `WasmAwareXHR` đồng bộ chuẩn 100% trong Worker, phản hồi `uciok` và `readyok` tức thì (<100ms), nạp sách khai cuộc chuẩn xác.
    - **Stockfish 18 WASM:** Khởi tạo instance trực tiếp từ Web Worker qua factory với `wasmBinary` từ bộ nhớ và stream output thời gian thực, phục vụ phân tích ván đấu (Play Engine) và thanh đánh giá Eval Bar (Classify Engine).
    - Tự động di chuyển (migrate) cấu hình cũ sang `komodo`.
    - Cơ chế đồng bộ hóa `__WORKER_READY__` và hàng đợi lệnh không mất mát (`_queuedCmds`), triệt tiêu hoàn toàn nguy cơ rớt lệnh `uci` hoặc treo app.
  - **Bảo toàn Hệ Thống Logger Tạm Thời & Engine Status HUD:**
    - Tiếp tục hỗ trợ `NnvcLogger` (ghi log chi tiết ra `/sdcard/Android/data/com.chess/files/nnvc_debug.log`), hộp thoại đọc log in-app `NnvcLogViewerDialog`, nút huy hiệu nổi kéo thả `FloatingLogPillView` và thông báo trạng thái `EngineStatusHUD`.
  - Bump phiên bản lên `v2.0.67` (versionCode `20067`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,731,004 bytes), đóng gói thành công `patches-2.0.67.mpp` (227,435,479 bytes).

### v2.0.66 (2026-10-10)
- **Khắc Phục Triệt Để Lỗi Tê Liệt Tính Năng (v2.0.65) & Bổ Sung Hệ Thống Logger Tạm Thời + Engine Status HUD**:
  - **Khắc phục triệt để lỗi không có tính năng nào hoạt động:**
    - Chuyển sang kiến trúc Hybrid Dual-Engine: Khôi phục và ưu tiên Stockfish Native (`libstockfish.so`) khởi động tức thì (<50ms), hoàn toàn độc lập, ngoại tuyến 100%, không bị phụ thuộc vào tệp WASM tải ngoài.
    - Cơ chế Fallback an toàn tuyệt đối: Nếu người dùng chọn engine WebAssembly (Komodo 3.3 hoặc Stockfish 18) nhưng tệp WASM chưa được nạp/tải hoặc Worker không phản hồi, hệ thống lập tức chuyển sang Native Stockfish với 0ms chờ đợi, triệt tiêu hoàn toàn tình trạng treo 75 giây.
    - Khôi phục 100% hoạt động của các tính năng: Mũi tên gợi ý Best Moves, Threat Arrows, Eval Bar thời gian thực, Phân loại nước cờ Brilliant/Great Move (Torch CEE) và Widget Accuracy / Elo kéo thả.
  - **Hệ Thống Nhật Ký Hoạt Động Tạm Thời (NnvcLogger & In-App Viewer):**
    - `NnvcLogger`: Ghi log đa luồng chi tiết thời gian thực ra tệp `/sdcard/Android/data/com.chess/files/nnvc_debug.log` (hoặc `context.getExternalFilesDir(null)/nnvc_debug.log`). Dễ dàng truy cập qua bất kỳ trình quản lý tệp nào (ZArchiver, v.v.).
    - Giao diện đọc log trực tiếp trong ứng dụng (`NnvcLogViewerDialog`): Thêm nút biểu tượng `[📜]` trên header Menu Cài Đặt và thẻ Nhật ký trong Tab Engine. Hỗ trợ xem log cuộn mượt mà font monospace, nút "📋 Sao Chép" vào bộ nhớ tạm và "🗑️ Xóa Log".
    - Huy hiệu nổi di động (`FloatingLogPillView`): Nút pill `[📜 LOG]` nhỏ gọn kéo thả tự do trên màn hình ván đấu để mở nhanh nhật ký chẩn đoán bất cứ lúc nào.
  - **Chỉ Báo Trạng Thái Nạp Engine (EngineStatusHUD):**
    - Hiển thị HUD thông báo trạng thái nạp engine (ví dụ: `[⚡ Stockfish Native Sẵn Sàng]`), triệt tiêu cảm giác ứng dụng bị đơ khi nạp động cơ.
  - Bump phiên bản lên `v2.0.66` (versionCode `20066`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,720,792 bytes), đóng gói thành công `patches-2.0.66.mpp` (227,433,088 bytes).

### v2.0.65 (2026-10-10)
- **Chuyển Đổi 100% Stockfish & Komodo Sang WebAssembly Chuẩn Extension NNVC**:
  - **Stockfish 18 WebAssembly Engine:**
    - Thay thế 100% binary native cũ `libstockfish.so` bằng `stockfish-18-lite-single.wasm` (7.29 MB) và `stockfish-18-lite-single.js` (20 KB) chạy trên headless WebView Web Worker độc lập.
    - Sửa triệt để lỗi Regex syntax error trong JS bootstrap khi nạp `stockfish-18-lite-single.js`, đảm bảo Worker khởi tạo và nạp tệp WASM thành công 100%.
    - Cơ chế hàng đợi lệnh khởi tạo (`_playPendingCmds`, `_classifyPendingCmds`) và đồng bộ `waitForPlayWorkerReady`, triệt tiêu hoàn toàn nguy cơ rớt lệnh `uci` và tình trạng đơ 60 giây khi khởi động.
  - **Eval Bar Engine Chuyên Trách (Classify Kind):**
    - Chạy riêng instance `kind: "classify"` của Stockfish 18 WASM với options chuẩn Extension: `Hash = 64`, `Threads = 1`, `MultiPV = 3`, `Depth = 10`.
    - Kết nối luồng đánh giá thời gian thực `evaluateClassifyPosition` cho từng FEN / nước đi, stream trực tiếp đến `OverlayManager.updateEvalBar`.
    - Bộ lọc `_stabilizeWhiteEval` với buffer 5 mẫu median, ngưỡng delta 14, adaptive alpha 0.16..0.72 và dải cp chuẩn hóa `[-500, +500]`.
    - Sửa lỗi tỷ lệ checkmate trên Eval Bar: khi có nước chiếu hết (`hasMate = true`), hiển thị trọn vẹn 100% (White) hoặc 0% (Black) thay vì bị kẹp sai lệch ở 86%.
  - **Komodo Dragon 3.3 WASM Engine:**
    - Tích hợp 100% Komodo Dragon 3.3 WASM (`dragon3.3.wasm` 16.27 MB + `komodo.js` 177 KB + `book.bin` 2.8 MB) khi người dùng chọn Komodo 3.3 trong Cài đặt.
  - **Khóa Độ Sâu Tối Đa:**
    - Khóa cứng `MAX_DEPTH = 12` (thang đo [1, 12], mặc định 10).
  - Bump phiên bản lên `v2.0.65` (versionCode `20065`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 42 file Java Extension thành `extension.mpe`, đóng gói thành công `patches-2.0.65.mpp` (227,420,883 bytes).

### v2.0.64 (2026-10-10)
- **Loại Bỏ Hoàn Toàn Rung Khi Sai Lầm & Bổ Sung Hiệu Ứng + Âm Thanh Nước Đi Brilliant / Great**:
  - **Loại bỏ triệt để rung khi mắc sai lầm:**
    - Xóa bỏ 100% rung haptic khi mắc lỗi (Blunder / Mistake / Inaccuracy).
    - Dọn dẹp switch rung sai lầm trong hộp thoại cài đặt HLV (`StockfishSettingsDialog.java`), khóa vĩnh viễn cấu hình `isBlunderAlertsEnabled()` về `false`.
  - **Hiệu ứng đồ họa vinh danh nước đi Brilliant (Cyber Radiance):**
    - Sóng hào quang kép lan tỏa (Dual Expanding Ripples, màu `#26C2A3` và `#64FFDA`) phát ra từ tâm ô đích trong chu kỳ hoạt ảnh 800ms mượt mà (`postInvalidateOnAnimation()`).
    - Ngôi sao tỏa tia 6 cánh sắc nét (6-point celebratory sparkle rayburst, màu `#FFFFFF`) và mấu góc lấp lánh (Corner Diamond Shimmer) bao quanh huy hiệu và khung tiêu điểm.
    - Tuyệt đối bảo tồn nguyên tắc Zero-Tint: Không lem bất kỳ lớp màu nào vào tâm quân cờ.
  - **Âm thanh nước cờ xuất sắc chuẩn Chess.com (Native Audio Delight):**
    - Xây dựng `SoundManager` với bộ đệm `SoundPool` độ trễ cực thấp, fallback sang `MediaPlayer`. Tự động phát âm thanh reo vui `sounds/brilliant.mp3` nguyên bản của Chess.com ngay khi nước cờ Brilliant xuất hiện.
  - Bump phiên bản lên `v2.0.64` (versionCode `20064`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 41 file Java Extension thành `extension.mpe`, đóng gói thành công `patches-2.0.64.mpp` (189,676,741 bytes).
  - Cập nhật GitHub Release `v2.0.64` chính thức đính kèm tệp bundle.

### v2.0.63 (2026-10-10)
- **Khắc Phục Triệt Để Lỗi Nhiễm Màu / Lem Màu Quân Cờ Khi Phân Loại Nước Đi**:
  - **Loại bỏ 100% lớp phủ nền (Zero Tint Fill) ở ô đích**: Khắc phục dứt điểm nhược điểm trước đây vẫn vẽ fill alpha ~10% lên toàn bộ ô cờ khiến các quân cờ trắng/đen bị ám màu đỏ/hồng (Blunder) hay xanh (Best). Tâm ô đích giờ đây hoàn toàn trong suốt 100%, sprite quân cờ Chess.com giữ trọn độ trong trẻo, sắc nét và nguyên bản tuyệt đối.
  - **Khung Viền Kép Cao Cấp (Dual-Layer Ambient Halo & Crisp Focus Frame)**:
    - Ô đích (`toSquare`): Bao quanh bởi lớp viền hào quang ngoài (`sqSize * 0.055f`, alpha 80) tạo ánh sáng dịu mắt, kết hợp khung tiêu điểm trong sắc nét (`sqSize * 0.032f`, alpha 235) với 4 góc bo tròn mềm mại (`cornerRadius = 8%`). Hiệu ứng hào quang mở rộng rực rỡ cho nước cờ Brilliant / Great Move mà tuyệt đối không lem vào thân quân cờ.
    - Loại bỏ sự va chạm / che khuất giữa mấu góc chữ L trước đây và huy hiệu badge ở góc trên-phải.
    - Ô xuất phát (`fromSquare`): Thể hiện bằng viền nét đứt thanh nhã (`DashPathEffect`) và lớp phủ nền siêu nhẹ (alpha 18) vì ô cờ này hoàn toàn trống.
    - Bộ nhớ Canvas tối ưu: Tái sử dụng pre-allocated `RectF`, cache `DashPathEffect`, zero object allocation trong `onDraw()`.
  - Bump phiên bản lên `v2.0.63` (versionCode `20063`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 40 file Java Extension thành `extension.mpe`, đóng gói thành công `patches-2.0.63.mpp` (189,674,525 bytes).
  - Cập nhật GitHub Release `v2.0.63` chính thức đính kèm tệp bundle.

### v2.0.62 (2026-10-10)
- **Nâng Cấp Giao Diện Highlight Ô Cờ Phân Loại Nước Đi (Cyber Frame & Corner Brackets)**:
  - Khắc phục triệt để lỗi quân cờ bị nhiễm màu / lem màu: Thay thế lớp phủ bán trong suốt đặc trước đây (đè lên sprite quân cờ) bằng thiết kế **Cyber Frame & Corner Brackets** chuẩn HUD công nghệ cao.
  - 4 góc ô cờ được tô điểm bởi mấu góc chữ L sắc nét (`bracketLen = 22%`, viền góc `3.5%` bo tròn đầu), viền ngoài thanh mảnh `1.5dp` ôm trọn ô cờ và hiệu ứng phát sáng ngoài nhẹ nhàng cho nước cờ Brilliant / Great Move.
  - Lớp nền bên trong ô cờ chỉ giữ độ mờ siêu nhẹ (6% cho ô xuất phát, 10-12% cho ô đích), giữ cho quân cờ Chess.com bên dưới hoàn toàn nguyên bản, trong trẻo và sắc nét 100%.
  - Bump phiên bản lên `v2.0.62` (versionCode `20062`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 40 file Java Extension thành `extension.mpe`, đóng gói thành công `patches-2.0.62.mpp` (189,674,389 bytes).
  - Cập nhật GitHub Release `v2.0.62` chính thức đính kèm tệp bundle.

### v2.0.61 (2026-10-10)
- **Tinh Chỉnh Widget Accuracy / Elo: Nhỏ Gọn & Sắc Nét Tuyệt Đối**:
  - Tinh chỉnh kích thước widget thu nhỏ ~12-15% (kích thước tối ưu 164x172dp so với 186x194dp ban đầu), thanh header mini 32dp khi thu gọn, mở rộng tối đa tầm quan sát bàn cờ mà không che khuất các quân hay mũi tên nước đi.
  - Sửa triệt để lỗi mờ chữ huy hiệu `[BẠN]` / `[YOU]`: Khắc phục tận gốc lỗi rò rỉ trạng thái alpha (`0x2EFFFFFF` từ thẻ đối thủ rò rỉ sang paint thẻ người chơi khiến độ mờ bị giảm còn 18%). Chuyển sang nền Cyber Blue nguyên khối `#0A84FF` đặc 100% opaque, bổ sung viền Cyber Cyan tương phản cao `#64D2FF`, chữ màu trắng tinh in đậm (`#FFFFFF`, bold) và áp dụng `setFakeBoldText(true)` giúp các dấu thanh tiếng Việt (như dấu nặng trong chữ `Ạ`) nổi bật và sắc nét tuyệt đối trên nền thẻ quân trắng.
  - Cập nhật ngưỡng chạm mở rộng / thu gọn `touchY <= 32dp` khớp chính xác với chiều cao header mới.
  - Bump phiên bản lên `v2.0.61` (versionCode `20061`) trong `patches-bundle.json` và `patches-list.json`.
  - Biên dịch sạch 40 file Java Extension thành `extension.mpe`, đóng gói thành công `patches-2.0.61.mpp` (189,673,974 bytes).
  - Cập nhật GitHub Release `v2.0.61` chính thức đính kèm tệp bundle.

### v2.0.60 (2026-10-10)
- **Khắc phục lỗi nguồn Morphe Manager (!) & Chuẩn hoá Bundle**:
  - Chuẩn hoá triệt để `patches-bundle.json`: Loại bỏ hoàn toàn các trường thừa (`versionCode`, `downloadUrl`), chỉ giữ 5 trường chuẩn schema của Morphe Manager (`created_at`, `description`, `download_url`, `signature_download_url`, `version`). Khắc phục triệt để lỗi parse JSON gây crash / `State.Failed` hiển thị chấm than đỏ `(!)` và "Số bản vá: 0".
  - Bump phiên bản lên `v2.0.60` theo đúng quy tắc sống còn trong `rule.md` để reset cache Morphe Manager và strike ledger của `PatchBundleLoadGuard`.
  - Cập nhật đồng bộ `patches-list.json` sang version `2.0.60`.
  - Biên dịch sạch 40 file Java Extension thành `extension.mpe` (DEX `dex\035\0`), đóng gói `patches-2.0.60.mpp` (189,674,046 bytes).
  - Tạo GitHub Release `v2.0.60` chính thức với tệp bundle đính kèm.

### v2.0.59 (2026-10-10)
- **Widget Độ Chính Xác (%) & Elo Trực Tiếp Chuẩn Extension**:
  - Chuyển đổi toàn bộ hiển thị Độ Chính Xác (%) và Estimated Elo từ 2 thanh pill dính mép sang **Widget nổi kéo thả tự do (`AccuracyEloWidgetView`)** chuẩn 100% theo giao diện của NNVC Extension.
  - Thiết kế Cyber Glass cao cấp (frosted dark theme `rgba(13, 17, 23, 0.94)`, viền vi hạt, icon cờ vua âm dương đặc trưng).
  - Phân chia 2 thẻ Trắng và Đen riêng biệt, nhận diện bên người chơi `[BẠN]` / `[YOU]` với viền phát sáng Cyber Blue `#0A84FF` và bên đối thủ `[ĐỐI THỦ]` / `[OPPONENT]`.
  - Hỗ trợ kéo thả (drag & drop) tự do toàn màn hình với hiệu ứng scale 1.02x / alpha 0.95x, tự động kẹp biên an toàn (boundary clamping) và lưu toạ độ vào `SharedPreferences` (`accuracy_widget_x`, `accuracy_widget_y`). Khắc phục hoàn toàn hiện tượng giật vị trí khi engine liên tục stream kết quả phân tích.
  - Hỗ trợ nút thu gọn / mở rộng (collapse / expand) trên header: khi thu gọn chỉ còn thanh header mini 36dp giúp thoáng tầm nhìn bàn cờ, tự động re-clamp chống tràn đáy màn hình khi mở rộng.
  - Tối ưu bộ nhớ Canvas: tái sử dụng toàn bộ pre-allocated Paint fields, loại bỏ hoàn toàn cấp phát đối tượng trong `onDraw()`.
  - Tự động kẹp biên an toàn khi xoay màn hình (orientation change / resize) thông qua `OnLayoutChangeListener`.
  - Đồng bộ đa ngôn ngữ 100% Anh / Việt theo `I18n`.
  - Đã biên dịch sạch 40 file Java, đóng gói thành công `patches-2.0.59.mpp` (189,674,047 bytes).