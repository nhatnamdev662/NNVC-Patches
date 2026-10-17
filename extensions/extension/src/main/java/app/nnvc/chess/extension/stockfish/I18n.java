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
        boolean isVi = isVi(context);
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
                return isVi ? "Hiển thị tiện ích thống kê độ chính xác (%) và ước tính Elo theo thời gian thực." : "Display real-time move accuracy percentage and estimated performance rating.";
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
            default:
                return key;
        }
    }
}
