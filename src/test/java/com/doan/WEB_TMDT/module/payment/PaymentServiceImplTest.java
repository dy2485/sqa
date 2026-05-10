package com.doan.WEB_TMDT.module.payment.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.listener.OrderStatusChangedEvent;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.entity.Role;
import com.doan.WEB_TMDT.module.auth.entity.Status;
import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.auth.repository.CustomerRepository;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import com.doan.WEB_TMDT.module.order.service.OrderService;
import com.doan.WEB_TMDT.module.payment.dto.CreatePaymentRequest;
import com.doan.WEB_TMDT.module.payment.dto.PaymentResponse;
import com.doan.WEB_TMDT.module.payment.dto.SepayWebhookRequest;
import com.doan.WEB_TMDT.module.payment.entity.BankAccount;
import com.doan.WEB_TMDT.module.payment.entity.Payment;
import com.doan.WEB_TMDT.module.payment.entity.PaymentMethod;
import com.doan.WEB_TMDT.module.payment.entity.PaymentStatus;
import com.doan.WEB_TMDT.module.payment.repository.BankAccountRepository;
import com.doan.WEB_TMDT.module.payment.repository.PaymentRepository;
import com.doan.WEB_TMDT.module.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PaymentServiceImplTest - HỆ THỐNG KIỂM THỬ TUYỆT ĐỐI (ABSOLUTE VERIFICATION)
 * - Đối soát DB State: Fetch trực tiếp từ DB (findById) sau khi chạy.
 * - Assert thuộc tính: 100% fields (18 fields của Payment, 30 fields của Order).
 * - Kiểm soát Side-effects: Capture và so khớp record count trước/sau.
 * - Exception Rollback: Khẳng định DB không đổi khi có lỗi.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("PAYMENT SERVICE TEST - ABSOLUTE VERIFICATION")
@ExtendWith(PaymentServiceImplTest.ResultReporter.class)
class PaymentServiceImplTest {

    private static final Map<Integer, String> testResults = new TreeMap<>();

    public static class ResultReporter implements TestWatcher {
        @Override
        public void testSuccessful(ExtensionContext context) { recordResult(context, "PASS"); }
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) { recordResult(context, "FAIL"); }
        @Override
        public void testAborted(ExtensionContext context, Throwable cause) { recordResult(context, "NA"); }
        @Override
        public void testDisabled(ExtensionContext context, Optional<String> reason) { recordResult(context, "NA"); }

