package com.luna.browser.ui.settings

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.browser.AppState
import com.luna.browser.data.K
import com.luna.browser.data.SearchEngines
import com.luna.browser.ui.components.Aurora
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.Danger
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.Glass
import com.luna.browser.ui.theme.Success
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextPrimary
import com.luna.browser.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private sealed interface SRow
private data class Toggle(val key: String, val title: String, val desc: String) : SRow
private data class Choice(val key: String, val title: String, val desc: String, val options: List<Pair<String, String>>) : SRow
private data class Action(val title: String, val desc: String, val value: String = "", val danger: Boolean = false, val confirm: Boolean = false, val run: () -> Unit) : SRow
private data class Info(val title: String, val value: String) : SRow

private class Cat(val name: String, val icon: ImageVector)

private val categories = listOf(
    Cat("Chung", Icons.Rounded.Tune),
    Cat("Trang chủ", Icons.Rounded.Home),
    Cat("Duyệt web", Icons.Rounded.Mouse),
    Cat("Chặn quảng cáo", Icons.Rounded.Shield),
    Cat("Video", Icons.Rounded.Videocam),
    Cat("Quyền riêng tư", Icons.Rounded.Lock),
    Cat("Giới thiệu", Icons.Rounded.Info),
)

@Composable
fun SettingsScreen(app: AppState) {
    var selected by remember { mutableIntStateOf(0) }
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFr.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        Aurora(animated = app.store.bool(K.ANIMATED_BG))
        Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.55f)))
        Row(Modifier.fillMaxSize().padding(horizontal = Dim.SafeH, vertical = Dim.SafeV)) {
            Column(Modifier.width(280.dp).fillMaxHeight().padding(end = 12.dp)) {
                Txt("Cài đặt", T.display.copy(fontSize = 38.sp), Modifier.padding(bottom = 18.dp))
                categories.forEachIndexed { i, c ->
                    CategoryItem(c, i == selected, if (i == 0) Modifier.focusRequester(firstFr) else Modifier) { selected = i }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color(0x99121622))
                    .verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                Txt(categories[selected].name, T.title, Modifier.padding(start = 6.dp, bottom = 14.dp))
                val rows = rows(categories[selected].name, app)
                var confirming by remember(selected) { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { r ->
                        when (r) {
                            is Toggle -> {
                                val on = app.store.bool(r.key)
                                SettingRow(r.title, r.desc, if (on) "✓  Bật" else "Tắt", on) { app.store.put(r.key, !on) }
                            }
                            is Choice -> {
                                val cur = r.options.indexOfFirst { it.first == app.store.str(r.key) }.coerceAtLeast(0)
                                SettingRow(r.title, r.desc, "‹  ${r.options[cur].second}  ›", false) {
                                    app.store.put(r.key, r.options[(cur + 1) % r.options.size].first)
                                }
                            }
                            is Action -> {
                                val armed = confirming == r.title
                                SettingRow(r.title, r.desc, if (armed) "Bấm OK lần nữa" else r.value, armed, danger = r.danger || armed) {
                                    if (r.confirm && !armed) {
                                        confirming = r.title
                                        app.toast("Bấm OK lần nữa để xác nhận")
                                    } else {
                                        confirming = null
                                        r.run()
                                    }
                                }
                            }
                            is Info -> InfoRow(r.title, r.value)
                        }
                    }
                    if (categories[selected].name == "Chặn quảng cáo") {
                        val ads = app.ads
                        val status = when {
                            ads.updating -> "Đang cập nhật…"
                            ads.updateFailed -> "Cập nhật thất bại"
                            ads.lastUpdated == 0L -> "Danh sách có sẵn"
                            else -> "Cập nhật " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ads.lastUpdated))
                        }
                        SettingRow(
                            "Cập nhật danh sách chặn",
                            if (ads.updateFailed) "Không tải được danh sách. Kiểm tra kết nối Internet rồi thử lại."
                            else "Tải danh sách mới nhất các trang quảng cáo và theo dõi cần chặn.",
                            status, false, danger = ads.updateFailed,
                        ) {
                            scope.launch {
                                val ok = ads.update()
                                app.toast(if (ok) "Đã cập nhật danh sách chặn" else "Chưa cập nhật được danh sách chặn. Hãy thử lại sau.")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(c: Cat, selected: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    // Moving focus onto a category shows its settings immediately — no extra click.
    FocusSurface(
        onClick = onFocus,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).height(58.dp).onFocusChanged { if (it.isFocused) onFocus() },
        container = if (selected) Accent.copy(alpha = 0.22f) else Glass,
        focusScale = 1.03f,
    ) {
        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Icon(c.icon, null, tint = if (selected) Accent else TextPrimary, modifier = Modifier.size(24.dp))
            Txt("  " + c.name, T.label)
        }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, value: String, on: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    FocusSurface(onClick, Modifier.fillMaxWidth().heightIn(min = Dim.RowHeight), container = Glass, focusScale = 1.02f) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(title, T.body)
                Txt(desc, T.caption, maxLines = 2)
            }
            if (value.isNotEmpty()) {
                Box(
                    Modifier.padding(start = 16.dp).background(if (on) Success.copy(alpha = .16f) else Color(0x14FFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Txt(value, T.label, color = if (danger) Danger else if (on) Success else TextSecondary) }
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, T.body, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Txt(value, T.body, color = TextSecondary, maxLines = 2)
    }
}

private fun rows(cat: String, app: AppState): List<SRow> {
    val s = app.store
    return when (cat) {
        "Chung" -> listOf(
            Choice(K.ENGINE, "Công cụ tìm kiếm", "Dùng khi bạn gõ từ khóa thay vì địa chỉ trang web.", SearchEngines.keys.map { it to it }),
            Toggle(K.ANIMATED_BG, "Hình nền chuyển động", "Ánh sáng màu trôi nhẹ phía sau màn hình."),
            Toggle(K.REDUCE_MOTION, "Giảm chuyển động", "Hiệu ứng đơn giản, nhanh hơn. Phù hợp với TV cấu hình yếu."),
        )
        "Trang chủ" -> listOf(
            Choice(K.LAUNCH, "Khi mở Luna", "Chọn màn hình đầu tiên bạn thấy.", listOf("home" to "Trang chủ", "resume" to "Trang xem gần nhất")),
            Toggle(K.SHOW_CONTINUE, "Hiện mục “Xem tiếp”", "Hiện những trang và video bạn vừa xem kèm ảnh thu nhỏ."),
        )
        "Duyệt web" -> listOf(
            Toggle(K.CURSOR_START, "Bắt đầu bằng con trỏ", "Trang web mở ra với con trỏ trên màn hình. Tắt để dùng phím mũi tên nhảy giữa các liên kết."),
            Choice(K.CURSOR_SPEED, "Tốc độ con trỏ", "Con trỏ chạy nhanh đến đâu khi giữ phím mũi tên.", (1..5).map { it.toString() to "Mức $it" }),
            Toggle(K.DESKTOP, "Xem trang web bản máy tính", "Hiện trang web đầy đủ, hợp với màn hình lớn."),
            Choice(K.ZOOM, "Cỡ chữ trang web", "Phóng to chữ trên trang để đọc rõ từ ghế sofa.", listOf("100", "125", "150", "175").map { it to "$it%" }),
        )
        "Chặn quảng cáo" -> listOf(
            Toggle(K.ADBLOCK, "Chặn quảng cáo và theo dõi", "Không tải nội dung từ các trang quảng cáo đã biết."),
            Toggle(K.BLOCK_POPUPS, "Chặn cửa sổ bật lên", "Không cho trang web tự mở cửa sổ mới."),
            Info("Đã chặn", "${app.ads.blockedCount} quảng cáo"),
            Info("Số trang trong danh sách", app.ads.listSize.toString()),
        )
        "Video" -> listOf(
            Choice(K.SEEK_STEP, "Bước tua", "Khoảng tua mỗi lần bấm Trái / Phải khi xem video.", listOf("5", "10", "15", "30").map { it to "$it giây" }),
            Info("OK", "Hiện điều khiển, rồi Phát / Dừng"),
            Info("Trái / Phải", "Tua lùi / tua tới"),
            Info("Quay lại", "Ẩn điều khiển, rồi thoát toàn màn hình"),
            Info("Phím Phát / Dừng", "Phát hoặc dừng video trên trang"),
        )
        "Quyền riêng tư" -> listOf(
            Toggle(K.THIRD_PARTY, "Cho phép cookie bên thứ ba", "Một số trang cần để đăng nhập hoặc phát video."),
            Action("Xóa lịch sử duyệt web", "Xóa danh sách trang đã xem và ảnh thu nhỏ.", danger = true, confirm = true) {
                s.clearHistory(); app.toast("Đã xóa lịch sử")
            },
            Action("Xóa cookie và dữ liệu trang", "Bạn sẽ bị đăng xuất khỏi các trang web.", danger = true, confirm = true) {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                app.toast("Đã xóa cookie và dữ liệu trang")
            },
            Action("Xóa bộ nhớ đệm", "Giải phóng dung lượng. Lần đầu mở lại trang có thể chậm hơn.") {
                app.browser.webView.clearCache(true); app.toast("Đã xóa bộ nhớ đệm")
            },
        )
        else -> listOf(
            Info("Luna", "Phiên bản 0.2.0"),
            Info("Trình duyệt nền", runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "Không rõ"),
            Info("Thiết bị", "${Build.MANUFACTURER} ${Build.MODEL}"),
            Info("Menu", "Mở / đóng thanh công cụ"),
            Info("Giữ OK", "Đổi giữa con trỏ và chế độ liên kết"),
            Info("Giữ Quay lại", "Mở thanh công cụ"),
        )
    }
}
