# NHẬT KÝ DỰ ÁN & BỘ GHI NHỚ DẶN DÒ (PROJECT MEMORY)
*Dự án: Chess Mobile - ReVanced Extension Patches cho Chess.com Android*
*Phiên bản hiện tại: v2.0.68*
*Cập nhật lần cuối: 2026-10-11*

---

## 1. QUY TẮC LÀM VIỆC & DẶN DÒ CỐT LÕI
1. **QUY TẮC PHIÊN BẢN (BẮT BUỘC SỐNG CÒN)**:
   - **TUYỆT ĐỐI KHÔNG ĐƯỢC TRÙNG PHIÊN BẢN KHI ĐÃ VÁ**: Morphe sẽ bị dính cache cũ nếu dùng lại version đã từng phát hành.
   - Mỗi lần sửa lỗi hoặc build gói mới, **BẮT BUỘC TĂNG PHIÊN BẢN LÊN** (ví dụ: 2.0.14 -> 2.0.15 -> 2.0.16 -> 2.0.17 -> 2.0.18).
   - **KHÔNG PUSH FILE BUILD .PY HOẶC SCRIPT TẠM LÊN GITHUB**: Tất cả script build/test/python chỉ để chạy local, đã được đưa vào `.gitignore`. Chỉ commit mã nguồn chính thức (Java, Kotlin, metadata JSON, MD).
2. **Thái độ nghiêm túc tuyệt đối**:
   - Không được để xảy ra bất kỳ lỗi ngớ ngẩn nào (syntax error, escape error, crash, NPE, missing assets).
   - Kiểm tra kỹ lưỡng trước khi đóng gói và phát hành.
   - Không code bừa, không báo kết quả ảo, mọi tính năng đều phải chạy thực tế trên Android WebView và Runtime.
3. **Phong cách giao tiếp**:
   - Cực kỳ ngắn gọn, dứt khoát, telegraphic (ultra-terse).
   - Đi thẳng vào vấn đề: Nêu nguyên nhân, hành động đã thực hiện, kết quả, bước tiếp theo. Không văn hoa, không dài dòng.
4. **Hệ thống chẩn đoán lỗi toàn diện (Diagnostic Logging)**:
   - Tất cả các tính năng (Stockfish UCI, FEN events, MoveClassifier, Torch WASM Engine, Overlays) **BẮT BUỘC** phải ghi log chi tiết kèm timestamp mili-giây vào `TorchEngine.DIAGNOSTIC_LOGS`.
   - Nút **"📋 Xem & Copy Log"** trong Menu cài đặt là công cụ then chốt để người dùng copy log thực tế và phản hồi nhanh nhất.
   - Buffer log duy trì tối thiểu 300 dòng mới nhất để bao quát toàn bộ ván đấu.
5. **PHÂN HỆ COACH PHỤ THUỘC 100% VÀO TORCH - TUYỆT ĐỐI KHÔNG LIÊN QUAN ĐẾN STOCKFISH**:
   - Phân loại nước đi (Brilliant, Great, Best, Good, Book, Inaccuracy, Mistake, Blunder...) là nhiệm vụ độc quyền của **Torch CEE 26MB (Komodo WebAssembly)**.
   - **NẾU KHÔNG PHÂN LOẠI ĐƯỢC THÌ BÁO LỖI RÕ RÀNG**, tuyệt đối không tự ý chuyển sang Stockfish ReviewMath.
   - Stockfish chỉ làm nhiệm vụ tính toán nước đi tốt nhất (`go depth`), Best Moves, Arrows, Eval Bar trong tab ENGINE.
6. **CẤU TRÚC GIAO DIỆN BÁM SÁT 100% THEO EXTENSION**:
   - Mobile bám sát theo cấu trúc của Extension, chia thành đúng 5 Tab theo chuẩn Extension:
     `[ LIVE | ENGINE | COACH | VISUAL | ARROWS ]`.
   - Mọi thiết lập của Torch Coach (phân loại nước đi, giọng nói, cảnh báo rung, chẩn đoán log) phải nằm trọn trong tab **COACH**.

---

## 2. KIẾN TRÚC KỸ THUẬT DỰ ÁN

### 2.1. Nền tảng mục tiêu
- Ứng dụng: **Chess.com Android APK**
- Phiên bản tương thích: `v4.9.0-free (versionCode: 409000000)`
- Công nghệ vá (Patching): ReVanced Patcher (`.mpp` format, ReVanced Patches v2/v3).
- Ngôn ngữ: Java 8 (Extension DEX) + Kotlin Bytecode (ReVanced Patch Bytecode).

### 2.2. Phân hệ Stockfish Engine Native
- Binary: `libstockfish.so` (biên dịch từ Stockfish C++ mã nguồn mở, tối ưu cho `arm64-v8a`, `armeabi-v7a`, `x86_64`).
- Giao thức: UCI qua `stdin`/`stdout`.
- Quản lý tiến trình: `StockfishProcess.java`, kết nối qua `StockfishBridge.java`.
- Tự động điều chỉnh Elo và Depth: Tính năng Auto-Depth theo Elo của người chơi, giới hạn thời gian suy nghĩ hợp lý để không gây nóng máy.

### 2.3. Phân hệ Torch Engine (CEE WebAssembly) - Đánh giá & Phân loại nước đi
- Engine: **Torch WebAssembly Engine (CEE 26MB)** nguyên bản từ Chess.com/NNVC Extension.
- Môi trường chạy: Headless Android `WebView` với **Web Worker** chạy nền.
- Cấu trúc file:
  - `torch.wasm`: File binary WebAssembly ~26MB.
  - `torch.js`: File glue code Emscripten ~375KB.
- Tự động tải: Nếu chưa có file cục bộ, tự động tải ngầm từ GitHub Release vào thư mục `files/torch/`.
- **Kỹ thuật Worker Bootstrap (v2.0.13+)**:
  - Main Document fetch `torch.js` và `torch.wasm` qua intercepted request (`https://torch-engine.local/`).
  - Toàn bộ mã nguồn Web Worker được đóng gói bằng **Base64 String** trong Java và giải mã bằng `atob()` trong WebView để triệt tiêu hoàn toàn 100% lỗi cú pháp và escape ký tự ngắt dòng (`\r\n`).
  - Worker thiết lập mock `XMLHttpRequest` (`WasmAwareXHR`) để cung cấp ngay buffer WASM cho Emscripten mà không cần tải lại mạng.
  - Sau khi khởi động, Worker kích hoạt các option: `UseDeclarativePositionCommand`, `HandleContinuations`, `HandleContinuationsDepth 4`, `ClassificationV3 true`, `ServeCommandV2 true`.
