# A-01 — Kiểm kê Backend và CI

## 1. Mục tiêu

Kiểm kê trạng thái hiện tại của Backend, xác định cấu trúc mã nguồn, công nghệ, cấu hình cơ sở dữ liệu, endpoint hiện có và các khoảng trống cần xử lý trước khi phát triển chức năng nghiệp vụ.

## 2. Môi trường và công nghệ

| Thành phần | Trạng thái |
|---|---|
| Java | 21 |
| Maven | Maven Wrapper (`./mvnw`) |
| Spring Boot | Được khai báo trong `backend/pom.xml` |
| Web | Spring Boot Web MVC |
| Persistence | Spring Data JPA |
| Database | PostgreSQL |
| Database migration | Flyway |
| Security | Spring Security |
| Validation | Spring Validation |
| Container | Docker Compose |

## 3. Cấu trúc mã nguồn

Mã nguồn Java hiện có:

`backend/src/main/java/com/fertilizer/backend/BackendApplication.java`

Đây là lớp khởi động ứng dụng Spring Boot. Tại thời điểm kiểm kê, chưa có các package controller, service, repository, entity hoặc DTO nghiệp vụ được xác nhận trong mã nguồn.

Tài nguyên hiện có:

`backend/src/main/resources/application.properties`

Chưa tìm thấy file migration SQL trong `backend/src/main/resources/db`.

## 4. REST API

Đã tìm kiếm các annotation `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping` và `@DeleteMapping` trong mã nguồn Java.

Kết quả: chưa tìm thấy endpoint REST nghiệp vụ.

Do đó, chưa thể lập danh sách endpoint nghiệp vụ hoặc xác nhận mức độ bao phủ kiểm thử API.

## 5. Cấu hình cơ sở dữ liệu

Ứng dụng sử dụng các biến môi trường:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`

Cấu hình datasource sử dụng PostgreSQL. Hibernate được cấu hình `ddl-auto=none`; Flyway được bật để quản lý migration.

Chưa có migration nghiệp vụ được kiểm kê. Cần thống nhất schema và migration với nhóm trước khi triển khai dữ liệu nghiệp vụ.

Không đưa mật khẩu, token hoặc thông tin bí mật vào tài liệu hay Git.

## 6. Kiểm thử

Đã chạy lệnh:

`./mvnw -B verify`

Kết quả lần chạy gần nhất được ghi nhận: `BUILD SUCCESS`.

Kết quả này xác nhận Maven hoàn thành các bước verify được cấu hình tại thời điểm chạy trên môi trường local. Chưa có bằng chứng trong tài liệu này về kết quả CI trên GitHub.

## 7. Khoảng trống cần xử lý

1. Chưa có endpoint REST nghiệp vụ.
2. Chưa có entity, repository, service và DTO nghiệp vụ được xác nhận.
3. Chưa có migration SQL nghiệp vụ.
4. Chưa kiểm kê được API giá bán, tồn kho, công nợ, trạng thái đơn hàng.
5. Chưa xác nhận cơ chế xác thực JWT và phân quyền USER/ADMIN đã được triển khai.
6. Chưa xác nhận CI trên GitHub chạy thành công.

Các mục trên là khoảng trống cần kiểm tra hoặc triển khai, không phải kết luận rằng những chức năng đó không tồn tại ở bất kỳ thành phần nào khác của toàn hệ thống.

## 8. Kế hoạch tiếp theo

- Hoàn thiện kiểm kê và tạo pull request A-01.
- Nhờ thành viên C review tài liệu, cấu hình build và CI.
- Phối hợp thành viên B để thống nhất hợp đồng tích hợp AI Agent.
- Thống nhất schema và migration trước khi tạo bảng nghiệp vụ.
- Bổ sung kiểm thử và CI theo phạm vi đã thống nhất.
