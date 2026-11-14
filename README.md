# Luna Browser

Trình duyệt web dành cho Android TV (làm cho Xiaomi TV S Pro 65"). Kotlin, Jetpack Compose, WebView hệ thống.
Giao diện tiếng Việt, phong cách ứng dụng xem phim.

- **Trang chủ kiểu Netflix/YouTube:** nền ánh sáng chuyển động, ảnh nền lớn đổi theo thẻ đang chọn,
  thẻ 16:9 có ảnh thu nhỏ thật (ảnh video YouTube đã xem, ảnh đại diện bài viết, ảnh chụp trang),
  các hàng "Xem tiếp", "Trang web của bạn", "Yêu thích", đồng hồ, tìm kiếm bằng giọng nói.
- **Con trỏ ảo** có gia tốc, tự ẩn khi đứng yên, hiệu ứng khi bấm; đổi sang chế độ liên kết bằng cách giữ OK.
- **Xem video:** điều khiển toàn màn hình bằng remote (OK, Trái/Phải tua, Phát/Dừng), kể cả với trình phát
  nằm trong iframe của tên miền khác (trang phim); tự phát tiếp khi vào toàn màn hình.
- **Chặn quảng cáo và popup:** danh sách tên miền quảng cáo (cập nhật được), chặn cửa sổ bật lên khác trang,
  chặn tự chuyển hướng sang trang quảng cáo, gỡ lớp phủ vô hình bắt cú click.
- Lịch sử, Yêu thích, Cài đặt (7 nhóm) — xem [docs/DESIGN.md](docs/DESIGN.md).

## Build
CI build APK debug ở mỗi lần push (Actions → *Build APK* → artifact `luna-browser-debug`).
Mọi bản build ký cùng khóa `app/luna.keystore`, nên cài đè lên TV vẫn giữ dữ liệu.
Build trên máy (JDK 17 + Android SDK 34):

```bash
gradle :app:assembleDebug
```

## Cài lên TV
Bật Developer options → USB debugging trên TV, rồi:

```bash
adb connect <ip-tv>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