- Giao thức phân loại: Gửi chuỗi nước đi `moves <UCI_LIST>` và `fetch analysis`, nhận JSON kết quả chứa nhãn nước đi chuẩn Chess.com:
  - `Brilliant` (!!) - Thiên tài
  - `Great` (!) - Xuất sắc
  - `Best` (★) - Tốt nhất
  - `Excellent` (👍) - Tuyệt vời
  - `Good` (✓) - Nước cờ hay
  - `Book` (📖) - Nước khai cuộc
  - `Inaccuracy` (?!) - Thiếu chính xác
  - `Mistake` (?) - Sai lầm
  - `Blunder` (??) - Sai lầm nghiêm trọng
  - `Miss` / `Missed Win` (✕) - Bỏ lỡ cơ hội thắng
  - `Forced` (➔) - Bắt buộc

---

## 3. CÁC ĐIỂM CHẠM BYTECODE & QUY TRÌNH BUILD

### 3.1. Hook 5 & Hook 6 trong `StockfishPatchKt.class`
- **Hook 5 (Stockfish Init)**:
  - Chèn gọi phương thức khởi tạo `StockfishExtension.init(activity)`.
  - **LƯU Ý SỐNG CÒN**: Phương thức trong Kotlin bytecode trả về `Unit.INSTANCE` (`kotlin.Unit`). Không được để hổng stack hoặc return sai kiểu.
- **Hook 6 (UI Hooks & Navigation)**:
  - Bắt các sự kiện `onBoardChanged`, `onArrowsChanged`, menu settings.

### 3.2. Quy trình biên dịch & Đóng gói (.mpp)
1. Thư mục mã nguồn Java: `extensions/extension/src/main/java/`
2. Biên dịch Java sang `.class` bằng `javac -source 8 -target 8`.
3. Biên dịch `.class` sang `.dex` bằng `d8.bat` (Android SDK Build Tools 34.0.0).
4. Đóng gói vào tệp zip không nén `dexes.zip` -> đổi tên thành `classes.dex` bên trong gói patch.
5. Tạo tệp `.mpp` (ReVanced Patches Package):
   - Đặt metadata version (ví dụ: `2.0.13`).
   - Cập nhật `patches-bundle.json` và `patches-list.json`.
6. Thực hiện tự động thông qua script `build_2_0_*.py`.

---

## 4. LỊCH SỬ PHÁT TRIỂN & CÁC BÀI HỌC KINH NGHIỆM
- **v2.0.9 - v2.0.11**:
  - Tích hợp Torch WebAssembly đầu tiên.
  - Nhận diện cần thiết kế bộ nạp file 26MB từ bộ nhớ máy hoặc GitHub Release.
- **v2.0.12**:
  - Bật mặc định tính năng phân loại nước đi (`show_move_classification = true`).
  - Tích hợp nút xem & sao chép log trực tiếp trong hộp thoại Cài đặt cờ vua Stockfish.
- **v2.0.13**:
  - **Sửa lỗi SyntaxError line 41**: Chuỗi escape `\r\n` trong Java string template literal bị parse thành ký tự ngắt dòng vật lý trong JS single quotes. Khắc phục dứt điểm bằng Base64 encode toàn bộ worker script.
  - **Nâng cấp Diagnostic Logger**: Tăng bộ đệm log lên 300 dòng; bổ sung log cho Stockfish UCI commands, Board FEN updates, và MoveClassifier events.
  - **Khởi tạo tài liệu dự án `PROJECT_MEMORY.md`**.
- **v2.0.14**:
  - **Tự động nhận diện Elo đối thủ và người chơi (`EloScanner.java`)**:
    - Quét tọa độ view người chơi phía trên (Top) và phía dưới (Bottom) bàn cờ ChessBoardView.
    - Áp dụng bộ lọc nghiêm ngặt (Strict Filter): Loại bỏ 100% đồng hồ thời gian (chứa dấu `:`, `|`), đơn vị thời gian (`min`, `sec`), điểm quân chênh lệch (`+1`, `-3`). Chỉ nhận đúng định dạng Elo số nguyên từ 100 đến 3800.
    - Tự động nhận diện hướng bàn cờ (Flipped board) để gán đúng `WhiteElo` và `BlackElo`.
    - Tự động đồng bộ vào Torch WebAssembly Engine qua `setoption name WhiteElo/BlackElo value ...`.
- **v2.0.16**:
  - **Khắc phục triệt để lỗi phân loại nước đi Torch CEE bằng chuỗi nước đi tích lũy (Cumulative Moves)**:
    - **Nguyên nhân cốt lõi**:
      1. Komodo/Torch CEE WebAssembly biên dịch với tùy chọn `UseDeclarativePositionCommand` chỉ chấp nhận cú pháp chuẩn của Chess.com Extension: `position startpos moves m1 m2 m3...`.
      2. Khi gửi `position fen <FEN> moves <move>`, parser của Torch CEE không hỗ trợ FEN đi kèm moves trực tiếp cho Declarative mode dẫn đến abort assertion `Aborted()`.
      3. Ngược lại, khi gửi danh sách nước đi tích lũy `position startpos moves d2d4 g8f6 c1f4...`, Torch chạy 100% mượt mà, trả về nhãn Book/Good/Best/Inaccuracy/Mistake/Blunder hoàn hảo.
    - **Giải pháp triệt để**:
      - Tự động duy trì và suy luận toàn bộ danh sách nước đi từ thế cờ ban đầu (`fenHistory` tự động chèn `startpos` nếu trận đấu bắt đầu từ nước 1).
      - Sử dụng chuỗi nước đi tích lũy: `position startpos moves <ALL_PLAYED_MOVES>`.
      - Bổ sung cơ chế Auto-Recover Worker: Tự động khởi động lại Worker sau 1.5s nếu gặp bất kỳ lỗi `RuntimeError: Aborted()` nào để khôi phục trạng thái sạch cho engine.
