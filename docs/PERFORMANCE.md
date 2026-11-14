# Hiệu năng trên TV

Thiết bị đo: Xiaomi TV S Pro 65 (MiTV-MOEU0), Android 14, MediaTek MT5879, **CPU 32-bit (armeabi-v7a)**,
GPU **Mali-G52**, RAM 2,4 GB. Màn hình 4K, giao diện dựng ở 1920×1080 rồi phóng lên.
Cách đo: [`tools/perf`](../tools/perf) (`measure.ps1` + `frames.py`, đọc `dumpsys gfxinfo framestats`).

## Kết quả (14/11/2026)

| Chỉ số | Ban đầu (bản debug) | Sau tối ưu (bản release) |
|---|---|---|
| Khởi động nguội (`am start -W`) | 4,2–4,8 s | **0,59–0,68 s** |
| Trang chủ, chuyển thẻ — luồng giao diện | 105 ms/khung | **10,7 ms** |
| Trang chủ, chuyển thẻ — GPU | ~54 ms* | **25,5 ms** |
| Trang chủ, chuyển thẻ — khoảng cách khung (trung vị) | ~105 ms (≈ 9 fps) | **17,9 ms (≈ 55 fps)** |
| Trang chủ đứng yên — GPU | 32 ms* | **16,2 ms** (aurora ~15 lần/giây) |
| Trang web, di con trỏ / cuộn | — | 16,7 ms (60 fps) |
| Video toàn màn hình khi tua | tua lại mỗi lần lặp phím | **1 lần tua** sau khi thả phím; GPU 2,5 ms |

\* đo trên bản release đầu tiên, trước khi giảm lớp vẽ.

## Đã làm và vì sao

| Vấn đề tìm thấy | Cách sửa |
|---|---|
| Bản cài trên TV là debug (không R8, ART không biên dịch trước) | Dùng bản **release** hằng ngày; `profileinstaller` cài baseline profile của Compose |
| Màn chào trong app chờ cố định 900 ms, nối sau màn chào hệ thống | Bỏ; dùng màn chào hệ thống (Android 12+) với logo Luna |
| Mỗi lần di focus, cả trang chủ dựng lại; `File.exists()` trong lúc dựng | `hero` chỉ đọc ở nơi cần; danh sách thẻ ghi nhớ theo `Store.version`; ảnh/tên miền/favicon tính một lần |
| Ghi lịch sử (JSON 300 mục) trên luồng giao diện | Ghi ở luồng nền tuần tự |
| Biên dịch ~20.000 luật lọc tranh CPU lúc khởi động | Luồng ưu tiên thấp; tải danh sách hoãn 20 s |
| Nền aurora: 3 lớp gradient toàn màn hình, 60 lần/giây | CPU vẽ vào ảnh 160×90, GPU chỉ phóng to; ~15 lần/giây; **tạm dừng khi đang bấm phím** |
| GPU chỉ chịu được ~5 lớp vẽ toàn màn hình/khung | Bỏ nền cửa sổ + nền gốc; ảnh nền vẽ thẳng với alpha (không qua bộ đệm phụ); 2 lớp phủ gộp thành 1 ảnh nhỏ; lớp làm tối của Cài đặt nằm sẵn trong aurora |
| `Crossfade` dùng bộ đệm phụ toàn màn hình khi đổi ảnh nền | `LightCrossfade` với `CompositingStrategy.ModulateAlpha` |
| Lịch sử dựng 300 dòng một lúc | `LazyColumn` |
| Tua: mỗi lần lặp phím = một lần tua → video tải lại liên tục | Cộng dồn, hiển thị vị trí đích, gửi **một** lệnh tua khi thả phím; giữ lâu thì bước tăng 10 s → 30 s → 60 s |
| Lần đầu mở web bị khựng do nạp Chromium | Nạp sẵn nhà cung cấp WebView ở luồng nền sau khởi động |

## Nguyên tắc khi thêm giao diện mới

- **Đếm lớp toàn màn hình**: mỗi lớp bán trong suốt toàn màn hình tốn ~5 ms GPU trên TV này. Tránh
  `Modifier.alpha`, `Crossfade`, `graphicsLayer { alpha }` cho vùng lớn — dùng `alpha` khi vẽ hoặc
  `CompositingStrategy.ModulateAlpha`.
- **Không I/O trong lúc dựng giao diện** (đọc file, `File.exists`, parse JSON).
- **Đọc state ở chỗ thấp nhất** (truyền `() -> T` thay vì `T`) để chỉ phần cần thiết dựng lại.
- Hiệu ứng nền chạy liên tục phải **dừng khi người dùng đang thao tác**.
- Đo lại trước/sau bằng `tools/perf/measure.ps1` trên bản **release**.

## Bản debug vs release

- **Release**: dùng hằng ngày (nhanh, không debug được).
- **Debug**: dùng khi cần `tools/sitecheck` (DevTools, log chặn quảng cáo). Hai bản ký cùng khóa nên
  cài đè qua lại không mất dữ liệu.