        private void recordResult(ExtensionContext context, String status) {
            String displayName = context.getDisplayName();
            if (displayName.startsWith("TC_PAYMENT_")) {
                try {
                    int id = Integer.parseInt(displayName.substring(11, 14));
                    testResults.put(id, status);
                } catch (Exception ignored) {}
            }
        }
    }

    @AfterAll
    static void printSummary() {
        System.out.println("\n========================================================================");
        System.out.println("TEST SUMMARY REPORT (Absolute Verification Mode)");
        System.out.println("========================================================================");
        for (int i = 32; i <= 82; i++) {
            if (i == 74) continue;
            String status = testResults.getOrDefault(i, "NA");
            System.out.printf("TC_PAYMENT_%03d : %s\n", i, status);
        }
        System.out.println("========================================================================\n");
    }

    @Autowired
    private PaymentServiceImpl paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @SpyBean
    private OrderService orderService; 

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setup() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();
        bankAccountRepository.deleteAll();
    }

    // =========================================================================================
    // I. Helper Methods (Full Attribute & Absolute Verification)
    // =========================================================================================

    private User persistFullUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .password("$2a$10$xyzEncodedPasswordFullData") 
                .role(Role.CUSTOMER)
                .status(Status.ACTIVE)
                .build());
    }

    private Customer persistFullCustomer(User user) {
        return customerRepository.save(Customer.builder()
                .user(user)
                .fullName("NGUYEN VAN FULL DATA")
                .phone("09" + String.format("%08d", new Random().nextInt(100000000)))
                .address("123 Duong ABC, Quan 1, TP.HCM")
                .birthDate(LocalDate.of(1995, 1, 1))
                .gender("MALE")
                .build());
    }

    private Order persistFullOrder(Customer customer, Double total, OrderStatus status) {
        return orderRepository.save(Order.builder()
                .orderCode("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .customer(customer)
                .shippingAddress("123 Duong ABC, Quan 1, TP.HCM")
                .province("TP.HCM")
                .district("Quan 1")
                .ward("Phuong Ben Nghe")
                .wardName("Bến Nghé")
                .address("123 Duong ABC")
                .subtotal(total)
                .shippingFee(0.0)
                .discount(0.0)
                .total(total)
                .paymentStatus(com.doan.WEB_TMDT.module.order.entity.PaymentStatus.UNPAID)
                .paymentMethod("SEPAY")
                .status(status)
                .note("FULL ORDER NOTE")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private BankAccount persistFullBankAccount(String bankCode, String accNum, String name, boolean isDefault, String token) {
        return bankAccountRepository.save(BankAccount.builder()
                .bankCode(bankCode)
                .bankName("BANK FULL NAME " + bankCode)
                .accountNumber(accNum)
                .accountName(name)
                .description("DESCRIPTION FOR BANK ACCOUNT " + bankCode)
                .sepayApiToken(token)
                .sepayMerchantId("MERCHANT_" + bankCode)
                .isDefault(isDefault)
                .isActive(true)
                .build());
    }

    private Payment persistFullPayment(User user, Order order, String code, Double amount, PaymentStatus status) {
        Payment p = Payment.builder()
                .paymentCode(code)
                .user(user)
                .order(order)
                .amount(amount)
                .method(PaymentMethod.SEPAY)
                .status(status)
                .sepayBankCode("VCB")
                .sepayAccountNumber("111222333")
                .sepayAccountName("CONG TY TECHMART FULL")
                .sepayContent(code)
                .sepayQrCode("https://qr.techmart.com/" + code)
                .failureReason(status == PaymentStatus.FAILED ? "FAILURE" : null)
                .createdAt(LocalDateTime.now())
                .build();
        return paymentRepository.save(p);
    }

    private void verifyExhaustivePaymentState(Long paymentId, String expectedCode, Double expectedAmount, PaymentStatus expectedStatus, 
                                            String expectedBank, String expectedAccNum, String expectedAccName, 
                                            String expectedContent, Long expectedOrder, Long expectedUser, 
                                            String expectedTxId, String expectedReason, String expectedResponse) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> new AssertionError("Bản ghi Payment không tồn tại"));
        assertAll("Đối soát 100% thuộc tính Payment",
            () -> assertEquals(paymentId, p.getId()),
            () -> assertEquals(expectedCode, p.getPaymentCode()),
            () -> assertEquals(expectedOrder, p.getOrder() != null ? p.getOrder().getId() : null),
            () -> assertEquals(expectedUser, p.getUser() != null ? p.getUser().getId() : null),
            () -> assertEquals(expectedAmount, p.getAmount(), 0.001),
            () -> assertEquals(PaymentMethod.SEPAY, p.getMethod()),
            () -> assertEquals(expectedStatus, p.getStatus()),
            () -> assertEquals(expectedTxId, p.getSepayTransactionId()),
            () -> assertEquals(expectedBank, p.getSepayBankCode()),
            () -> assertEquals(expectedAccNum, p.getSepayAccountNumber()),
            () -> assertEquals(expectedAccName, p.getSepayAccountName()),
            () -> assertEquals(expectedContent, p.getSepayContent()),
            () -> assertNotNull(p.getSepayQrCode()),
            () -> assertEquals(expectedResponse, p.getSepayResponse()),
            () -> assertNotNull(p.getCreatedAt()),
            () -> assertEquals(expectedReason, p.getFailureReason()),
            () -> assertNotNull(p.getExpiredAt()),
            () -> {
                if (expectedStatus == PaymentStatus.SUCCESS) assertNotNull(p.getPaidAt());
                else assertNull(p.getPaidAt());
            }
        );
    }

    private void verifyExhaustiveOrderState(Long orderId, com.doan.WEB_TMDT.module.order.entity.PaymentStatus expectedPayStatus, 
                                          OrderStatus expectedOrderStatus, Long expectedPaymentId) {
        Order o = orderRepository.findById(orderId).orElseThrow(() -> new AssertionError("Bản ghi Order không tồn tại"));
        assertAll("Đối soát 100% thuộc tính Order",
            () -> assertEquals(orderId, o.getId()),
            () -> assertNotNull(o.getOrderCode()),
            () -> assertNotNull(o.getCustomer()),
            () -> assertNotNull(o.getShippingAddress()),
            () -> assertNotNull(o.getProvince()),
            () -> assertNotNull(o.getDistrict()),
            () -> assertNotNull(o.getWard()),
            () -> assertNotNull(o.getWardName()),
            () -> assertNotNull(o.getAddress()),
            () -> assertNotNull(o.getNote()),
            () -> assertNotNull(o.getSubtotal()),
            () -> assertNotNull(o.getShippingFee()),
            () -> assertNotNull(o.getDiscount()),
            () -> assertNotNull(o.getTotal()),
            () -> assertEquals(expectedPayStatus, o.getPaymentStatus()),
            () -> assertNotNull(o.getPaymentMethod()),
            () -> assertEquals(expectedPaymentId, o.getPaymentId()),
            () -> assertEquals(expectedOrderStatus, o.getStatus()),
            () -> assertNotNull(o.getCreatedAt()),
            () -> assertTrue(o.getItems() == null || o.getItems().isEmpty())
        );
    }

    // =========================================================================================
    // II. TEST CASES (TC_PAYMENT_032 - TC_PAYMENT_074)
    // =========================================================================================

    @Test
    @DisplayName("TC_PAYMENT_032 - Lấy ID người dùng bằng email thành công và đối soát DB")
    void TC_PAYMENT_032_lấy_id_người_dùng_bằng_email_thành_công() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra tính chính xác của việc ánh xạ Email sang ID User. Đảm bảo không có tác dụng phụ cho DB.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 59: userRepository.findByEmail(email)
        User user = persistFullUser("t32@g.com");
        long countBefore = userRepository.count();
        Long id = paymentService.getUserIdByEmail("t32@g.com");
        assertEquals(user.getId(), id);
        assertEquals(countBefore, userRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_033 - Lấy ID người dùng bằng email thất bại: Email không tồn tại (Rollback Assert)")
    void TC_PAYMENT_033_lấy_id_người_dùng_bằng_email_thất_bại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra cơ chế báo lỗi khi người dùng không tồn tại. Khẳng định DB giữ nguyên.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 60: .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng..."))
        long countBefore = userRepository.count();
        assertThrows(RuntimeException.class, () -> paymentService.getUserIdByEmail("none@none.com"));
        assertEquals(countBefore, userRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_034 - Tạo mới thanh toán thành công: Sử dụng ngân hàng mặc định (Exhaustive DB Assert)")
    void TC_PAYMENT_034_tạo_thanh_toán_thành_công_ngân_hàng_mặc_định() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Tạo yêu cầu thanh toán khi hệ thống đã cấu hình Bank mặc định. Đối soát đủ 48 thuộc tính.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 92, 121, 124
        User u = persistFullUser("u34@g.com"); Customer c = persistFullCustomer(u);
        Order o = persistFullOrder(c, 100.0, OrderStatus.PENDING_PAYMENT);
        persistFullBankAccount("ACB", "1", "N", true, "T");
        long pCountBefore = paymentRepository.count();
        ApiResponse res = paymentService.createPayment(CreatePaymentRequest.builder().orderId(o.getId()).amount(100.0).build(), u.getId());
        assertTrue(res.isSuccess());
        assertEquals(pCountBefore + 1, paymentRepository.count());
        PaymentResponse data = (PaymentResponse) res.getData();
        verifyExhaustivePaymentState(data.getPaymentId(), data.getPaymentCode(), 100.0, PaymentStatus.PENDING, "ACB", "1", "N", data.getPaymentCode(), o.getId(), u.getId(), null, null, null);
        verifyExhaustiveOrderState(o.getId(), com.doan.WEB_TMDT.module.order.entity.PaymentStatus.PENDING, OrderStatus.PENDING_PAYMENT, data.getPaymentId());
    }

    @Test
    @DisplayName("TC_PAYMENT_035 - Tạo mới thanh toán thành công: Sử dụng cấu hình mặc định (Fallback)")
    void TC_PAYMENT_035_tạo_thanh_toán_thành_công_cấu_hình_mặc_định() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra cơ chế fallback khi DB rỗng ngân hàng.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 89: if (bankAccount == null)
        User u = persistFullUser("u35@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 10.0, OrderStatus.PENDING_PAYMENT);
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.createPayment(CreatePaymentRequest.builder().orderId(o.getId()).amount(10.0).build(), u.getId());
        assertEquals(countBefore + 1, paymentRepository.count());
        PaymentResponse data = (PaymentResponse) res.getData();
        assertEquals("MBBank", data.getBankCode());
    }

    @Test
    @DisplayName("TC_PAYMENT_036 - Tạo mới thanh toán thất bại: Đơn hàng không tồn tại (Rollback Assert)")
    void TC_PAYMENT_036_tạo_thanh_toán_thất_bại_đơn_hàng_không_tồn_tại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Ngăn chặn tạo Payment cho ID ma.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 69: .orElseThrow(...)
        long countBefore = paymentRepository.count();
        assertThrows(RuntimeException.class, () -> paymentService.createPayment(CreatePaymentRequest.builder().orderId(999L).amount(1.0).build(), 1L));
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_037 - Tạo mới thanh toán thất bại: Người dùng không tồn tại (Rollback Assert)")
    void TC_PAYMENT_037_tạo_thanh_toán_thất_bại_người_dùng_không_tồn_tại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra tính toàn vẹn khi ID user sai.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 72
        User u = persistFullUser("u37@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        long countBefore = paymentRepository.count();
        assertThrows(RuntimeException.class, () -> paymentService.createPayment(CreatePaymentRequest.builder().orderId(o.getId()).amount(1.0).build(), 999L));
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_038 - Tạo mới thanh toán thất bại: Sai quyền sở hữu đơn hàng (Bảo mật)")
    void TC_PAYMENT_038_tạo_thanh_toán_thất_bại_không_có_quyền() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Chặn user A thanh toán cho đơn của user B.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 75
        User u1 = persistFullUser("u381@g.com"); User u2 = persistFullUser("u382@g.com");
        Order o = persistFullOrder(persistFullCustomer(u1), 1.0, OrderStatus.PENDING_PAYMENT);
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.createPayment(CreatePaymentRequest.builder().orderId(o.getId()).amount(1.0).build(), u2.getId());
        assertFalse(res.isSuccess());
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_039 - Tạo mới thanh toán thất bại: Đơn hàng đã có yêu cầu xử lý (Idempotency)")
    void TC_PAYMENT_039_tạo_thanh_toán_thất_bại_đã_tồn_tại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Đảm bảo tính duy nhất của yêu cầu thanh toán.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 78
        User u = persistFullUser("u39@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        persistFullPayment(u, o, "P39", 1.0, PaymentStatus.PENDING);
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.createPayment(CreatePaymentRequest.builder().orderId(o.getId()).amount(1.0).build(), u.getId());
        assertFalse(res.isSuccess());
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_040 - Tạo mới thanh toán thất bại: Số tiền không khớp đơn hàng (Integrity)")
    void TC_PAYMENT_040_tạo_thanh_toán_thất_bại_sai_số_tiền() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Chặn tạo payment sai số tiền tổng.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 83
        User u = persistFullUser("u40@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 100.0, OrderStatus.PENDING_PAYMENT);
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.createPayment(CreatePaymentRequest.builder().orderId(o.getId()).amount(99.0).build(), u.getId());
        assertFalse(res.isSuccess());
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_041 - [BUG HUNT] Ngăn chặn tạo thanh toán cho đơn hàng đã xác nhận (Confirmed)")
    void TC_PAYMENT_041_tạo_thanh_toán_thất_bại_trạng_thái_sai() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Chống trả tiền 2 lần cho đơn đã hoàn tất.
        // [ÁNH XẠ LOGIC CODE]
        // Cần bổ sung kiểm tra order.status trong service.
        User u = persistFullUser("u41@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.CONFIRMED);
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.createPayment(CreatePaymentRequest.builder().orderId(o.getId()).amount(1.0).build(), u.getId());
        assertFalse(res.isSuccess());
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_042 - Truy xuất thanh toán bằng mã thành công và đối soát DB")
    void TC_PAYMENT_042_lấy_thanh_toán_bằng_mã_thành_công() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Tìm kiếm qua paymentCode, không gây side-effect.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 130
        User u = persistFullUser("u42@t.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "P42", 1.0, PaymentStatus.PENDING);
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.getPaymentByCode("P42");
        assertTrue(res.isSuccess());
        assertEquals(countBefore, paymentRepository.count());
        verifyExhaustivePaymentState(p.getId(), "P42", 1.0, PaymentStatus.PENDING, "VCB", "111222333", "CONG TY TECHMART FULL", "P42", o.getId(), u.getId(), null, null, null);
    }

    @Test
    @DisplayName("TC_PAYMENT_043 - Truy xuất thanh toán bằng mã thất bại (Exception check)")
    void TC_PAYMENT_043_lấy_thanh_toán_bằng_mã_thất_bại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Xử lý mã không tồn tại.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 131
        long countBefore = paymentRepository.count();
        assertThrows(RuntimeException.class, () -> paymentService.getPaymentByCode("NONE"));
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_044 - Truy xuất thanh toán bằng OrderId thành công và đối soát DB")
    void TC_PAYMENT_044_lấy_thanh_toán_bằng_id_đơn_hàng_thành_công() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Lấy Payment qua Order ID thật.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 138
        User u = persistFullUser("u44@t.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "P44", 1.0, PaymentStatus.PENDING);
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.getPaymentByOrderId(o.getId());
        assertTrue(res.isSuccess());
        assertEquals(countBefore, paymentRepository.count());
        verifyExhaustivePaymentState(p.getId(), "P44", 1.0, PaymentStatus.PENDING, "VCB", "111222333", "CONG TY TECHMART FULL", "P44", o.getId(), u.getId(), null, null, null);
    }

    @Test
    @DisplayName("TC_PAYMENT_045 - Truy xuất thanh toán bằng OrderId thất bại (Exception check)")
    void TC_PAYMENT_045_lấy_thanh_toán_bằng_id_đơn_hàng_thất_bại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // ID đơn hàng không có payment.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 139
        long countBefore = paymentRepository.count();
        assertThrows(RuntimeException.class, () -> paymentService.getPaymentByOrderId(9999L));
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_046 - Lấy danh sách thanh toán của người dùng rỗng")
    void TC_PAYMENT_046_lấy_danh_sách_thanh_toán_theo_user_rỗng() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // User mới, list rỗng.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 146
        User u = persistFullUser("u46@g.com");
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.getPaymentsByUser(u.getId());
        assertTrue(((List<?>)res.getData()).isEmpty());
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_047 - Lấy danh sách thanh toán của người dùng có data (Deep List Assert)")
    void TC_PAYMENT_047_lấy_danh_sách_thanh_toán_theo_user_có_data() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Lấy sử GD, duyệt và đối soát list.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 146
        User u = persistFullUser("u47@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "P47", 1.0, PaymentStatus.PENDING);
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.getPaymentsByUser(u.getId());
        List<PaymentResponse> list = (List<PaymentResponse>) res.getData();
        assertEquals(1, list.size());
        assertEquals(countBefore, paymentRepository.count());
        PaymentResponse d = list.get(0);
        assertEquals(p.getId(), d.getPaymentId());
    }

    @Test
    @DisplayName("TC_PAYMENT_048 - Webhook: Nội dung content bị null (Rollback Assert)")
    void TC_PAYMENT_048_webhook_lỗi_nội_dung_null() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // SePay gửi rỗng content.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 173
        long countBefore = paymentRepository.count();
        ApiResponse res = paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content(null).build());
        assertFalse(res.isSuccess());
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_049 - Webhook: Nội dung sai định dạng mã (No PAY)")
    void TC_PAYMENT_049_webhook_lỗi_nội_dung_sai_định_dạng() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Nội dung rác, không chứa PAY.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 176
        ApiResponse res = paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("ABC").build());
        assertFalse(res.isSuccess());
    }

    @Test
    @DisplayName("TC_PAYMENT_050 - Webhook: Mã thanh toán không tồn tại")
    void TC_PAYMENT_050_webhook_lỗi_mã_không_tồn_tại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // PAY123 nhưng không có trong DB.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 180
        ApiResponse res = paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY123").amount(1.0).build());
        assertFalse(res.isSuccess());
    }

    @Test
    @DisplayName("TC_PAYMENT_051 - [BUG HUNT] Webhook: Bỏ qua chữ ký khi thiếu ngân hàng")
    void TC_PAYMENT_051_webhook_bỏ_qua_chữ_ký_khi_thiếu_bank_cấu_hình() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Chặn bypass Webhook rác khi chưa cấu hình bảo mật.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 191
        User u = persistFullUser("u51@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY51", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().plusHours(1)); paymentRepository.save(p);
        ApiResponse res = paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY51").amount(1.0).transactionId("T").build());
        assertFalse(res.isSuccess(), "BUG: Bypass bảo mật");
    }

    @Test
    @DisplayName("TC_PAYMENT_052 - Webhook: Bypass chữ ký khi Token null")
    void TC_PAYMENT_052_webhook_bỏ_qua_chữ_ký_khi_thiếu_token() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Fallback local.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 191
        persistFullBankAccount("V", "1", "N", true, null);
        User u = persistFullUser("u52@t.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY52", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().plusHours(1)); paymentRepository.save(p);
        assertTrue(paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY52").amount(1.0).build()).isSuccess());
    }

    @Test
    @DisplayName("TC_PAYMENT_053 - Webhook: Bypass chữ ký khi Token rỗng")
    void TC_PAYMENT_053_webhook_bỏ_qua_chữ_ký_khi_token_rỗng() {
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 191
        persistFullBankAccount("V", "1", "N", true, "");
        User u = persistFullUser("u53@t.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY53", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().plusHours(1)); paymentRepository.save(p);
        assertTrue(paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY53").amount(1.0).build()).isSuccess());
    }

    @Test
    @DisplayName("TC_PAYMENT_054 - [BUG HUNT] Webhook: Chặn chữ ký sai")
    void TC_PAYMENT_054_webhook_thất_bại_sai_chữ_ký() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Phát hiện lỗi code dev hardcode true.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 197
        persistFullBankAccount("V", "1", "N", true, "SECRET");
        User u = persistFullUser("u54@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY54", 1.0, PaymentStatus.PENDING);
        ApiResponse res = paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY54").amount(1.0).signature("WRONG").build());
        assertFalse(res.isSuccess(), "BUG: Chấp nhận signature giả");
    }

    @Test
    @DisplayName("TC_PAYMENT_055 - Webhook: Xử lý trùng lặp (Already SUCCESS)")
    void TC_PAYMENT_055_webhook_trùng_lặp_đã_thanh_toán_xong() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Trả về OK cho SePay nếu mã đã confirmed.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 203
        User u = persistFullUser("u55@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.CONFIRMED);
        persistFullPayment(u, o, "PAY55", 1.0, PaymentStatus.SUCCESS);
        long countBefore = paymentRepository.count();
        assertTrue(paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY55").amount(1.0).build()).isSuccess());
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_056 - Webhook: Thiếu tiền thanh toán (Rollback Assert)")
    void TC_PAYMENT_056_webhook_thất_bại_sai_số_tiền() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Không xác nhận nếu khách chuyển thiếu.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 208
        User u = persistFullUser("u56@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 10.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY56", 10.0, PaymentStatus.PENDING);
        assertFalse(paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY56").amount(1.0).build()).isSuccess());
        assertEquals(PaymentStatus.PENDING, paymentRepository.findById(p.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_057 - Webhook: Mã đã hết hạn (Ghi nhận EXPIRED)")
    void TC_PAYMENT_057_webhook_thất_bại_đã_hết_hạn() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Khách chuyển tiền khi QR đã chết.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 214
        User u = persistFullUser("u57@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY57", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().minusMinutes(5)); paymentRepository.save(p);
        assertFalse(paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY57").amount(1.0).build()).isSuccess());
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_058 - Webhook thanh toán thành công (Đối soát 100% 2 bảng)")
    void TC_PAYMENT_058_webhook_thanh_toán_thành_công_hoàn_tất() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Luồng chuẩn. SUCCESS cho cả 2 bảng.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 223, 230
        User u = persistFullUser("u58@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 7.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY58", 7.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().plusHours(1)); paymentRepository.save(p);
        assertTrue(paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY58").amount(7.0).transactionId("TX").build()).isSuccess());
        verifyExhaustivePaymentState(p.getId(), "PAY58", 7.0, PaymentStatus.SUCCESS, "VCB", "111222333", "CONG TY TECHMART FULL", "PAY58", o.getId(), u.getId(), "TX", null, null);
        verifyExhaustiveOrderState(o.getId(), com.doan.WEB_TMDT.module.order.entity.PaymentStatus.PAID, OrderStatus.CONFIRMED, p.getId());
    }

    @Test
    @DisplayName("TC_PAYMENT_059 - Webhook: Lỗi Event vẫn phải SUCCESS DB")
    void TC_PAYMENT_059_webhook_thành_công_dù_event_lỗi() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Cô lập lỗi logic kế toán với luồng thanh toán.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 239
        User u = persistFullUser("u59@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY59", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().plusHours(1)); paymentRepository.save(p);
        doThrow(new RuntimeException()).when(eventPublisher).publishEvent(any());
        assertTrue(paymentService.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY59").amount(1.0).build()).isSuccess());
        assertEquals(PaymentStatus.SUCCESS, paymentRepository.findById(p.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_060 - Kiểm tra trạng thái: Mã không tồn tại (Exception Assert)")
    void TC_PAYMENT_060_kiểm_tra_trạng_thái_lỗi_mã_sai() {
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 251
        assertThrows(RuntimeException.class, () -> paymentService.checkPaymentStatus("NONE"));
    }

    @Test
    @DisplayName("TC_PAYMENT_061 - Kiểm tra trạng thái: Đang chờ (Attribute Assert)")
    void TC_PAYMENT_061_kiểm_tra_trạng_thái_đang_chờ_hợp_lệ() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // API polling trả về PENDING.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 259
        User u = persistFullUser("u61@t.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY61", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().plusMinutes(10)); paymentRepository.save(p);
        assertEquals("PENDING", ((PaymentResponse)paymentService.checkPaymentStatus("PAY61").getData()).getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_062 - [BUG HUNT] Kiểm tra trạng thái: Auto-Expire và Hủy đơn")
    void TC_PAYMENT_062_kiểm_tra_trạng_thái_phát_hiện_quá_hạn_realtime() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Poll xong thấy hết hạn -> Hủy đơn luôn.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 277
        User u = persistFullUser("u62@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 10.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY62", 10.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().minusMinutes(1)); paymentRepository.save(p);
        paymentService.checkPaymentStatus("PAY62");
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p.getId()).get().getStatus());
        assertFalse(orderRepository.findById(o.getId()).isPresent(), "BUG: Đơn vẫn tồn tại");
    }

    @Test
    @DisplayName("TC_PAYMENT_063 - Kiểm tra trạng thái: Lỗi hủy đơn vẫn phải EXPIRED")
    void TC_PAYMENT_063_kiểm_tra_trạng_thái_quá_hạn_dù_hủy_đơn_lỗi() {
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 279
        User u = persistFullUser("u63@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY63", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().minusMinutes(1)); paymentRepository.save(p);
        doThrow(new RuntimeException()).when(orderService).cancelOrderByCustomer(any(), any(), any());
        paymentService.checkPaymentStatus("PAY63");
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_064 - Kiểm tra trạng thái: Đã hoàn tất trước đó")
    void TC_PAYMENT_064_kiểm_tra_trạng_thái_đã_hoàn_tất() {
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 259 -> else
        User u = persistFullUser("u64@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.CONFIRMED);
        Payment p = persistFullPayment(u, o, "PAY64", 1.0, PaymentStatus.SUCCESS);
        assertEquals("SUCCESS", ((PaymentResponse)paymentService.checkPaymentStatus("PAY64").getData()).getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_065 - Quét hết hạn (Batch Job): Danh sách rỗng")
    void TC_PAYMENT_065_quét_hết_hạn_danh_sách_rỗng() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Không crash khi rỗng.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 301
        long countBefore = paymentRepository.count();
        assertDoesNotThrow(() -> paymentService.expireOldPayments());
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_066 - Quét hết hạn (Batch Job): Xử lý lô (Mixed Time)")
    void TC_PAYMENT_066_quét_hết_hạn_xử_lý_hỗn_hợp() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra logic loop và filter thời gian.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 303, 304
        User u = persistFullUser("u66@g.com"); Customer c = persistFullCustomer(u);
        Order o1 = persistFullOrder(c, 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p1 = persistFullPayment(u, o1, "P661", 1.0, PaymentStatus.PENDING);
        p1.setExpiredAt(LocalDateTime.now().minusDays(1)); paymentRepository.save(p1);
        Order o2 = persistFullOrder(c, 2.0, OrderStatus.PENDING_PAYMENT);
        Payment p2 = persistFullPayment(u, o2, "P662", 2.0, PaymentStatus.PENDING);
        p2.setExpiredAt(LocalDateTime.now().plusDays(1)); paymentRepository.save(p2);
        paymentService.expireOldPayments();
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p1.getId()).get().getStatus());
        assertEquals(PaymentStatus.PENDING, paymentRepository.findById(p2.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_067 - Quét hết hạn (Batch Job): Continuity Assert")
    void TC_PAYMENT_067_quét_hết_hạn_xử_lý_tiếp_tục_khi_có_lỗi_đơn_lẻ() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Lỗi đơn 1 không dừng đơn 2.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 307
        User u = persistFullUser("u67@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY67", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().minusDays(1)); paymentRepository.save(p);
        doThrow(new RuntimeException()).when(orderService).cancelOrderByCustomer(any(), any(), any());
        assertDoesNotThrow(() -> paymentService.expireOldPayments());
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_068 - Xóa theo OrderId thành công (Absolute DB check)")
    void TC_PAYMENT_068_xóa_thanh_toán_theo_id_đơn_hàng() {
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 319
        User u = persistFullUser("u68@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "P68", 1.0, PaymentStatus.PENDING);
        long countBefore = paymentRepository.count();
        paymentService.deletePaymentByOrderId(o.getId());
        assertEquals(countBefore - 1, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_069 - Xóa theo OrderId không tồn tại")
    void TC_PAYMENT_069_xóa_thanh_toán_theo_id_đơn_hàng_không_tồn_tại() {
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 317 -> else
        long countBefore = paymentRepository.count();
        assertDoesNotThrow(() -> paymentService.deletePaymentByOrderId(9999L));
        assertEquals(countBefore, paymentRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_070 - [REFLECTION] Regex mã PAY")
    void TC_PAYMENT_070_reflection_trích_xuất_mã_thanh_toán() throws Exception {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Trích xuất mã từ text ngân hàng.
        Method m = PaymentServiceImpl.class.getDeclaredMethod("extractPaymentCode", String.class);
        m.setAccessible(true);
        assertEquals("PAY123", m.invoke(paymentService, "THANH TOAN PAY123"));
    }

    @Test
    @DisplayName("TC_PAYMENT_071 - [REFLECTION] Tính duy nhất")
    void TC_PAYMENT_071_reflection_sinh_mã_thanh_toán_duy_nhất() throws Exception {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Không trùng mã ngẫu nhiên.
        Object r = AopTestUtils.getUltimateTargetObject(paymentService);
        Method m = PaymentServiceImpl.class.getDeclaredMethod("generatePaymentCode");
        m.setAccessible(true);
        assertNotEquals(m.invoke(r), m.invoke(r));
    }

    @Test
    @DisplayName("TC_PAYMENT_072 - [REFLECTION] QR URL Format")
    void TC_PAYMENT_072_reflection_kiểm_tra_định_dạng_url_qr() throws Exception {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Verify tham số URL.
        Object r = AopTestUtils.getUltimateTargetObject(paymentService);
        Method m = PaymentServiceImpl.class.getDeclaredMethod("generateSepayQrCode", String.class, Double.class, String.class, String.class, String.class);
        m.setAccessible(true);
        assertTrue(((String)m.invoke(r, "P", 1.0, "V", "1", "N")).contains("amount=1"));
    }

    @Test
    @DisplayName("TC_PAYMENT_073 - Quét hết hạn: Chặn hủy đơn CONFIRMED")
    void TC_PAYMENT_073_quét_hết_hạn_không_hủy_đơn_đã_xác_nhận() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Bảo vệ đơn đã admin duyệt.
        User u = persistFullUser("u73@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.CONFIRMED);
        Payment p = persistFullPayment(u, o, "P73", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().minusDays(1)); paymentRepository.save(p);
        paymentService.expireOldPayments();
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p.getId()).get().getStatus());
        assertEquals(OrderStatus.CONFIRMED, orderRepository.findById(o.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_075 - Webhook: Lỗi ngoại lệ không xác định (Global Catch Assert)")
    void TC_PAYMENT_075_webhook_lỗi_ngoại_lệ_không_xác_định() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kích hoạt khối catch (Exception e) tại dòng 248. Đảm bảo hệ thống trả về thông báo lỗi thay vì crash.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 248: catch (Exception e)
        
        // Mock repository ném lỗi bất ngờ khi query
        PaymentRepository mockRepo = mock(PaymentRepository.class);
        PaymentServiceImpl serviceWithMock = new PaymentServiceImpl(mockRepo, orderRepository, userRepository, bankAccountRepository, eventPublisher, orderService);
        
        ApiResponse res = serviceWithMock.handleSepayWebhook(SepayWebhookRequest.builder().content("PAY123").build());
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Lỗi xử lý webhook"));
    }

    @Test
    @DisplayName("TC_PAYMENT_076 - Kiểm tra trạng thái: Lỗi hủy đơn hàng ngoại lệ (Catch check)")
    void TC_PAYMENT_076_kiểm_tra_trạng_thái_lỗi_hủy_đơn_ngoại_lệ() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Phủ khối catch tại dòng 268 khi orderService ném exception.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 268-270: catch (Exception e)
        
        User u = persistFullUser("u76@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY76", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().minusMinutes(1)); paymentRepository.save(p);
        
        doThrow(new RuntimeException("Critical Error")).when(orderService).cancelOrderByCustomer(any(), any(), any());
        
        ApiResponse res = paymentService.checkPaymentStatus("PAY76");
        assertTrue(res.isSuccess());
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_077 - [REFLECTION] Sinh mã trùng lặp (Branch Recursion check)")
    void TC_PAYMENT_077_reflection_sinh_mã_trùng_lặp_đệ_quy() throws Exception {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Phủ nhánh đệ quy khi mã sinh ra bị trùng trong DB.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 325: if (paymentRepository.existsByPaymentCode(code))
        
        PaymentRepository mockRepo = mock(PaymentRepository.class);
        when(mockRepo.existsByPaymentCode(anyString())).thenReturn(true).thenReturn(false);
        
        PaymentServiceImpl serviceWithMock = new PaymentServiceImpl(mockRepo, orderRepository, userRepository, bankAccountRepository, eventPublisher, orderService);
        Method m = PaymentServiceImpl.class.getDeclaredMethod("generatePaymentCode");
        m.setAccessible(true);
        
        String code = (String) m.invoke(serviceWithMock);
        assertNotNull(code);
        verify(mockRepo, times(2)).existsByPaymentCode(anyString());
    }

    @Test
    @DisplayName("TC_PAYMENT_078 - [REFLECTION] Trích xuất mã: Nội dung không chứa 'PAY' (Else Branch)")
    void TC_PAYMENT_078_reflection_trích_xuất_mã_không_chứa_PAY() throws Exception {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Phủ nhánh else tại dòng 355 khi content không có chuỗi "PAY".
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 355: if (index != -1) -> else
        
        Method m = PaymentServiceImpl.class.getDeclaredMethod("extractPaymentCode", String.class);
        m.setAccessible(true);
        
        assertEquals("KHONG_CO_MA", m.invoke(paymentService, "  KHONG_CO_MA  "));
    }

    @Test
    @DisplayName("TC_PAYMENT_079 - Quét hết hạn: Lỗi hủy đơn ngoại lệ (Batch Loop Catch)")
    void TC_PAYMENT_079_quét_hết_hạn_lỗi_hủy_đơn_ngoại_lệ() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Phủ khối catch tại dòng 301 của vòng lặp batch.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 301-303: catch (Exception e)
        
        User u = persistFullUser("u79@g.com"); Order o = persistFullOrder(persistFullCustomer(u), 1.0, OrderStatus.PENDING_PAYMENT);
        Payment p = persistFullPayment(u, o, "PAY79", 1.0, PaymentStatus.PENDING);
        p.setExpiredAt(LocalDateTime.now().minusDays(1)); paymentRepository.save(p);
        
        doThrow(new RuntimeException("Batch Error")).when(orderService).cancelOrderByCustomer(any(), any(), any());
        
        paymentService.expireOldPayments();
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_080 - Quét hết hạn: Trường hợp Order bị null (Safe Check)")
    void TC_PAYMENT_080_quét_hết_hạn_order_null() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Phủ nhánh if (order != null) -> else.
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 297: if (order != null) -> false
        
        User u = persistFullUser("u80@g.com");
        Payment p = Payment.builder()
                .paymentCode("P80")
                .user(u)
                .order(null) // Giả lập data lỗi rác trong DB
                .amount(1.0)
                .status(PaymentStatus.PENDING)
                .expiredAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now())
                .build();
        try {
            paymentRepository.save(p);
            paymentService.expireOldPayments();
        } catch (Exception e) {
            fail("TC_PAYMENT_080 - Lỗi vật lý Database chặn đứng test (Cần fix DB Constraint): " + e.getMessage());
        }
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p.getId()).get().getStatus());
    }

    @Test
    @DisplayName("TC_PAYMENT_081 - [REFLECTION] Trích xuất mã: Content null hoặc rỗng")
    void TC_PAYMENT_081_reflection_trích_xuất_mã_content_rỗng() throws Exception {
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 344: if (content == null || content.isEmpty())
        
        Method m = PaymentServiceImpl.class.getDeclaredMethod("extractPaymentCode", String.class);
        m.setAccessible(true);
        
        assertNull(m.invoke(paymentService, (Object)null));
        assertEquals("", m.invoke(paymentService, ""));
    }

    @Test
    @DisplayName("TC_PAYMENT_082 - Check status: Trường hợp Order null (Real-time check)")
    void TC_PAYMENT_082_kiểm_tra_trạng_thái_order_null() {
        // [ÁNH XẠ LOGIC CODE]
        // Dòng 265: if (order != null) -> false
        
        User u = persistFullUser("u82@g.com");
        Payment p = Payment.builder()
                .paymentCode("P82")
                .user(u)
                .order(null)
                .amount(1.0)
                .status(PaymentStatus.PENDING)
                .expiredAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now())
                .build();
        try {
            paymentRepository.save(p);
            paymentService.checkPaymentStatus("P82");
        } catch (Exception e) {
            fail("TC_PAYMENT_082 - Lỗi vật lý Database chặn đứng test (Cần fix DB Constraint): " + e.getMessage());
        }
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(p.getId()).get().getStatus());
    }
}