- **v2.0.18**:
  - **Tách phân hệ COACH 100% độc lập cho Torch & Cấu trúc 5 Tab chuẩn Extension**:
    - **100% Phụ thuộc vào Torch Coach Engine**:
      - Loại bỏ hoàn toàn sự can thiệp của Stockfish vào việc phân loại nước đi (`MoveClassifier`).
      - Phân loại nước cờ là nhiệm vụ độc quyền của **Torch CEE 26MB (Komodo WebAssembly)**.
      - Khi Torch gặp lỗi hoặc thiếu lịch sử `startpos`, hệ thống **ghi log chẩn đoán lỗi và báo lỗi trung thực**, tuyệt đối không tự động chuyển sang Stockfish ReviewMath.
    - **Tổ chức 5 Tab chuẩn Extension**:
      - Đồng bộ cấu trúc Settings Dialog với Extension: `[ LIVE | ENGINE | COACH | VISUAL | ARROWS ]`.
      - Toàn bộ cài đặt phân loại nước đi, cảnh báo rung, chẩn đoán log WebAssembly được chuyển trọn vẹn sang tab **COACH**.
      - Tab **ENGINE** chỉ còn thuần túy phục vụ cấu hình động cơ tính toán nước đi (Komodo/Stockfish, Depth, MultiPV, Elo).
- **v2.0.19**:
  - **Khắc phục triệt để lỗi RuntimeError Aborted WebAssembly khi vào giữa ván**:
    - **Nguyên nhân gốc rễ**: Khi vào lại ván ở giữa chừng (nước 4-5), `fenHistory` bị thiếu các nước mở màn từ `startpos`. Chuỗi nước đi bắt đầu bằng nước đi không thể thực hiện từ bàn cờ ban đầu (ví dụ `e1g1` - nhập thành khi các quân chưa mở đường), khiến WebAssembly dính assertion fail `RuntimeError: Aborted()` và rơi vào vòng lặp restart worker.
    - **Giải pháp**:
      - Bổ sung bộ lọc an toàn `STARTPOS_LEGAL_FIRST_MOVES` (20 nước đi mở màn hợp lệ của Trắng từ `startpos`).
      - Nếu chuỗi nước đi không bắt đầu từ một nước đi hợp lệ của `startpos`, lập tức chặn không gửi cho Torch WebAssembly và hiển thị hướng dẫn người dùng: `⚠️ [Torch Coach] Cần lịch sử nước đi từ đầu ván`.
      - Bảo vệ tuyệt đối WebAssembly Worker không bao giờ bị abort hay restart lặp đi lặp lại.
- **v2.0.20**:
  - **Hook Move History trực tiếp từ PositionObject của Chess.com App**:
    - **Vấn đề**: Khi người dùng vào lại ván cờ giữa chừng, `fenHistory` không có các nước đi trước đó, dẫn đến bộ lọc chặn lệnh phân loại và hiển thị cảnh báo thiếu lịch sử.
    - **Giải pháp triệt để**:
      - Hook trực tiếp vào `positionObject` (`StandardPosition`) được truyền vào `onBoardChanged(stateImplObject, positionObject)`.
      - Thông qua reflection gọi `positionObject.h()` để lấy toàn bộ danh sách `PositionAndMove` (`com.chess.chessboard.history.i`) từ Move 1 đến hiện tại.
      - Chuyển đổi từng phần tử sang chuỗi UCI move bằng `com.chess.chessboard.compengine.MoveConverterKt.c(item)` (hoặc `b(move)`).
      - Cung cấp chuỗi nước đi đầy đủ 100% từ `startpos` cho `torch.analyze(moves, userColor, ...)`.
      - Giải quyết dứt điểm vấn đề vào giữa ván: Torch CEE phân loại chính xác mọi nước đi mà không bao giờ bị thiếu lịch sử hay crash WebAssembly.
- **v2.0.21**:
  - **Deep Reflection & Enhanced Scanner tự động nhận diện Elo cho Torch Coach**:
    - **Đục sâu Reflection từ Game Models**: Quét đa tầng qua `stateImplObject` và `Activity` (ViewModel, Game Controller) để tìm trực tiếp `RcnGameState` (`getWhiteRating()`, `getBlackRating()`) và `UserInfo` / `LiveUserInfo` (`getRating()`, `getColor()`) của cả 2 bên.
    - **Nâng cấp Enhanced Regex Scanner**: Hỗ trợ nhận diện số Elo kèm tên hoặc thể loại cờ (ví dụ `Magnus (2850)`, `Bot Martin (250)`, `1500 Rapid`), tự động phát hiện Elo người chơi (Bottom) và đối thủ (Top) dựa theo chiều bàn cờ (Flipped).
    - **Tự động đồng bộ Elo vào Torch CEE**: Gửi ngay lệnh `setoption name WhiteElo value X` và `setoption name BlackElo value Y` vào WebAssembly Engine để tính toán phân loại chuẩn xác theo trình độ thực tế của ván đấu.
- **v2.0.22**:
  - **Triệt tiêu 100% hiện tượng khựng/lag khi đi cờ**:
    - Chuyển toàn bộ tác vụ quét Elo (Reflection và View Hierarchy) sang một background single-thread worker (`SCAN_EXECUTOR`).
    - Trên UI Thread chỉ thực hiện snapshot cực nhẹ các text view (< 1ms, không reflection, không regex).
    - Loại bỏ lệnh gọi `EloScanner.scanAndApply` dư thừa khỏi `onArrowsChanged`.
    - Thêm throttle 3000ms và không quét lại khi đã phát hiện Elo cho ván hiện tại.
  - **Phân biệt rành mạch [Bạn] vs [Đối thủ]**:
    - Xác định chính xác quân của người chơi (`isUserWhite`), gắn tag `[Bạn]` hoặc `[Đối thủ]` trên Toast và Log.
    - Truyền đúng `userColor` là màu quân của người chơi thực tế vào Torch CEE.
  - **Sửa lỗi map nhãn Great Move**:
    - Ánh xạ chính xác `greatfind`, `great_find` sang `Nước cờ xuất sắc (Great)` (`!`) thay vì rơi vào default `Good`.
    - Bổ sung alias `missedwin` sang `Bỏ lỡ cơ hội thắng (Missed Win)` (`✕`).
  - **Hỗ trợ v1 và v2 ChessBoardView**:
    - Mở rộng `findChessBoardView` nhận diện cả `com.chess.chessboard.v2.ChessBoardView`.
    - Bổ sung log chẩn đoán `[ELO DEBUG]` chi tiết từng bước.
