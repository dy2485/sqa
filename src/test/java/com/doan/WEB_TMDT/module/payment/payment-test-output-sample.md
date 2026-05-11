# Payment Module Test Output Sample

| ID | Chức năng | Hàm/Phương thức | Testcase | Kết quả mong muốn | Kết quả thực tế |
|---|---|---|---|---|---|
| PAY-001 | Tạo thanh toán | PaymentServiceImpl.createPayment | Khi người dùng tạo thanh toán cho đơn hợp lệ và tài khoản nhận tiền mặc định đã sẵn sàng | Hệ thống tạo payment thành công và trả thông tin QR đầy đủ | Pass |
| PAY-002 | Tạo thanh toán | PaymentServiceImpl.createPayment | Khi người dùng gửi yêu cầu thanh toán với mã đơn không tồn tại | Hệ thống từ chối và thông báo không tìm thấy đơn hàng | Pass |
| PAY-003 | Tạo thanh toán | PaymentServiceImpl.createPayment | Khi phiên người dùng không còn hợp lệ trong lúc tạo thanh toán | Hệ thống từ chối và thông báo không tìm thấy người dùng | Pass |
| PAY-004 | Phân quyền thanh toán | PaymentServiceImpl.createPayment | Khi người dùng cố thanh toán một đơn hàng không thuộc quyền sở hữu | Hệ thống chặn thao tác vì không đủ quyền | Pass |
| PAY-005 | Chống trùng giao dịch | PaymentServiceImpl.createPayment | Khi cùng một đơn hàng đã có giao dịch thanh toán trước đó | Hệ thống không tạo thêm payment mới | Pass |
| PAY-006 | Kiểm tra số tiền thanh toán | PaymentServiceImpl.createPayment | Khi số tiền gửi lên không khớp với tổng tiền đơn hàng | Hệ thống từ chối và báo sai số tiền thanh toán | Pass |
| PAY-007 | Nghiệp vụ công nợ | SupplierPayableServiceImpl.makePayment | Khi kế toán ghi nhận thanh toán công nợ với số tiền bằng 0 | Hệ thống phải từ chối giao dịch vì số tiền không hợp lệ | Fail |
| PAY-008 | Nghiệp vụ công nợ | SupplierPayableServiceImpl.makePayment | Khi kế toán ghi nhận thanh toán công nợ với số tiền âm | Hệ thống phải từ chối giao dịch vì số tiền không hợp lệ | Fail |
| PAY-009 | Tài khoản nhận tiền | PaymentServiceImpl.createPayment | Khi chưa cấu hình tài khoản mặc định nhưng người dùng vẫn tạo thanh toán | Hệ thống dùng dữ liệu dự phòng và vẫn trả được thông tin thanh toán | Pass |
| PAY-010 | Chính sách tài khoản nhận tiền | PaymentServiceImpl.createPayment | Khi không có tài khoản mặc định và chính sách yêu cầu bắt buộc cấu hình trước khi thu tiền | Hệ thống phải chặn tạo payment cho đến khi cấu hình đủ | Fail |
| PAY-011 | Tra cứu payment | PaymentServiceImpl.getPaymentByCode | Khi người dùng tra cứu payment bằng mã hợp lệ | Hệ thống trả đúng thông tin payment | Pass |
| PAY-012 | Tra cứu payment | PaymentServiceImpl.getPaymentByCode | Khi người dùng tra cứu payment bằng mã không tồn tại | Hệ thống báo không tìm thấy payment | Pass |
| PAY-013 | Xử lý webhook | PaymentServiceImpl.handleSepayWebhook | Khi webhook gửi nội dung không chứa mã thanh toán | Hệ thống từ chối xử lý webhook | Pass |
| PAY-014 | Xử lý webhook | PaymentServiceImpl.handleSepayWebhook | Khi webhook có mã nhưng không tồn tại payment tương ứng trong hệ thống | Hệ thống trả lỗi không tìm thấy thanh toán | Pass |
| PAY-015 | Xử lý webhook lặp | PaymentServiceImpl.handleSepayWebhook | Khi webhook được gửi lặp lại cho payment đã xử lý thành công | Hệ thống xử lý idempotent, không cập nhật trùng | Pass |
| PAY-016 | Đối soát số tiền webhook | PaymentServiceImpl.handleSepayWebhook | Khi số tiền từ webhook không khớp số tiền payment đã tạo | Hệ thống từ chối cập nhật trạng thái thanh toán | Pass |
| PAY-017 | Chuẩn ngoại lệ webhook | PaymentServiceImpl.handleSepayWebhook | Khi có sai lệch amount và yêu cầu chuẩn hóa theo cơ chế exception nghiệp vụ | Hệ thống phải ném ngoại lệ đúng chuẩn để upstream xử lý | Fail |
| PAY-018 | Quá hạn thanh toán | PaymentServiceImpl.handleSepayWebhook | Khi webhook đến sau thời gian hết hạn thanh toán | Hệ thống đánh dấu expired và từ chối xác nhận thanh toán | Pass |
| PAY-019 | Kiểm tra trạng thái realtime | PaymentServiceImpl.checkPaymentStatus | Khi payment đang pending nhưng đã quá hạn lúc người dùng kiểm tra trạng thái | Hệ thống tự expire payment và kích hoạt hủy đơn phù hợp | Pass |
| PAY-020 | Job xử lý quá hạn định kỳ | PaymentServiceImpl.expireOldPayments | Khi job định kỳ quét thấy danh sách payment đã quá hạn | Hệ thống expire đúng và hủy đơn đúng điều kiện nghiệp vụ | Pass |
| PAY-021 | Validation API webhook | PaymentController.handleSepayWebhook | Khi API nhận payload webhook rỗng hoặc thiếu dữ liệu bắt buộc | API phải từ chối sớm và trả lỗi hợp lệ | Fail |
| PAY-022 | Trạng thái tài khoản ngân hàng | BankAccountServiceImpl.toggleActive | Khi người vận hành thao tác tắt tài khoản đang là mặc định nhận tiền | Hệ thống phải chặn thao tác để tránh gián đoạn thu tiền | Fail |
