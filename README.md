# Đếm Nhịp – máy đếm nhịp (metronome) cho kính Rokid RV101

Ứng dụng Android chạy **ngay trên kính** (không cần điện thoại khi dùng), vẽ riêng cho màn hình
đơn sắc 480×640 của RV101: nền đen, số BPM thật to, vòng tròn nháy theo phách, tiếng "tích"
phát qua loa kính. Nhịp được phát bằng AudioTrack theo mẫu âm thanh nên giữ đều, không trôi.

## Thao tác trên bàn cảm ứng gọng kính

| Thao tác | Tác dụng |
|---|---|
| Chạm 1 lần | Chạy / Dừng |
| Chạm 2 lần nhanh | Đổi loại nhịp: 2/4 → 3/4 → 4/4 → 6/8 |
| Vuốt lên / xuống | Tăng / giảm 5 BPM |
| Vuốt tới / lui | Tăng / giảm 1 BPM |
| Giữ lâu (Back) | Thoát ứng dụng |

Phạm vi 30–250 BPM. Phách mạnh (đầu ô nhịp, với 6/8 là phách 1 và 4) kêu cao hơn và nháy xanh,
phách thường nháy trắng. BPM và loại nhịp được nhớ lại cho lần mở sau.

## Lấy file APK bằng GitHub Actions (không cần cài gì trên máy)

1. Vào tab **Actions** → chọn workflow **Build APK** → **Run workflow** (hoặc chỉ cần push lên
   nhánh `main` là nó tự chạy).
2. Chờ ~3–5 phút, mở lần chạy đó, kéo xuống mục **Artifacts** → tải `DemNhip-RokidRV101`
   (file zip, bên trong là `DemNhip-RokidRV101.apk`).

## Build bằng Android Studio

Mở thư mục này bằng Android Studio (Hedgehog trở lên), chờ Gradle sync xong rồi
**Build → Build Bundle(s)/APK(s) → Build APK(s)**. File nằm ở
`app/build/outputs/apk/debug/app-debug.apk`.

## Cài APK vào kính

**Cách dễ nhất – qua app Hi Rokid trên điện thoại (không cần cáp):**

1. Chép file APK vào điện thoại đang ghép với kính.
2. Mở **Hi Rokid** → **Hộp công cụ (Toolbox)** → **Quản lý ứng dụng kính (Glasses app management)**
   → chọn file APK → cài. (Tên menu có thể hơi khác tuỳ phiên bản app.)
3. Trên kính, vào danh sách ứng dụng sẽ thấy **Đếm Nhịp**.

**Cách 2 – ADB (cần bật gỡ lỗi ADB trong Hi Rokid và cáp phát triển 5 chân, không phải cáp sạc 3 chân):**

```bash
adb devices                 # kính phải hiện trong danh sách
adb install -r DemNhip-RokidRV101.apk
adb shell am start -n vn.quyet.demnhip/.MainActivity
```

## Cấu trúc mã nguồn

- `MetronomeEngine.kt` – tạo tiếng tích và phát nhịp bằng AudioTrack (luồng riêng).
- `MetronomeView.kt` – vẽ giao diện tối ưu cho màn đơn sắc.
- `MainActivity.kt` – nhận phím D-pad từ bàn cảm ứng, lưu cài đặt.

Muốn đổi âm tiếng tích (cao/thấp, dài/ngắn) thì sửa hai dòng `makeClick(...)` trong
`MetronomeEngine.kt`; muốn thêm loại nhịp thì thêm vào danh sách `SIGNATURES`.