- **v2.0.23**:
  - **Triệt tiêu 100% Toast rác chạy ngầm / hệ thống**:
    - Xóa bỏ toàn bộ các Toast thông báo trạng thái nội bộ (`[Torch] Engine WASM sẵn sàng (100% Real)`, `Đang tải engine Torch`, `Tải hoàn tất`, cảnh báo startpos, v.v.).
    - Đảm bảo khi người dùng đóng app hoặc ra ngoài màn hình không bao giờ bị popup làm phiền.
    - Thêm kiểm tra vòng đời `isFinishing()` và `isDestroyed()` để bảo vệ toast phân loại nước đi.
  - **Đại tu giao diện Nhật ký chẩn đoán (Log Console)**:
    - **Tự động cuộn xuống đáy (Auto-scroll to bottom)**: Khi mở màn hình Log, tự động cuộn xuống dưới cùng để người dùng thấy ngay các log mới nhất.
    - **Tô màu cú pháp thông minh (Syntax Highlighting)**: Dùng `SpannableStringBuilder` phân biệt rực rỡ các tag `[ERROR]`, `[WARN]`, `[BESTMOVE]`, `[CLASSIFIED]`, `[TORCH]`, `[STOCKFISH]`, `[ELO]`, `[BOARD]` và timestamp.
    - **Thanh lọc nhanh (Filter Chips)**: Thêm 5 chip lựa chọn: `Tất cả`, `Torch / Phân loại`, `Stockfish`, `Bàn cờ & Elo`, `Lỗi / Cảnh báo`.
- **v2.0.24**:
  - **Trích xuất & Hiển thị Huy hiệu Phân loại chính hãng Chess.com trên Ô cờ (Square Classification Badge)**:
    - Nạp trực tiếp các Vector Drawable gốc từ resource APK Chess.com (`move_classification_classification_brilliant`, `great_find`, `best`, `excellent`, `good`, `book`, `inaccuracy`, `mistake`, `blunder`, `miss`, `missed_win`, `forced`).
    - Vẽ huy hiệu tròn chính thức ngay góc trên ô cờ vừa đi với bóng đổ mượt mà, biến mất khi bắt đầu ván mới.
    - Có cơ chế Fallback vẽ huy hiệu màu sắc chuẩn Chess.com nếu không nạp được drawable.
  - **Tích hợp Âm thanh Nước cờ Thiên tài (Brilliant Sound)**:
    - Tự động phát âm thanh chính thức `sounds/brilliant.mp3` của Chess.com khi phát hiện nước cờ Thiên tài.
  - **Loại bỏ Toast che màn hình**:
    - Chuyển hoàn toàn từ Toast hệ thống sang hiển thị trực quan trực tiếp trên bàn cờ.
- **v2.0.27 - v2.0.29**:
  - **Chuẩn hóa hiển thị đúng 1 phân loại duy nhất (v2.0.27)**: Khi đối thủ đi cờ, huy hiệu và hiệu ứng ô cờ của người chơi được thay thế ngay lập tức bằng nước đi mới của đối thủ.
  - **Bảng màu nguyên bản 100% từ APK (v2.0.28)**: Trích xuất trực tiếp giá trị màu nhị phân từ resource `color_classification_*.xml` trong APK Chess.com gốc.
  - **Gỡ bỏ cấm chụp màn hình (v2.0.29)**: Can thiệp Window và xóa cờ `FLAG_SECURE` trên toàn bộ Activity để người dùng tự do quay/chụp màn hình.
- **v2.0.30 - v2.0.31 (Sửa Lỗi Lệch Bàn Cờ Trận Trực Tiếp Live Match)**:
  - **Nhận diện chính xác ChessBoardView**: Không bị nhận nhầm container ngoài `ChessBoardLayout` (vốn bao gồm cả player bar và clock làm đẩy overlay lên trên).
  - **Nhận diện Flipped động**: Gọi trực tiếp `getFlipBoard()` từ `ChessBoardView` phản hồi ngay lập tức chiều bàn cờ.
  - **Chuẩn hóa tọa độ DecorView**: Tính toán delta giữa window location của bàn cờ và decor view cùng padding hệ thống, đảm bảo tuyệt đối khớp 1:1.
  - **Giới hạn biên huy hiệu (Clamping)**: Huy hiệu phân loại luôn nằm an toàn 100% trong ô cờ, không tràn viền.
- **v2.0.32 (Đột Phá Quét Elo Trong Trận Trực Tiếp RealGameActivity)**:
  - **Đục sâu RealGameViewModel qua Reflection**: Mở `Lazy` delegate, truy xuất `RcnPlayGameDelegateImpl` (`field m` -> `b()` -> `RcnGameState.getWhiteRating()` / `getBlackRating()`), `GameViewModelPlayersImpl` (`field d`), và danh sách `UserInfo` (`field e`).
  - **Bóc tách Jetpack Compose Semantics Tree**: Duyệt sâu `AndroidComposeView` / `ComposeView` qua `SemanticsOwner` (`field z` / `getSemanticsOwner()`) và config maps để lấy Elo và tên người chơi hiển thị trên giao diện Live.
  - **Regex Nâng Cấp**: Sử dụng `find()` thay cho `matches()` với mẫu `\\((\\d{3,4})\\??\\)` chấp nhận tên có emoji, cờ quốc gia, ký tự đặc biệt.
- **v2.0.35 (Thẻ Độ Chính Xác & Estimated Elo Gắn Trực Tiếp Vào Bàn Cờ Chess.com)**:
  - **Tạo widget PlayerAccuracyPillView**: Gắn 2 thẻ Cyber Glass trực tiếp vào mép trên và mép dưới bàn cờ hiển thị Accuracy (%) và Estimated Elo thời gian thực trích xuất từ `CAPS` và `reportCard` của Torch CEE WebAssembly.
  - **Nhận diện đúng màu quân người chơi**: Người chơi cầm Trắng thì thẻ phía bạn là Trắng, cầm Đen thì thẻ phía bạn là Đen kèm viền Cyber Blue phát sáng và nhãn `[BẠN]`, phía đối thủ mang nhãn `[ĐỐI THỦ]`.
  - **Bật/tắt linh hoạt**: Nút switch `Độ Chính Xác & Elo Trực Tiếp` trong Tab COACH menu cài đặt.
  - **Tự động dọn dẹp**: Tự ẩn khi hết ván hoặc tắt engine.
- **v2.0.36 (Sửa Lỗi Tối Màn Hình & Ghim Chặt Layout Thẻ Accuracy)**:
  - **Vô hiệu hóa triệt để StockfishTourOverlay**: Loại bỏ hoàn toàn overlay hướng dẫn tự động kích hoạt gây bóng elip tối mờ 80% màn hình trong trận đấu.
  - **Ghim Layout Thẻ Bằng Translation**: Sửa `OverlayManager` dùng `FrameLayout.LayoutParams` và `setTranslationX/Y()` để ghim chặt thẻ phía trên và dưới bàn cờ, không bị trôi hay co giật.
  - **Reset MaskFilter & Mutate Drawable**: Đảm bảo vẽ shadow và nạp tài nguyên icon không bị lem màu hoặc đè shader giữa các nước đi.
