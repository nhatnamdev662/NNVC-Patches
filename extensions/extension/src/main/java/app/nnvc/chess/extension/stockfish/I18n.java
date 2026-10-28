package app.nnvc.chess.extension.stockfish;

import android.content.Context;

public class I18n {

    private static volatile Boolean cachedIsVi = null;

    public static void invalidateCache() {
        cachedIsVi = null;
    }

    public static boolean isVi(Context context) {
        Boolean cached = cachedIsVi;
        if (cached != null) return cached;
        if (context == null) return true;
        try {
            boolean vi = "vi".equalsIgnoreCase(StockfishSettings.getLanguage(context));
            cachedIsVi = vi;
            return vi;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static String get(Context context, String key) {
        return get(key, isVi(context));
    }

    public static String get(String key, boolean isVi) {
        switch (key) {
            case "title":
                return isVi ? "CÀI ĐẶT NNVC" : "NNVC SETTINGS";
            case "ttl_sub":
                return "AI ENGINE · CHESS.COM";
            case "tab_live":
                return "LIVE";
            case "tab_engine":
                return "ENGINE";
            case "tab_coach":
                return "COACH";
            case "tab_visual":
                return "VISUAL";
            case "tab_arrows":
                return "ARROWS";
            case "engine":
                return "Engine";
            case "card_control":
                return isVi ? "Điều Khiển" : "Controls";
            case "enable_stockfish":
                return isVi ? "Bật Engine" : "Enable Engine";
            case "panic_hint":
                return isVi ? "Chạm đúp vào thanh trên để bật/tắt ngay lập tức (chế độ ẩn)." : "Double-tap the top bar to switch it off or on instantly (panic mode).";
            case "row_automove":
                return isVi ? "Tự Động Đi" : "Auto Move";
            case "automove_sub":
                return isVi ? "Auto-move khi có nước tốt" : "Auto-move best move";
            case "row_smart_delay":
                return isVi ? "Độ Trễ Thông Minh (Smart Delay)" : "Auto Smart Delay";
            case "smart_delay_sub":
                return isVi ? "Mô phỏng nhịp nghĩ sinh trắc học theo thể loại cờ" : "Human-like delays adapted to game time control";
            case "row_bullet_flagging":
                return isVi ? "Xả Nước Nhanh (Bullet Flagging)" : "Bullet Flagging";
            case "bullet_flagging_sub":
                return isVi ? "Đi ngay lập tức (0ms) khi thời gian còn <= 6s" : "Instantly move (0ms) when clock is under 6s";
            case "row_safemode":
                return isVi ? "🛡️ Chế Độ Safe" : "🛡️ Safe Mode";
            case "safemode_sub":
                return isVi ? "Đánh tự nhiên như người thật, an toàn tránh bị phát hiện (Tắt để đánh hết sức)" : "Play human-like & safe from bans (Disable to play at full strength)";
            case "safemode_toast_on":
                return isVi ? "🛡️ Chế độ Safe: BẬT (Đánh tự nhiên)" : "🛡️ Safe Mode: ON (Natural play)";
            case "safemode_toast_off":
                return isVi ? "⚡ Chế độ Safe: TẮT (Đánh hết sức)" : "⚡ Safe Mode: OFF (Max strength)";
            case "row_autostart":
                return isVi ? "Tự Động Tìm Trận" : "Auto Find Game";
            case "row_autostart_sub":
                return isVi ? "Tự động bắt đầu ván mới sau khi kết thúc" : "Automatically find a new match when game ends";
            case "toast_newgame":
                return isVi ? "🎮 Đang tìm trận mới..." : "🎮 Finding new game...";
            case "toast_empty_board_recovery":
                return isVi ? "⚡ Đang làm mới bàn cờ do mất kết nối..." : "⚡ Refreshing board due to connection stall...";
            case "toast_cd_countdown":
                return isVi ? "🎮 Tìm trận sau %ds... [Chạm để hủy]" : "🎮 New game in %ds... [Tap to cancel]";
            case "toast_cd_cancel":
                return isVi ? "Đã hủy tự động tìm trận" : "Auto find game cancelled";
            case "cd_label":
                return isVi ? "TÌM TRẬN MỚI" : "FIND NEW MATCH";
            case "cd_sub":
                return isVi ? "%d giây nữa tự tìm trận..." : "auto find match in %ds...";
            case "cd_cancel":
                return isVi ? "⏹ HỦY" : "⏹ CANCEL";
            case "cd_now":
                return isVi ? "⚡ TÌM NGAY" : "⚡ FIND NOW";
            case "autostart_toast_on":
                return isVi ? "🎮 Tự động tìm trận: BẬT" : "🎮 Auto find game: ON";
            case "autostart_toast_off":
                return isVi ? "🎮 Tự động tìm trận: TẮT" : "🎮 Auto find game: OFF";
            case "card_elo":
                return isVi ? "Sức Mạnh Engine" : "Engine Strength";
            case "row_elo":
                return "Elo";
            case "elo_hint":
                return isVi ? "Điều chỉnh trình độ gợi ý của Engine theo mức Elo." : "Adjust the engine Elo.";
            case "depth":
                return isVi ? "Độ Sâu (Depth)" : "Depth";
            case "depth_hint":
                return isVi ? "Độ sâu tìm kiếm nước đi tối ưu của Engine." : "Search depth for optimal moves.";
            case "card_arrow":
                return isVi ? "Thiết Lập Mũi Tên" : "Arrow Settings";
            case "best_move_arrows":
                return isVi ? "Bật / Tắt Mũi Tên" : "Toggle Arrows";
            case "best_move_arrows_hint":
                return isVi ? "Bật hoặc tắt toàn bộ mũi tên gợi ý trên bàn cờ." : "Toggle all suggestion arrows on board.";
            case "num_arrows":
                return isVi ? "Số Lượng Mũi Tên" : "Arrow Limit";
            case "arrows_hint":
                return isVi ? "Mũi tên 1 luôn bật tối ưu. Tăng số lượng để xem thêm các nước ứng viên." : "Move 1 is always optimal. Increase limit to view more candidate moves.";
            case "opponent_arrows":
                return isVi ? "Mũi Tên Đối Thủ" : "Opponent Arrows";
            case "opponent_arrows_hint":
                return isVi ? "Hiện mũi tên gợi ý khi đến lượt đối thủ." : "Show suggestion arrows when it is opponent's turn.";
            case "threat_arrow":
                return isVi ? "Mũi Tên Đe Dọa" : "Threat Arrows";
            case "threat_arrow_hint":
                return isVi ? "Vẽ mũi tên chỉ rõ quân cờ đang tấn công và mục tiêu bị uy hiếp." : "Draw arrows showing attacking pieces and threatened targets.";
            case "threat_mode":
                return isVi ? "Chế Độ Đe Dọa" : "Threat Mode";
            case "threat_depth":
                return isVi ? "Độ Sâu Đe Dọa" : "Threat Depth";
            case "premove_arrow":
                return isVi ? "Mũi Tên Premove" : "Premove Arrow";
            case "premove_arrow_hint":
                return isVi ? "Hiện gợi ý nước premove trao đổi quân khi đối thủ đang suy nghĩ." : "Show premove recapture arrow for obvious piece exchanges.";
            case "card_cfg":
                return isVi ? "Giao Diện" : "Visual Interface";
            case "eval_bar":
                return isVi ? "Thanh Eval Bar" : "Eval Bar";
            case "eval_bar_hint":
                return isVi ? "Hiện thanh đánh giá cạnh bàn cờ." : "Show evaluation bar next to the board.";
            case "rate_moves":
                return isVi ? "Phân Loại Nước Đi" : "Move Classification";
            case "rate_moves_hint":
                return isVi ? "Hiển thị biểu tượng đánh giá chất lượng nước đi như Báo cáo trận đấu: Xuất sắc, Tối ưu, Sai lầm, Đại sai lầm..." : "Display Game Review badges for every move: Brilliant, Great, Best, Inaccuracy, Mistake, Blunder...";
            case "vibrate_blunder":
                return isVi ? "Rung Khi Mắc Sai Lầm" : "Vibrate on Mistakes";
            case "vibrate_hint":
                return isVi ? "Rung cảnh báo khi vừa đi nước đi không tối ưu hoặc Blunder." : "Vibrate warning on sub-optimal or blunder moves.";
            case "reset_defaults":
                return isVi ? "Đặt Lại Cài Đặt Mặc Định" : "Reset to Defaults";
            case "reset_title":
                return isVi ? "Đặt lại cài đặt engine?" : "Reset engine settings?";
            case "reset_message":
                return isVi ? "Độ sâu, mũi tên, lớp phủ và Elo sẽ trở về mặc định." : "Depth, arrows, overlays and strength go back to their defaults.";
            case "reset_toast":
                return isVi ? "Đã đặt lại cài đặt engine" : "Engine settings reset";
            case "cancel":
                return isVi ? "Huỷ" : "Cancel";
            case "reset":
                return isVi ? "Đặt lại" : "Reset";
            case "save":
                return isVi ? "Lưu" : "Save";
            case "settings_saved":
                return isVi ? "Đã lưu cài đặt" : "Settings saved";
            case "engine_name":
                return isVi ? "Engine: Stockfish 19 NNUE · ngoại tuyến" : "Engine: Stockfish 19 NNUE · offline";
            case "dev_name":
                return "NNVC";
            case "contact_telegram":
                return isVi ? "Liên hệ Telegram: @nncutett" : "Telegram: @nncutett";
            case "open_telegram":
                return isVi ? "Mở Telegram @nncutett" : "Open Telegram @nncutett";
            case "arrow_palette_title":
                return isVi ? "Bảng màu mũi tên gợi ý" : "Arrow Color Palette";
            case "arrow_palette_hint":
                return isVi ? "Chạm vào ô màu để đổi màu cho từng thứ hạng gợi ý." : "Tap color box to customize colors for each candidate tier.";
            case "tier_1":
                return isVi ? "Nước 1 (Tối ưu)" : "Move 1 (Best)";
            case "tier_2":
                return isVi ? "Nước 2" : "Move 2";
            case "tier_3":
                return isVi ? "Nước 3" : "Move 3";
            case "tier_4":
                return isVi ? "Nước 4" : "Move 4";
            case "tier_5":
                return isVi ? "Nước 5" : "Move 5";
            case "choose_color":
                return isVi ? "Chọn màu" : "Pick Color";
            case "arrow_theme_title":
                return isVi ? "Chủ đề màu mũi tên" : "Arrow Color Themes";
            case "arrow_theme_hint":
                return isVi ? "Chạm để áp dụng ngay bảng màu thiết kế sẵn cho cả 5 mũi tên." : "Tap to apply a pre-designed color theme to all 5 candidate arrows.";
            case "theme_cyber":
                return isVi ? "🌟 Cyber Neon" : "🌟 Cyber Neon";
            case "theme_classic":
                return isVi ? "♟️ Cổ điển" : "♟️ Classic";
            case "theme_synthwave":
                return isVi ? "🌌 Synthwave" : "🌌 Synthwave";
            case "theme_fire":
                return isVi ? "🔥 Rực lửa" : "🔥 Fire Magma";
            case "theme_ice":
                return isVi ? "❄️ Băng giá" : "❄️ Ice Frost";
            case "theme_emerald":
                return isVi ? "🌿 Lục bảo" : "🌿 Emerald";
            case "toast_theme_applied":
                return isVi ? "🎨 Đã đổi chủ đề màu mũi tên!" : "🎨 Arrow color theme applied!";
            case "color_preview":
                return isVi ? "Xem trước trên bàn cờ" : "Live Board Preview";
            case "color_hue":
                return isVi ? "Dải màu quang phổ (Kéo để chỉnh)" : "Color Spectrum (Drag to tune)";
            case "color_swatches_title":
                return isVi ? "Bảng màu tuyển chọn (48 màu)" : "Curated Swatches (48 Colors)";
            case "quick_colors":
                return isVi ? "Màu phổ biến" : "Popular Colors";
            case "btn_reset_default":
                return isVi ? "Mặc định" : "Default";
            case "btn_done":
                return isVi ? "Xong" : "Done";
            case "engine_choice":
                return isVi ? "Chọn Engine" : "Engine";
            case "engine_choice_hint":
                return isVi ? "Lựa chọn động cơ phân tích phù hợp với phong cách thi đấu của bạn." : "Select the chess engine that matches your strategic playing style.";
            case "engine_komodo":
                return "Komodo 3.3";
            case "engine_stockfish":
                return "Stockfish 18";
            case "debug_log":
                return isVi ? "Nhật Ký Hoạt Động" : "Activity Log";
            case "debug_log_hint":
                return isVi ? "Nhật ký hệ thống phục vụ chẩn đoán." : "System diagnostics log.";
            case "floating_log_pill":
                return isVi ? "Nút Nổi" : "Floating Pill";
            case "floating_log_pill_hint":
                return isVi ? "Nút nổi hỗ trợ truy cập nhanh." : "Floating shortcut button.";
            case "auto_depth":
                return isVi ? "Tự Động Độ Sâu Theo Elo" : "Auto Depth by Elo";
            case "auto_depth_hint":
                return isVi ? "Tự động tối ưu hóa độ sâu tìm kiếm tương ứng với trình độ Elo đã chọn." : "Automatically optimize search depth according to selected Elo rating.";
            case "komodo_style":
                return isVi ? "Lối Chơi Komodo" : "Komodo Style";
            case "style_default":
                return isVi ? "Mặc định" : "Default";
            case "style_aggressive":
                return isVi ? "Tấn công" : "Aggressive";
            case "style_defensive":
                return isVi ? "Phòng thủ" : "Defensive";
            case "coach_depth":
                return isVi ? "Độ Sâu Phân Loại" : "Classification Depth";
            case "coach_depth_hint":
                return isVi ? "Độ sâu tính toán khi phân loại nước đi (Mặc định: 2). Giá trị thấp giúp phản hồi nhanh và mượt mà hơn." : "Calculation depth for move classification (Default: 2). Lower values provide faster responses.";
            case "show_accuracy_elo":
                return isVi ? "Độ Chính Xác & Elo Trực Tiếp" : "Live Accuracy & Estimated Elo";
            case "show_accuracy_elo_hint":
                return isVi ? "Hiển thị tiện ích độ chính xác (%) và ước tính Elo (Chụm 2 ngón tay trên HUD để phóng to / thu nhỏ)." : "Display real-time move accuracy and estimated Elo (Pinch with 2 fingers on HUD to resize).";
            case "white":
                return isVi ? "Trắng" : "White";
            case "black":
                return isVi ? "Đen" : "Black";
            case "coach_title":
                return isVi ? "HUẤN LUYỆN VIÊN" : "AI COACH";
            case "coach_sub":
                return isVi ? "Phân tích thế trận, giải thích chiến thuật và hướng dẫn nước cờ tối ưu sau mỗi lượt đi." : "Real-time position insights, move analysis, and tactical coaching after every turn.";
            case "coach_log":
                return isVi ? "📋 Nhật Ký Huấn Luyện Viên" : "📋 Coach Diagnostic Log";
            case "view_copy_log":
                return isVi ? "📋 Xem & Copy Log" : "📋 View & Copy Log";
            case "log_title":
                return isVi ? "⚡ NHẬT KÝ HỆ THỐNG" : "⚡ SYSTEM LOGS";
            case "jump_bottom":
                return isVi ? "⬇ Cuối" : "⬇ Bottom";
            case "jump_top":
                return isVi ? "⬆ Đầu" : "⬆ Top";
            case "clear_log":
                return isVi ? "🗑️ Xóa" : "🗑️ Clear";
            case "close":
                return isVi ? "Đóng" : "Close";
            case "copy":
                return isVi ? "📋 Sao chép" : "📋 Copy";
            case "copied_toast":
                return isVi ? "Đã sao chép nhật ký vào bộ nhớ tạm!" : "Logs copied to clipboard!";
            case "cleared_toast":
                return isVi ? "Đã xóa toàn bộ nhật ký!" : "All logs cleared!";
            case "role_you":
                return isVi ? "BẠN" : "YOU";
            case "role_opponent":
                return isVi ? "ĐỐI THỦ" : "OPPONENT";
            case "label_accuracy":
                return isVi ? "CHÍNH XÁC" : "ACCURACY";
            case "label_elo":
                return isVi ? "ELO ĐÁNH GIÁ" : "EST. ELO";
            case "custom_title":
                return isVi ? "Danh Hiệu Người Dùng" : "User Title";
            case "custom_title_hint":
                return isVi ? "Hiển thị danh hiệu cờ vua (GM, IM, FM, NM...) trên hồ sơ và tên người chơi của bạn." : "Display authentic chess title badge (GM, IM, FM, NM...) on your profile and username.";
            case "choose_title":
                return isVi ? "Chọn Danh Hiệu" : "Choose Title";
            case "user_only_title":
                return isVi ? "Chỉ Áp Dụng Cho Tài Khoản Của Bạn" : "Applied To Your Account Only";
            case "custom_title_user_hint":
                return isVi ? "Nhập username của bạn (để trống sẽ tự nhận diện nick đang đăng nhập)." : "Enter your username (leave blank to auto-detect logged-in account).";
            case "status_ready":
                return isVi ? "ĐANG HOẠT ĐỘNG" : "ANALYSIS ACTIVE";
            case "status_off":
                return isVi ? "ĐANG TẮT" : "ANALYSIS DISABLED";
            case "tcp_label":
                return isVi ? "THỜI GIAN VÁN ĐẤU" : "TIME CONTROL";
            case "tcp_src_auto":
                return isVi ? "Tự động nhận diện" : "Auto-detected";
            case "tcp_src_clock":
                return isVi ? "Từ đồng hồ" : "From clock";
            case "engine_recovering":
                return isVi ? "🔄 Đang tự động phục hồi Engine..." : "🔄 Auto-Recovering Engine...";
            default:
                return key;
        }
    }

    public static String getTcPresetLabel(Context context, int tcIndex) {
        boolean isVi = isVi(context);
        switch (tcIndex) {
            case 0: return isVi ? "1 phút" : "1 min";
            case 1: return "1|1";
            case 2: return "2|1";
            case 3: return isVi ? "3 phút" : "3 min";
            case 4: return "3|2";
            case 5: return isVi ? "5 phút" : "5 min";
            case 6: return isVi ? "10 phút" : "10 min";
            case 7: return "15|10";
            case 8: return isVi ? "30 phút" : "30 min";
            default: return isVi ? "5 phút" : "5 min";
        }
    }

    public static String getTcButtonLabel(Context context, int tcIndex) {
        boolean isVi = isVi(context);
        switch (tcIndex) {
            case 0: return isVi ? "1ph" : "1m";
            case 1: return "1|1";
            case 2: return "2|1";
            case 3: return isVi ? "3ph" : "3m";
            case 4: return "3|2";
            case 5: return isVi ? "5ph" : "5m";
            case 6: return isVi ? "10ph" : "10m";
            case 7: return "15|10";
            case 8: return isVi ? "30ph" : "30m";
            default: return isVi ? "5ph" : "5m";
        }
    }
}
