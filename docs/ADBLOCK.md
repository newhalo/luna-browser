# Chặn quảng cáo và popup — cách hoạt động và cách bảo trì

Trang phim miễn phí đổi tên miền và cách chèn quảng cáo liên tục. Luna không viết code riêng cho
từng trang. Thay vào đó, mọi thứ đi qua **ba lớp chung**, và phần thay đổi theo trang nằm trong
**danh sách lọc dạng văn bản** (cú pháp Adblock Plus / uBlock Origin).

```
           ┌────────────────────────── Danh sách lọc (văn bản, cú pháp ABP) ─────────────────────────┐
           │  filters/luna.txt (đi kèm app)   ABPVN (tải hằng ngày)   ABPVN hosts (tải hằng ngày)    │
           └───────────────────────────────────────┬──────────────────────────────────────────────────┘
                                                   ▼
                                     FilterEngine (data/filter/)
                ┌──────────────────────────┬───────┴─────────────┬──────────────────────────────┐
                ▼                          ▼                     ▼                              ▼
   1. Chặn request mạng       2. Ẩn phần tử (CSS)      3. Chặn chuyển trang / popup    (PopupGuard — luật chung,
   shouldInterceptRequest      injectCosmetics          shouldOverrideUrlLoading,       không cần danh sách)
                                                         onCreateWindow
```

| Lớp | Ở đâu | Xử lý được |
|---|---|---|
| Chặn request | `AdBlocker.shouldBlock` ← `BrowserController.shouldInterceptRequest` | script/ảnh/iframe quảng cáo, mạng popunder |
| Ẩn phần tử | `FilterEngine.css` → `injectCosmetics` | banner dính đáy (catfish), khung quảng cáo trong trang |
| Chuyển trang / popup | `BrowserController` + `PopupGuard` | trang bị thay bằng quảng cáo, cửa sổ bật lên, trang tự chuyển hướng |
| Lớp phủ vô hình | `PopupGuard.OVERLAY_JS` (chèn ở đầu mọi frame) | iframe/lớp trong suốt phủ toàn màn hình bắt cú click |
| Điều khiển video | `PopupGuard.VIDEO_BRIDGE_JS` | trình phát nằm trong iframe khác tên miền |

Luật chung của `PopupGuard` (không phụ thuộc trang):
- Cửa sổ mới chỉ được mở nếu **cùng trang** hoặc là trang đăng nhập (Google, Facebook, Apple, Microsoft, Zalo).
- Trang **tự chuyển sang trang khác** khi người xem không bấm gì (sau 2,5 giây kể từ lúc tải) bị chặn.
- Chuyển trang tới tên miền nằm trong danh sách lọc bị chặn.

## Khi một trang lại hiện quảng cáo

1. **Cập nhật danh sách:** Cài đặt → Chặn quảng cáo → *Cập nhật danh sách chặn*. ABPVN thường đã có luật mới.
2. **Tìm nguồn quảng cáo:** thêm trang vào `tools/sitecheck/sites.txt` rồi chạy

   ```bash
   powershell -ExecutionPolicy Bypass -File tools/sitecheck/sitecheck.ps1 -Device 192.168.31.27:5555
   ```

   Xem `tools/sitecheck/out/report.md`: các request đã bị chặn (và bởi luật nào), ô quảng cáo còn thấy,
   tên miền bên thứ ba trang đã gọi, trang có bị chuyển đi sau khi bấm không, kèm ảnh chụp màn hình.
3. **Thêm luật** vào `app/src/main/assets/filters/luna.txt`, đúng mục, kèm ghi chú ngày và trang:
   - Chặn cả tên miền quảng cáo: `||ten-mien-quang-cao.com^`
   - Chặn một đường dẫn trên một trang: `||cdn.trang.com/ads/$script,domain=trang.com`
   - Ẩn một phần tử trên một trang: `trang.com##.banner-duoi`
   - Ẩn trên mọi trang tên `phimabc.*` (mọi đuôi tên miền): `phimabc.*##.ads-top`
   - Trang bị chặn nhầm: `@@||trang.com^$document`
4. **Viết test** cho trường hợp đó trong `app/src/test/.../FilterEngineTest.kt` nếu luật có dạng mới.
5. Push → CI chạy test và build APK → cài lên TV → chạy lại sitecheck.
6. (Nên làm) Báo luật cho ABPVN để cả cộng đồng được hưởng: https://abpvn.com

## Khi trang đổi cách làm quảng cáo

| Dấu hiệu trong báo cáo | Thường là | Sửa ở đâu |
|---|---|---|
| ❌ Bị chuyển sang trang khác sau khi bấm | script popunder mới | thêm tên miền script vào mục 1 của `luna.txt` |
| 🟡 Ô quảng cáo / banner dính còn thấy | banner mới | luật `##` cho trang đó (mục 4) |
| Lớp phủ vô hình > 0 | kiểu lớp phủ mới | điều chỉnh `OVERLAY_JS` (ngưỡng z-index, diện tích) |
| Video trong iframe không điều khiển được | trình phát dùng Shadow DOM / canvas | `VIDEO_BRIDGE_JS` |

## Thêm một danh sách lọc mới

Thêm một mục vào `FilterLists` trong `data/AdBlocker.kt` (tên, mô tả, URL). Danh sách sẽ tự hiện
trong Cài đặt, được tải về, cập nhật hằng ngày và có thể bật/tắt. Cú pháp hỗ trợ: xem đầu file
`data/filter/FilterEngine.kt`. Luật dạng scriptlet / procedural (`#?#`, `#$#`, `##+js`) được bỏ qua.

## Giới hạn hiện tại

- WebView không cho biết loại tài nguyên; Luna đoán theo phần mở rộng và header `Accept`. Luật có
  `$script` sẽ không khớp với script không có đuôi `.js` (chúng được coi là "không rõ loại" và chỉ
  khớp với luật script/xhr/other).
- `filters/luna.txt` chỉ cập nhật theo phiên bản app (repo đang private). Muốn cập nhật không cần
  cài lại, có thể đưa file lên một gist công khai và thêm URL vào mục `luna` trong `FilterLists`.