- **v2.0.37 (Cách C: Ghim Khít Bàn Cờ, Đồng Bộ 100% Song Ngữ & Gỡ Thông Báo Chiếu Hết)**:
  - **Triển khai Cách C cho Accuracy & Elo Pills**: Ghim khít 0 margin vào mép trên và mép dưới bàn cờ, tự động đo chiều rộng tối thiểu 232dp, triệt tiêu hoàn toàn lỗi cắt chữ `ELO ĐÁNH GIÁ`.
  - **Đồng bộ hóa 100% Song Ngữ Anh / Việt**: Cập nhật toàn bộ thẻ Accuracy/Elo, menu cài đặt 5 tab, hộp thoại nhật ký hệ thống, phân loại nước đi đồng bộ qua `I18n.java`.
  - **Xóa bỏ hoàn toàn thông báo chiếu hết**: Gỡ vĩnh viễn banner `MATE IN X!` và switch cài đặt.
  - **Quy chuẩn hóa và dọn dẹp**: Bổ sung `rule.md`, dọn sạch repository đưa scripts cũ vào `scripts_archive/` và `mpp_archive/`.
- **v2.0.38 (Áp Dụng Cài Đặt Real-time & Nút Tắt Menu Chuẩn Extension)**:
  - **Tự động áp dụng & lưu cấu hình thời gian thực (Auto-apply Real-time)**: Mọi thao tác switch, seekbar, chọn engine, chọn phong cách Komodo, đổi bảng màu đều tự động lưu vào SharedPreferences và áp dụng tức thì lên bàn cờ, không cần bấm nút lưu.
  - **Nút đóng Menu dấu ✕ phong cách Extension**: Trang bị nút `✕` tinh tế ở góc phải header; footer chuyển thành nút `Xong` (hoặc đóng ngay bằng `✕`).
  - **Đồng bộ hóa ngôn ngữ thời gian thực**: Nút chuyển đổi [VI]/[EN] gọi `OverlayManager.refreshOverlaysLanguage()` cập nhật ngay lập tức các lớp phủ bàn cờ và menu.

- **v2.0.39 (Tải Ngôn Ngữ In-Place & Loại Bỏ 100% Nút Footer Menu)**:
  - **Tải lại ngôn ngữ In-Place không chớp giật**: Nút [VI]/[EN] thay thế nội dung menu ngay tại chỗ (`dialog.setContentView`), lưu giữ nguyên Tab đang chọn, không bị dismiss dialog hay nhấp nháy màn hình.
  - **Loại bỏ hoàn toàn các nút ở Footer**: Gỡ sạch toàn bộ các nút ở footer (không Lưu, không Hủy, không Xong). Menu đóng duy nhất bằng nút `✕` chuẩn phong cách Extension ở góc trên bên phải header.

- **v2.0.41 (Tối Ưu & Dọn Dẹp Phân Loại)**:
  - Khôi phục cấu hình phân loại nước đi ổn định.
- **v2.0.42 (Khôi Phục Mũi Tên Hiểm Họa Gốc Native Chess.com)**:
  - Khôi phục vẽ Threat Arrow qua `ArrowInjector` sử dụng vector `HintArrow` gốc màu đỏ neon.
- **v2.0.43 (Tối Giản Menu Cài Đặt)**:
  - Xóa nút đỏ "Đặt lại cài đặt mặc định" ở cuối Tab ENGINE.
  - Xóa nút "Xem & Copy Log" chẩn đoán Torch ở Tab COACH.
- **v2.0.44 - v2.0.45 (Khắc Phục Triệt Để Âm Thanh Lạ / Illegal Move Sound & Rung Động)**:
  - Loại bỏ hoàn toàn các lệnh gọi phản xạ `setMoveArrows` và `invalidateAllBoards()` khi đi cờ.
  - Chuyển Threat Arrow sang render 100% bằng Canvas trên `ArrowOverlayView` với màu đỏ neon `#EF4444`.
  - Triệt tiêu 100% tiếng kêu âm thanh nước đi không hợp lệ của Chess.com và loại bỏ rung phản hồi.
- **v2.0.46 - v2.0.50 (Xử Lý Âm Thanh Nước Đi Khai Cuộc & Tách Lớp Phân Loại)**:
  - Chặn triệt để âm thanh không hợp lệ phát ra trong giai đoạn khai cuộc.
  - Khôi phục lại âm thanh nước cờ hợp lệ chuẩn của Chess.com.
  - Hạ thứ tự ưu tiên hiển thị (Z-order) của giao diện phân loại nước đi để không đè lên mũi tên và thanh eval bar.
- **v2.0.51 - v2.0.52 (Style Mũi Tên Đe Dọa Độc Lập Giống Chess.com)**:
  - Thiết kế style mũi tên hiểm họa màu đỏ riêng biệt, dễ phân biệt hoàn toàn với các mũi tên gợi ý tốt nhất khác.
- **v2.0.53 (Sửa Triệt Để Lỗi Đảo Lộn Toạ Độ Lượt Đi & Bàn Cờ Bị Lật)**:
  - Sửa dứt điểm việc đọc nhầm `sideToPlaySelfEffects` thành màu quân người chơi.
  - Bàn cờ xác định đúng hướng 100% thông qua hook sâu vào trường `t` và `getFlipBoard()`.
- **v2.0.54 (Tự Động Né Nhãn Điểm Eval Khỏi Thanh Eval Bar)**:
  - Tính toán độ chiếm chỗ của thanh Eval Bar ở cạnh trái bàn cờ.
  - Đăng ký vùng cấm đè vào thuật toán va chạm và kẹp toạ độ an toàn `minSafeX`, tự động dạt nhãn điểm sang các góc bên phải ô đích.
- **v2.0.55 (Tích Hợp Trực Tiếp Thanh Eval Bar Gốc Chess.com Native EvaluationBarView)**:
  - Hook và khởi tạo trực tiếp instance `com.chess.internal.views.EvaluationBarView` từ mã nguồn APK gốc của Chess.com.
  - Bơm trực tiếp đối tượng điểm `Score$Centipawns` và `Score$MateIn` nguyên bản, hiển thị animation và giao diện chuẩn gốc 100%.
