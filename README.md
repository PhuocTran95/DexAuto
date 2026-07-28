# DexAuto

DexAuto là ứng dụng giúp dùng Samsung DeX tiện hơn. Khi cắm điện thoại vào màn hình DeX, DexAuto hiển thị một thanh công cụ nhỏ trên màn hình để bạn mở nhanh các bộ ứng dụng quen dùng.

Ví dụ: bạn có thể lưu một bố cục gồm Zalo bên trái, Chrome bên phải, hoặc một bố cục khác gồm 3-4 ứng dụng cho công việc. Lần sau chỉ cần bấm một nút, DexAuto sẽ mở lại đúng nhóm ứng dụng đó.

Dự án đã được mở nguồn theo giấy phép Apache License 2.0.

## DexAuto làm được gì?

- Tạo các bố cục ứng dụng hay dùng, ví dụ chia đôi màn hình, chia ba, chia bốn hoặc mở một app toàn màn hình.
- Lưu nhiều bố cục khác nhau để dùng lại khi cần.
- Mở nhanh một bố cục đã lưu từ thanh công cụ nổi.
- Tự hiện thanh công cụ khi vào Samsung DeX.
- Cho phép đặt thanh công cụ ở cạnh trái, phải, trên hoặc dưới màn hình.
- Thu gọn thanh công cụ khi không dùng để đỡ vướng.
- Có màn hình nghỉ ngơi để che màn hình khi cần tạm dừng.
- Có popup QR chuyển khoản để mở nhanh mã QR thanh toán.
- Có tùy chọn nâng cao để giữ mức pin cố định hoặc khôi phục bố cục DeX, nếu máy có Root hoặc Shizuku.

## Cần gì để dùng?

- Android 11 trở lên.
- Điện thoại hoặc máy tính bảng Samsung có hỗ trợ DeX.
- Cấp quyền hiển thị trên ứng dụng khác để DexAuto hiện thanh công cụ nổi.
- Trên Android 13 trở lên, nên cấp quyền thông báo để DexAuto hiển thị trạng thái chạy nền.
- Root hoặc Shizuku chỉ cần cho các tính năng nâng cao. Các tính năng mở layout cơ bản vẫn có thể dùng mà không cần Root/Shizuku.

## Ứng dụng sẽ hỏi quyền gì?

DexAuto cần một số quyền để hoạt động ổn định trên DeX:

- Hiển thị trên ứng dụng khác: để thanh công cụ nổi xuất hiện trên màn hình DeX.
- Thông báo: để Android cho phép DexAuto chạy nền ổn định.
- Không tối ưu pin: để hệ thống ít tự tắt DexAuto khi đang dùng DeX.
- Khởi động cùng máy: để DexAuto kiểm tra lại trạng thái sau khi thiết bị bật lại.
- Internet: để tải dữ liệu phục vụ tính năng QR chuyển khoản.

Tên quyền kỹ thuật tương ứng: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`.

## Dành cho developer

Clone repo và build bằng Gradle wrapper:

```bash
./gradlew :app:assembleDebug
```

Build release:

```bash
./gradlew :app:assembleRelease
```

APK debug nằm trong:

```text
app/build/outputs/apk/debug/
```

APK release nằm trong:

```text
app/build/outputs/apk/release/
```

## Trạng thái open source

DexAuto không yêu cầu license/activation để chạy. Người dùng có thể build, cài đặt, sửa đổi và phân phối lại theo điều khoản của Apache License 2.0.

## License

Copyright 2026 DexAuto contributors

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

Ae thấy ok thì giúp mình 1 Star hay ủng hộ ít token để support ae nha <3

<img width="150" height="190" alt="image" src="https://github.com/user-attachments/assets/1e659752-d876-4702-bf38-2665d1b09706" />

