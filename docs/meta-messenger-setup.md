# Kết nối Messenger với backend khi phát triển

Tài liệu cho nhóm: dựng lại môi trường nhận và trả lời tin nhắn Messenger trên máy cá nhân.
Không chứa bí mật nào; mọi giá trị nhạy cảm nằm trong file `.env` (không commit).

## Cần có
- Một Fanpage và một Meta App (use case "Engage with customers on Messenger"); người làm là Admin của app.
- `cloudflared` (cài theo hướng dẫn của Cloudflare cho Ubuntu) để đưa backend ra Internet khi phát triển.

## Biến môi trường (file `.env`, không commit)
| Biến | Lấy ở đâu |
|---|---|
| `FB_VERIFY_TOKEN` | Chuỗi tự đặt (ví dụ `openssl rand -hex 16`), nhập giống nhau ở `.env` và ô Verify token của Meta |
| `FB_APP_SECRET` | App settings → Basic → App secret |
| `FB_PAGE_ACCESS_TOKEN` | Use case Messenger → Generate access tokens (hoặc Graph API Explorer) |

## Chạy
1. Tab 1: `set -a; source .env; set +a`, rồi `cd backend && ./mvnw spring-boot:run`. Chờ thấy `Started BackendApplication`.
2. Tab 2: `cloudflared tunnel --protocol http2 --url http://127.0.0.1:8080`, chép địa chỉ `https://...trycloudflare.com`.
3. Kiểm tra bằng trình duyệt: `<địa chỉ>/webhooks/facebook?hub.mode=subscribe&hub.verify_token=<FB_VERIFY_TOKEN>&hub.challenge=12345` phải hiện `12345`.
4. Trong Meta (use case Messenger → Configure webhooks): Callback URL = `<địa chỉ>/webhooks/facebook`, Verify token = `FB_VERIFY_TOKEN`, bấm Verify and save.
5. Mục Generate access tokens: Connect Fanpage, Add Subscriptions, tích trường `messages`.
6. Lấy Page Access Token, dán vào `.env`, khởi động lại backend.
7. Nhắn Fanpage bằng tài khoản có vai trò trong app; bot phải trả lời. Bảng `message` có cả dòng `IN` và `OUT`.

## Lưu ý khi vận hành
- Địa chỉ trycloudflare đổi mỗi lần chạy lại `cloudflared`: phải cập nhật Callback URL ở Meta mỗi lần.
- Đường hầm không tên không có cam kết hoạt động; mất kết nối lâu thì bị xóa (log báo `Tunnel not found`), phải tạo lại và cập nhật Callback URL.
- Laptop ngủ hoặc mất mạng thì bot không nhận được tin. Cắm sạc, tắt chế độ ngủ, dùng mạng ổn định.
- Nếu WSL báo lỗi DNS (`i/o timeout`, `Could not resolve host`), đặt DNS cố định trong WSL (`/etc/wsl.conf` với `generateResolvConf = false`, `/etc/resolv.conf` với `1.1.1.1` và `8.8.8.8`) rồi `wsl --shutdown`.
- Ở Development mode chỉ người có vai trò trong app (Admin/Developer/Tester) nhắn được. Thêm người thử ở App roles → Roles.

## Xử lý lỗi nhanh
| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Verify and save báo lỗi | Backend hoặc đường hầm tắt; sai verify token; thiếu đuôi `/webhooks/facebook` | Làm lại bước kiểm tra bằng trình duyệt ở mục Chạy |
| Nhắn không có phản ứng | Chưa subscribe `messages`; sai Page; người nhắn chưa có vai trò | Làm lại bước 5; kiểm tra App roles |
| Log `invalid signature` | `FB_APP_SECRET` sai hoặc backend chưa nạp lại `.env` | Sửa `.env`, khởi động lại backend |
| Log `Chưa cấu hình FB_PAGE_ACCESS_TOKEN` | Token trống | Điền `.env`, khởi động lại backend |
| Log `Send API trả lỗi HTTP 400/401/403` | Token sai hoặc hết hạn; người nhận chưa có vai trò | Lấy lại token, khởi động lại backend |

## Bảo mật
- Không đưa App secret, Page token, verify token hoặc khóa API lên GitHub hay chat.
- Không log nội dung tin nhắn của khách.