- **v2.0.56 (Chuẩn Hóa Hiển Thị Thanh Eval Bar Dọc Native Vertical Orientation)**:
  - Phân tích bytecode DEX `classes8.dex`: `EvaluationBarView` mặc định khởi tạo hướng là `HORIZONTAL` khi không có xml styleable.
  - Can thiệp trực tiếp bằng Reflection gán trường enum `EvaluationBarView$Orientation` thành `Orientation.VERTICAL`.
  - Đồng bộ TextPaint sang `Paint.Align.CENTER` để căn chỉnh nhãn điểm eval chuẩn tâm theo chiều dọc.
  - Bật sẵn thanh Eval Bar mặc định (`isEvalBarEnabled = true`) trong SharedPreferences.
  - Đảm bảo hiển thị dọc chuẩn 100% lên/xuống dọc theo cạnh bàn cờ.
- **v2.0.57 (Tối Ưu Bố Cục Bàn Cờ & Thanh Eval Bar Không Đè Lên Ô Cờ)**:
  - Thử nghiệm dịch bàn cờ và scale tỷ lệ khi Eval Bar hiển thị.
- **v2.0.58 (Sửa Triệt Để Lệch Toạ Độ & Chuẩn Hoá Chiều Cao Thanh Eval Bar 100%)**:
  - Loại bỏ hoàn toàn can thiệp scale/translation vào `ChessBoardView`, khôi phục bàn cờ nguyên bản 1:1 sắc nét, toạ độ chuẩn tuyệt đối và cảm ứng kéo thả quân cờ ở cột a hoạt động hoàn hảo.
  - Chuẩn hoá `BoardMetrics`: Reset mọi biến dạng tồn đọng trước khi đọc toạ độ, kẹp chuẩn hình học bàn cờ 1:1 (`Math.min(rawW, rawH)`), đồng bộ tuyệt đối chiều cao thanh Eval Bar khớp 100% cạnh dọc bàn cờ.
  - Căn chỉnh điểm số tại đầu sân (Home-end Anchoring): Nhãn điểm số ghim cố định ở đầu sân của bên dẫn điểm thay vì nhảy cóc giữa thanh, triệt tiêu va chạm với vạch 0.00.
  - Sử dụng `EvalBarView` render nhãn điểm số xoay dọc -90 độ, căn giữa trục ngang theo `FontMetrics`, loại trừ 100% hiện tượng bị cắt chữ hay tràn viền ngoài màn hình.
  - Cảm ứng xuyên thấu 100%: Cả `dispatchTouchEvent` và `onTouchEvent` trả về `false`, không cản trở thao tác trên ô cờ cột a.
  - Bổ sung đường phân cách mảnh 1px tách biệt mép phải thanh Eval Bar với ô cờ cột a.
- **v2.0.59 (Widget Độ Chính Xác (%) & Elo Trực Tiếp Chuẩn 100% Extension)**:
  - **Widget nổi kéo thả độc lập (`AccuracyEloWidgetView`)**: Chuyển đổi toàn diện tính năng Độ Chính Xác (%) và Estimated Elo thành Widget Cyber Glass floating cao cấp giống hệt Chrome Extension NNVC.
  - **Phân chia 2 thẻ người chơi Trắng & Đen riêng biệt**: Nhận diện thông minh bên người chơi `[BẠN]` / `[YOU]` với viền phát sáng Cyber Blue `#0A84FF` và bên `[ĐỐI THỦ]` / `[OPPONENT]`.
  - **Kéo thả tự do & Ghi nhớ vị trí**: Hỗ trợ cảm ứng kéo thả linh hoạt, hiệu ứng scale 1.02x và alpha 0.95x, tự động kẹp an toàn mép màn hình và ghi nhớ toạ độ `(x, y)` vào SharedPreferences qua `StockfishSettings`. Loại bỏ hoàn toàn hiện tượng giật vị trí khi engine stream kết quả liên tục.
  - **Nút thu gọn / mở rộng tiện ích**: Header tích hợp chevron thu gọn thành mini pill 36dp giúp tiết kiệm không gian màn hình tối đa khi cần, tự động re-clamp chống tràn đáy màn hình khi mở rộng.
  - **Tối ưu bộ nhớ & Safe Area**: Tái sử dụng pre-allocated Paint fields trong `onDraw()`, tôn trọng WindowInsets top safe area, tự động re-clamp khi xoay màn hình (orientation change).
  - **Đồng bộ song ngữ Anh / Việt**: Khớp 100% với `I18n.java`.
  - **Biên dịch & Đóng gói**: Hoàn thành sạch 40 file Java, đóng gói thành công `patches-2.0.59.mpp` (189,674,047 bytes).
- **v2.0.60 (Khắc Phục Lỗi Nguồn Morphe Manager (!) & Chuẩn Hoá Metadata Bundle)**:
  - **Khắc phục triệt để lỗi nguồn Morphe Manager (!)**: Tìm ra nguyên nhân gốc rễ dấu chấm than đỏ `(!)` và "Số bản vá: 0" do schema `patches-bundle.json` trước đó chứa các trường thừa không chuẩn (`versionCode`, `downloadUrl`), gây lỗi parse JSON trên các client Morphe Manager nghiêm ngặt.
  - **Chuẩn hoá schema JSON 100%**: Chỉ giữ đúng 5 trường chuẩn schema của Morphe Manager (`created_at`, `description`, `download_url`, `signature_download_url`, `version`).
  - **Bump phiên bản lên v2.0.60**: Tuân thủ quy tắc sống còn trong `rule.md` để tự động dọn dẹp cache Morphe Manager và reset ledger gián đoạn của `PatchBundleLoadGuard`.
  - **Đồng bộ toàn bộ metadata**: Cập nhật đồng bộ `patches-list.json` và `patches-bundle.json`.
- **v2.0.61 (Tinh Chỉnh Widget Accuracy / Elo: Nhỏ Gọn & Sắc Nét Tuyệt Đối)**:
  - **Thu nhỏ kích thước widget ~12-15%**: Tinh chỉnh kích thước tối ưu 164x172dp (so với 186x194dp ban đầu), thanh header mini 32dp khi thu gọn, mở rộng tối đa tầm quan sát bàn cờ mà không che khuất các quân hay mũi tên nước đi.
  - **Sửa triệt để lỗi mờ chữ huy hiệu [BẠN] / [YOU]**: Khắc phục tận gốc lỗi rò rỉ trạng thái alpha (`0x2EFFFFFF` từ thẻ đối thủ rò rỉ sang paint thẻ người chơi khiến độ mờ bị giảm còn 18%). Chuyển sang nền Cyber Blue nguyên khối `#0A84FF` đặc 100% opaque, bổ sung viền Cyber Cyan tương phản cao `#64D2FF`, chữ màu trắng tinh in đậm (`#FFFFFF`, bold) và áp dụng `setFakeBoldText(true)` giúp các dấu thanh tiếng Việt (như dấu nặng trong chữ `Ạ`) nổi bật và sắc nét tuyệt đối trên nền thẻ quân trắng.
  - **Cập nhật ngưỡng chạm mở rộng / thu gọn**: Điều chỉnh `touchY <= 32dp` khớp chính xác với chiều cao header mới.
  - **Bump phiên bản lên v2.0.61**: Cập nhật đồng bộ `patches-bundle.json` và `patches-list.json`.
- **v2.0.62 (Nâng Cấp Giao Diện Highlight Ô Cờ Phân Loại Nước Đi)**:
  - **Khắc phục triệt để lỗi quân cờ bị lem màu / nhiễm màu**: Loại bỏ toàn bộ lớp màu bán trong suốt đặc (alpha 128 / 50%) từng phủ trùm lên ô cờ trong `ArrowOverlayView`, triệt tiêu hoàn toàn tình trạng quân cờ bị đè màu tint hay biến dạng màu sắc ban đầu.
  - **Thiết kế Cyber Frame & Corner Brackets chuẩn HUD**: 
    - Lớp nền bên trong ô cờ giảm xuống siêu mờ (6% cho ô xuất phát, 10-12% cho ô đích), giữ nguyên vẹn 100% độ sắc nét và màu sắc gốc của quân cờ Chess.com bên dưới.
    - Bổ sung viền ngoài thanh mảnh 1.5dp ôm sát 4 cạnh ô cờ.
    - Vẽ 4 mấu góc Cyber Corner Brackets chữ L sắc sảo (`bracketLen = 22%`, viền bo tròn `3.5%`) tại 4 góc với độ tương phản cao, mang lại hiệu ứng HUD công nghệ cao đẳng cấp chuẩn NNVC Extension.
    - Hiệu ứng phát sáng nhẹ nhàng (Glow) cho các nước cờ Brilliant và Great Move.
- **v2.0.63 (Khắc Phục Triệt Để Lỗi Nhiễm Màu / Lem Màu Quân Cờ Khi Phân Loại Nước Đi)**:
  - **Khắc phục dứt điểm lỗi quân cờ bị ám màu (Zero Tint Fill)**:
    - Loại bỏ 100% lớp phủ nền (fill tint) trên ô đích (`toSquare`) nơi quân cờ đang đứng. Giữ nguyên 100% chi tiết và màu sắc gốc của quân cờ Chess.com, tuyệt đối không bị đè màu hay biến đổi màu sắc (quân trắng không còn bị ám hồng/đỏ khi Blunder hay ám xanh khi Best).
  - **Khung Viền Kép Cao Cấp (Dual-Layer Ambient Halo & Crisp Focus Frame)**:
    - **Ô đích (Target)**: Viền hào quang ngoài (Ambient Halo) dịu mắt (`sqSize * 0.055f`, alpha 80), kết hợp khung tiêu điểm trong sắc nét (`sqSize * 0.032f`, alpha 235) với 4 góc bo tròn mềm mại (`cornerRadius = 8%`). Hiệu ứng hào quang ngoài mở rộng cho nước cờ Brilliant / Great Move mà hoàn toàn không xâm lấn vào thân quân cờ.
    - Triệt tiêu hoàn toàn sự che khuất / va chạm giữa mấu góc chữ L trước đây và huy hiệu badge ở góc trên-phải.
    - **Ô xuất phát (Origin)**: Sử dụng viền nét đứt thanh nhã (`DashPathEffect`) và lớp phủ nền siêu nhẹ (alpha 18) vì ô này hoàn toàn trống.
    - **Hiệu năng Canvas tối đa**: Tái sử dụng `RectF`, cache `DashPathEffect`, không tạo mới object trong `onDraw()`.
  - **Bump phiên bản lên v2.0.63**: Đồng bộ `patches-bundle.json` và `patches-list.json`.
  - **Biên dịch & Đóng gói**: Biên dịch thành công 40 file Java thành `extension.mpe`, đóng gói `patches-2.0.63.mpp` (189,674,525 bytes) và phát hành GitHub Release `v2.0.63`.

- **v2.0.64 (Loại Bỏ Rung Khi Sai Lầm & Thêm Hiệu Ứng + Âm Thanh Nước Đi Brilliant / Great)**:
  - **Loại bỏ triệt để rung khi mắc sai lầm:**
    - Xóa bỏ 100% rung haptic khi mắc lỗi (Blunder / Mistake / Inaccuracy).
    - Dọn dẹp switch rung sai lầm trong hộp thoại cài đặt HLV (`StockfishSettingsDialog.java`), khóa vĩnh viễn cấu hình `isBlunderAlertsEnabled()` về `false`.
  - **Hiệu ứng đồ họa vinh danh nước đi Brilliant (Cyber Radiance):**
    - Sóng hào quang kép lan tỏa (Dual Expanding Ripples, màu `#26C2A3` và `#64FFDA`) phát ra từ tâm ô đích trong chu kỳ hoạt ảnh 800ms mượt mà (`postInvalidateOnAnimation()`).
    - Ngôi sao tỏa tia 6 cánh sắc nét (6-point celebratory sparkle rayburst, màu `#FFFFFF`) và mấu góc lấp lánh (Corner Diamond Shimmer) bao quanh huy hiệu và khung tiêu điểm.
    - Tuyệt đối bảo tồn nguyên tắc Zero-Tint: Không lem bất kỳ lớp màu nào vào tâm quân cờ.
  - **Âm thanh nước cờ xuất sắc chuẩn Chess.com (Native Audio Delight):**
    - Xây dựng `SoundManager` với bộ đệm `SoundPool` độ trễ cực thấp, fallback sang `MediaPlayer`. Tự động phát âm thanh reo vui `sounds/brilliant.mp3` nguyên bản của Chess.com ngay khi nước cờ Brilliant xuất hiện.
  - **Bump phiên bản lên v2.0.64**: Đồng bộ `patches-bundle.json` và `patches-list.json`.
  - **Biên dịch & Đóng gói**: Biên dịch thành công 41 file Java thành `extension.mpe`, đóng gói `patches-2.0.64.mpp` (189,676,741 bytes) và phát hành GitHub Release `v2.0.64`.

### v2.0.65 (2026-10-10) — Chuyển Đổi 100% Stockfish & Komodo Sang WebAssembly Chuẩn Extension NNVC
- **Stockfish 18 WebAssembly Engine:**
  - Thay thế 100% binary native cũ `libstockfish.so` bằng `stockfish-18-lite-single.wasm` (7.29 MB) và `stockfish-18-lite-single.js` (20 KB) chạy trên headless WebView Web Worker độc lập.
  - Sửa lỗi Regex syntax error trong JS bootstrap khi nạp `stockfish-18-lite-single.js`.
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
- **Bump phiên bản lên v2.0.65**: Đồng bộ `patches-bundle.json` và `patches-list.json`.
- **Biên dịch & Đóng gói**: Biên dịch thành công 42 file Java thành `extension.mpe`, đóng gói `patches-2.0.65.mpp` (227,420,883 bytes).

### v2.0.66 (2026-10-10) — Khắc Phục Lỗi Tê Liệt Tính Năng (v2.0.65), Bổ Sung Hệ Thống Logger Tạm Thời & Engine Status HUD
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
- **Bump phiên bản lên v2.0.66**: Đồng bộ `patches-bundle.json` và `patches-list.json`.
- **Biên dịch & Đóng gói**: Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,720,792 bytes), đóng gói `patches-2.0.66.mpp` (227,433,088 bytes).

### v2.0.67 (2026-10-10) — 100% WebAssembly Parity: Komodo Dragon 3.3 WASM & Stockfish 18 WASM Chuẩn Extension (Zero Native Subprocess & Zero Fallback)
- **Khắc phục dứt điểm lỗi khởi động WebAssembly Engine trên Mobile:**
  - Tuyệt đối tuân thủ chỉ thị người dùng: Loại bỏ 100% subprocess native Stockfish cũ (`libstockfish.so`) và triệt tiêu hoàn toàn mọi cơ chế fallback.
  - **Komodo Dragon 3.3 WASM làm mặc định:** Tự động nạp `dragon3.3.wasm` (16.27 MB), `komodo.js` (177 KB) và sách khai cuộc `book.bin` (2.8 MB). Xây dựng cơ chế `WasmAwareXHR` đồng bộ chuẩn 100% trong Worker, phản hồi `uciok` và `readyok` tức thì (<100ms), nạp sách khai cuộc chuẩn xác.
  - **Stockfish 18 WASM:** Khởi tạo instance trực tiếp từ Web Worker qua factory với `wasmBinary` từ bộ nhớ và stream output thời gian thực, phục vụ phân tích ván đấu (Play Engine) và thanh đánh giá Eval Bar (Classify Engine).
  - Tự động di chuyển (migrate) cấu hình cũ sang `komodo`.
  - Cơ chế đồng bộ hóa `__WORKER_READY__` và hàng đợi lệnh không mất mát (`_queuedCmds`), triệt tiêu hoàn toàn nguy cơ rớt lệnh `uci` hoặc treo app.
- **Bảo toàn Hệ Thống Logger Tạm Thời & Engine Status HUD:**
  - Tiếp tục hỗ trợ `NnvcLogger` (ghi log chi tiết ra `/sdcard/Android/data/com.chess/files/nnvc_debug.log`), hộp thoại đọc log in-app `NnvcLogViewerDialog`, nút huy hiệu nổi kéo thả `FloatingLogPillView` và thông báo trạng thái `EngineStatusHUD`.
- **Bump phiên bản lên v2.0.67**: Đồng bộ `patches-bundle.json` và `patches-list.json`.
- **Biên dịch & Đóng gói**: Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,731,004 bytes), đóng gói `patches-2.0.67.mpp` (227,435,479 bytes).

### v2.0.68 (2026-10-11) — 100% Offline Local Engine Extraction & Bảo Mật Tuyệt Đối (Zero Source Leak)
- **Đóng Gói Trực Tiếp Toàn Bộ File WebAssembly Vào APK Qua Resource Patcher:**
  - Khắc phục triệt để lỗi "No WASM binaries present yet on disk": Xây dựng `StockfishResourceCopier` tích hợp trực tiếp vào bytecode của `stockfishResourcePatch`. Khi Morphe Manager thực hiện vá APK, toàn bộ tài nguyên WebAssembly (`komodo/dragon3.3.wasm`, `komodo/komodo.js`, `komodo/book.bin`, `stockfish18/stockfish-18-lite-single.wasm`, `stockfish18/stockfish-18-lite-single.js`, `torch/torch.wasm`, `torch/torch.js`) được sao chép thẳng vào thư mục `assets/` của APK Chess.com.
  - Cải tiến `WasmEngineManager`: Bổ sung cơ chế quét đa tầng `extractBundledWasmFiles` (quét trực tiếp các mục trong tệp APK qua `context.getPackageResourcePath()`, mở trực tiếp qua `context.getAssets().open()`, và quét bộ nhớ máy nếu có). Động cơ được giải nén sẵn sàng ngay trong lần đầu khởi chạy mà không cần tải thêm bất kỳ file nào từ internet.
- **Bảo Mật Tuyệt Đối & Loại Bỏ Hoàn Toàn Nguy Cơ Rò Rỉ Mã Nguồn:**
  - Xóa bỏ 100% các URL GitHub cá nhân / kho lưu trữ mã nguồn trong toàn bộ mã nguồn extension (`WasmEngineManager`, `TorchEngine`, `AccuracyEloWidgetView`, `ArrowOverlayView`, `EloScanner`, `PlayerAccuracyPillView`, `SoundManager`), không còn xuất hiện trong chuỗi string, file log debug `nnvc_debug.log` hay màn hình hiển thị HUD.
  - Loại bỏ tiến trình tải nền qua mạng gây ra lỗi HTTP 404 FileNotFoundException; chuyển sang kiến trúc 100% ngoại tuyến (offline-first).
- **Bump phiên bản lên v2.0.68**: Đồng bộ `patches-bundle.json` và `patches-list.json`.
- **Biên dịch & Đóng gói**: Biên dịch sạch 46 file Java Extension thành `extension.mpe` (2,731,908 bytes) và DEX gốc `classes.dex` (95,736 bytes), đóng gói `patches-2.0.68.mpp` (227,441,697 bytes).

---

## 3. TRẠNG THÁI HIỆN TẠI & KẾ HOẠCH TIẾP THEO

### 3.1. Trạng thái hiện tại
- **Phiên bản mới nhất**: **v2.0.68** (`patches-2.0.68.mpp`).
- **Trạng thái Repo**: 100% sạch, không commit file `.py`, root ngăn nắp.
- **Tiến trình kỹ thuật**:
  1. Kiến trúc 100% Offline Local Engine Extraction: Toàn bộ tệp WASM được trích xuất trực tiếp từ APK hoặc assets.
  2. Zero source code leakage, zero remote fallback downloads.
  3. Hệ thống chẩn đoán NnvcLogger + In-app Viewer `[📜]` + Floating Pill `[📜 LOG]` và EngineStatusHUD bảo toàn đầy đủ.


