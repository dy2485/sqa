package com.doan.WEB_TMDT.module.payment;

/*
 * Run command:
 * export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms
 * export PATH="$JAVA_HOME/bin:$PATH"
 * 
 * 
 */

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.listener.OrderStatusChangedEvent;
import com.doan.WEB_TMDT.module.accounting.entity.SupplierPayable;
import com.doan.WEB_TMDT.module.accounting.entity.SupplierPayment;
import com.doan.WEB_TMDT.module.accounting.repository.FinancialTransactionRepository;
import com.doan.WEB_TMDT.module.accounting.repository.SupplierPayableRepository;
import com.doan.WEB_TMDT.module.accounting.repository.SupplierPaymentRepository;
import com.doan.WEB_TMDT.module.accounting.service.impl.SupplierPayableServiceImpl;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.entity.Role;
import com.doan.WEB_TMDT.module.auth.entity.Status;
import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import com.doan.WEB_TMDT.module.inventory.entity.PurchaseOrder;
import com.doan.WEB_TMDT.module.inventory.entity.Supplier;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import com.doan.WEB_TMDT.module.order.service.OrderService;
import com.doan.WEB_TMDT.module.payment.controller.PaymentController;
import com.doan.WEB_TMDT.module.payment.dto.CreatePaymentRequest;
import com.doan.WEB_TMDT.module.payment.dto.PaymentResponse;
import com.doan.WEB_TMDT.module.payment.dto.SepayWebhookRequest;
import com.doan.WEB_TMDT.module.payment.entity.BankAccount;
import com.doan.WEB_TMDT.module.payment.entity.Payment;
import com.doan.WEB_TMDT.module.payment.entity.PaymentMethod;
import com.doan.WEB_TMDT.module.payment.repository.BankAccountRepository;
import com.doan.WEB_TMDT.module.payment.repository.PaymentRepository;
import com.doan.WEB_TMDT.module.payment.service.PaymentService;
import com.doan.WEB_TMDT.module.payment.service.impl.BankAccountServiceImpl;
import com.doan.WEB_TMDT.module.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentModuleTest {

    private static final List<String> CASE_IDS = List.of(
            "PAY-001", "PAY-002", "PAY-003", "PAY-004", "PAY-005", "PAY-006", "PAY-007", "PAY-008", "PAY-009", "PAY-010",
            "PAY-011", "PAY-012", "PAY-013", "PAY-014", "PAY-015", "PAY-016", "PAY-017", "PAY-018", "PAY-019", "PAY-020",
            "PAY-021", "PAY-022"
    );

    @RegisterExtension
    static final TestCaseStatusLogger testCaseStatusLogger = new TestCaseStatusLogger(CASE_IDS);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentServiceImpl paymentServiceImpl;

    @Mock
    private PaymentService paymentService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PaymentController paymentController;

    @InjectMocks
    private BankAccountServiceImpl bankAccountServiceImpl;

    @Mock
    private SupplierPayableRepository payableRepository;

    @Mock
    private SupplierPaymentRepository supplierPaymentRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @InjectMocks
    private SupplierPayableServiceImpl payableService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentServiceImpl, "sepayBankCode", "VCB");
        ReflectionTestUtils.setField(paymentServiceImpl, "sepayAccountNumber", "1234567890");
        ReflectionTestUtils.setField(paymentServiceImpl, "sepayAccountName", "CONG TY TEST");
        ReflectionTestUtils.setField(paymentServiceImpl, "amountMultiplier", 1.0);

        TestingAuthenticationToken auth = new TestingAuthenticationToken("tester@example.com", "password", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @org.junit.jupiter.api.AfterEach
    void clearAuthenticationContext() {
        SecurityContextHolder.clearContext();
    }

    @org.junit.jupiter.api.Order(1)
    @Test
    @DisplayName("PAY-001: Tạo payment thành công khi có account mặc định")
    void createPaymentSuccessWithDefaultBankAccount() {
        stubSaveForPaymentFlow();
        CreatePaymentRequest request = CreatePaymentRequest.builder().orderId(11L).amount(120000.0).build();
        Order order = buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT);

        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buildUser(10L, "customer@test.com")));
        when(paymentRepository.findByOrderId(11L)).thenReturn(Optional.empty());
        when(paymentRepository.existsByPaymentCode(anyString())).thenReturn(false);
        when(bankAccountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(buildBankAccount(1L, true, true)));

        ApiResponse response = paymentServiceImpl.createPayment(request, 10L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isInstanceOf(PaymentResponse.class);
    }

    @org.junit.jupiter.api.Order(2)
    @Test
    @DisplayName("PAY-002: Tạo payment khi order không tồn tại")
    void createPaymentFailsWhenOrderNotFound() {
        CreatePaymentRequest request = CreatePaymentRequest.builder().orderId(99L).amount(100000.0).build();
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentServiceImpl.createPayment(request, 10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng");
    }

    @org.junit.jupiter.api.Order(3)
    @Test
    @DisplayName("PAY-003: Tạo payment khi user không tồn tại")
    void createPaymentFailsWhenUserNotFound() {
        CreatePaymentRequest request = CreatePaymentRequest.builder().orderId(11L).amount(120000.0).build();
        when(orderRepository.findById(11L)).thenReturn(Optional.of(buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT)));
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentServiceImpl.createPayment(request, 10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không tìm thấy người dùng");
    }

    @org.junit.jupiter.api.Order(4)
    @Test
    @DisplayName("PAY-004: Thanh toán đơn không thuộc sở hữu")
    void createPaymentFailsWhenOrderNotOwnedByUser() {
        CreatePaymentRequest request = CreatePaymentRequest.builder().orderId(11L).amount(120000.0).build();
        when(orderRepository.findById(11L)).thenReturn(Optional.of(buildOrder(11L, 999L, 120000.0, OrderStatus.PENDING_PAYMENT)));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buildUser(10L, "customer@test.com")));

        ApiResponse response = paymentServiceImpl.createPayment(request, 10L);

        assertThat(response.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(5)
    @Test
    @DisplayName("PAY-005: Không tạo payment trùng cho cùng đơn")
    void createPaymentFailsWhenPaymentAlreadyExists() {
        CreatePaymentRequest request = CreatePaymentRequest.builder().orderId(11L).amount(120000.0).build();
        Order order = buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT);

        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buildUser(10L, "customer@test.com")));
        when(paymentRepository.findByOrderId(11L)).thenReturn(Optional.of(
                buildPayment("PAY-OLD", order, 120000.0, com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.PENDING)
        ));

        ApiResponse response = paymentServiceImpl.createPayment(request, 10L);

        assertThat(response.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(6)
    @Test
    @DisplayName("PAY-006: Từ chối amount không khớp total")
    void createPaymentFailsWhenAmountMismatch() {
        CreatePaymentRequest request = CreatePaymentRequest.builder().orderId(11L).amount(100000.0).build();
        when(orderRepository.findById(11L)).thenReturn(Optional.of(buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT)));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buildUser(10L, "customer@test.com")));
        when(paymentRepository.findByOrderId(11L)).thenReturn(Optional.empty());

        ApiResponse response = paymentServiceImpl.createPayment(request, 10L);

        assertThat(response.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(7)
    @Test
    @DisplayName("PAY-007: Công nợ thanh toán số tiền 0")
    void payablePaymentWithZeroAmountShouldBeRejected() {
        com.doan.WEB_TMDT.module.accounting.dto.CreatePaymentRequest request = buildAccountingPaymentRequest(1L, BigDecimal.ZERO);
        SupplierPayable payable = buildSupplierPayable(BigDecimal.valueOf(100), BigDecimal.ZERO);

        when(payableRepository.findById(1L)).thenReturn(Optional.of(payable));

        ApiResponse response = payableService.makePayment(request);

        assertThat(response.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(8)
    @Test
    @DisplayName("PAY-008: Công nợ thanh toán số tiền âm")
    void payablePaymentWithNegativeAmountShouldBeRejected() {
        com.doan.WEB_TMDT.module.accounting.dto.CreatePaymentRequest request = buildAccountingPaymentRequest(1L, BigDecimal.valueOf(-10));
        SupplierPayable payable = buildSupplierPayable(BigDecimal.valueOf(100), BigDecimal.ZERO);

        when(payableRepository.findById(1L)).thenReturn(Optional.of(payable));

        ApiResponse response = payableService.makePayment(request);

        assertThat(response.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(9)
    @Test
    @DisplayName("PAY-009: Tạo payment khi thiếu default account")
    void createPaymentUsesFallbackWhenNoDefaultBankAccount() {
        stubSaveForPaymentFlow();
        CreatePaymentRequest request = CreatePaymentRequest.builder().orderId(11L).amount(120000.0).build();

        when(orderRepository.findById(11L)).thenReturn(Optional.of(buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT)));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buildUser(10L, "customer@test.com")));
        when(paymentRepository.findByOrderId(11L)).thenReturn(Optional.empty());
        when(paymentRepository.existsByPaymentCode(anyString())).thenReturn(false);
        when(bankAccountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        ApiResponse response = paymentServiceImpl.createPayment(request, 10L);

        assertThat(response.isSuccess()).isTrue();
    }

    @org.junit.jupiter.api.Order(10)
    @Test
    @DisplayName("PAY-010: Chính sách bắt buộc default account")
    void createPaymentMustRequireDefaultAccount() {
        stubSaveForPaymentFlow();
        CreatePaymentRequest request = CreatePaymentRequest.builder().orderId(11L).amount(120000.0).build();

        when(orderRepository.findById(11L)).thenReturn(Optional.of(buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT)));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buildUser(10L, "customer@test.com")));
        when(paymentRepository.findByOrderId(11L)).thenReturn(Optional.empty());
        when(paymentRepository.existsByPaymentCode(anyString())).thenReturn(false);
        when(bankAccountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        ApiResponse response = paymentServiceImpl.createPayment(request, 10L);

        assertThat(response.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(11)
    @Test
    @DisplayName("PAY-011: Tra cứu payment theo mã hợp lệ")
    void getPaymentByCodeSuccess() {
        when(paymentRepository.findByPaymentCode("PAY202604180001")).thenReturn(Optional.of(
                buildPayment("PAY202604180001", buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT), 120000.0,
                        com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.PENDING)
        ));

        ApiResponse response = paymentServiceImpl.getPaymentByCode("PAY202604180001");

        assertThat(response.isSuccess()).isTrue();
    }

    @org.junit.jupiter.api.Order(12)
    @Test
    @DisplayName("PAY-012: Tra cứu payment theo mã không tồn tại")
    void getPaymentByCodeFailsWhenNotFound() {
        when(paymentRepository.findByPaymentCode("PAY-NOT-FOUND")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentServiceImpl.getPaymentByCode("PAY-NOT-FOUND"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không tìm thấy thanh toán");
    }

    @org.junit.jupiter.api.Order(13)
    @Test
    @DisplayName("PAY-013: Webhook không chứa mã thanh toán")
    void webhookRejectsContentWithoutPaymentCode() {
        ApiResponse response = paymentServiceImpl.handleSepayWebhook(SepayWebhookRequest.builder()
                .content("CHUYEN KHOAN TEST")
                .amount(120000.0)
                .build());

        assertThat(response.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(14)
    @Test
    @DisplayName("PAY-014: Webhook có mã nhưng không có payment")
    void webhookFailsWhenPaymentNotFound() {
        when(paymentRepository.findByPaymentCode("PAY202604180111")).thenReturn(Optional.empty());

        ApiResponse response = paymentServiceImpl.handleSepayWebhook(buildWebhook("PAY202604180111", 120000.0));

        assertThat(response.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(15)
    @Test
    @DisplayName("PAY-015: Webhook lặp cho payment đã xử lý")
    void webhookIsIdempotentForAlreadySuccessPayment() {
        Order order = buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT);
        when(paymentRepository.findByPaymentCode("PAY202604180222")).thenReturn(Optional.of(
                buildPayment("PAY202604180222", order, 120000.0, com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.SUCCESS)
        ));
        when(bankAccountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        ApiResponse response = paymentServiceImpl.handleSepayWebhook(buildWebhook("PAY202604180222", 120000.0));

        assertThat(response.isSuccess()).isTrue();
    }

    @org.junit.jupiter.api.Order(16)
    @Test
    @DisplayName("PAY-016: Webhook sai lệch amount")
    void webhookRejectsAmountMismatch() {
        Order order = buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT);
        when(paymentRepository.findByPaymentCode("PAY202604180333")).thenReturn(Optional.of(
                buildPayment("PAY202604180333", order, 120000.0, com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.PENDING)
        ));
        when(bankAccountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        ApiResponse response = paymentServiceImpl.handleSepayWebhook(buildWebhook("PAY202604180333", 100000.0));

        assertThat(response.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(17)
    @Test
    @DisplayName("PAY-017: Chuẩn hóa xử lý ngoại lệ sai lệch amount")
    void webhookAmountMismatchShouldThrowBusinessException() {
        Order order = buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT);
        when(paymentRepository.findByPaymentCode("PAY202604180333")).thenReturn(Optional.of(
                buildPayment("PAY202604180333", order, 120000.0, com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.PENDING)
        ));
        when(bankAccountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentServiceImpl.handleSepayWebhook(buildWebhook("PAY202604180333", 100000.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Order(18)
    @Test
    @DisplayName("PAY-018: Webhook đến sau thời gian hết hạn")
    void webhookRejectsExpiredPayment() {
        Order order = buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT);
        Payment payment = buildPayment("PAY202604180444", order, 120000.0,
                com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.PENDING);
        payment.setExpiredAt(LocalDateTime.now().minusMinutes(1));

        when(paymentRepository.findByPaymentCode("PAY202604180444")).thenReturn(Optional.of(payment));
        when(bankAccountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        ApiResponse response = paymentServiceImpl.handleSepayWebhook(buildWebhook("PAY202604180444", 120000.0));

        assertThat(response.isSuccess()).isFalse();
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getStatus())
                .isEqualTo(com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.EXPIRED);
    }

    @org.junit.jupiter.api.Order(19)
    @Test
    @DisplayName("PAY-019: Kiểm tra trạng thái realtime với payment quá hạn")
    void checkPaymentStatusExpiresPendingAndCancelsOrder() {
        Payment payment = buildPayment("PAY202604180666", buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT),
                120000.0, com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.PENDING);
        payment.setExpiredAt(LocalDateTime.now().minusMinutes(2));

        when(paymentRepository.findByPaymentCode("PAY202604180666")).thenReturn(Optional.of(payment));
        when(orderService.cancelOrderByCustomer(eq(11L), eq(77L), anyString())).thenReturn(ApiResponse.success("Đã hủy đơn"));

        ApiResponse response = paymentServiceImpl.checkPaymentStatus("PAY202604180666");

        assertThat(response.isSuccess()).isTrue();
        verify(orderService).cancelOrderByCustomer(eq(11L), eq(77L), anyString());
    }

    @org.junit.jupiter.api.Order(20)
    @Test
    @DisplayName("PAY-020: Job xử lý payment quá hạn")
    void expireOldPaymentsUpdatesStatusAndCancelsOrders() {
        Payment payment1 = buildPayment("PAY202604180777", buildOrder(11L, 10L, 120000.0, OrderStatus.PENDING_PAYMENT),
                120000.0, com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.PENDING);
        Payment payment2 = buildPayment("PAY202604180778", buildOrder(12L, 12L, 220000.0, OrderStatus.CONFIRMED),
                220000.0, com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.PENDING);

        when(paymentRepository.findByStatusAndExpiredAtBefore(eq(com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(payment1, payment2));

        paymentServiceImpl.expireOldPayments();

        assertThat(payment1.getStatus()).isEqualTo(com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.EXPIRED);
        assertThat(payment2.getStatus()).isEqualTo(com.doan.WEB_TMDT.module.payment.entity.PaymentStatus.EXPIRED);
    }

    @org.junit.jupiter.api.Order(21)
    @Test
    @DisplayName("PAY-021: API webhook nhận body rỗng")
    void controllerShouldRejectEmptyWebhookBody() {
        SepayWebhookRequest request = new SepayWebhookRequest();
        when(paymentService.handleSepayWebhook(request)).thenReturn(ApiResponse.success("webhook"));

        ApiResponse actual = paymentController.handleSepayWebhook(request);

        assertThat(actual.isSuccess()).isFalse();
    }

    @org.junit.jupiter.api.Order(22)
    @Test
    @DisplayName("PAY-022: Không cho tắt tài khoản mặc định")
    void defaultBankAccountMustNotBeDeactivated() {
        BankAccount target = buildBankAccount(2L, true, true);
        when(bankAccountRepository.findById(2L)).thenReturn(Optional.of(target));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse response = bankAccountServiceImpl.toggleActive(2L);

        assertThat(response.isSuccess()).isFalse();
    }

    private void stubSaveForPaymentFlow() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(999L);
            }
            if (payment.getCreatedAt() == null) {
                payment.setCreatedAt(LocalDateTime.now());
            }
            if (payment.getExpiredAt() == null) {
                payment.setExpiredAt(LocalDateTime.now().plusMinutes(1));
            }
            return payment;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private User buildUser(Long userId, String email) {
        return User.builder()
                .id(userId)
                .email(email)
                .password("secret")
                .role(Role.CUSTOMER)
                .status(Status.ACTIVE)
                .build();
    }

    private Order buildOrder(Long orderId, Long ownerUserId, Double total, OrderStatus status) {
        User ownerUser = buildUser(ownerUserId, "owner" + ownerUserId + "@mail.com");
        Customer customer = Customer.builder()
                .id(77L)
                .fullName("Test Customer")
                .phone("0900000000")
                .user(ownerUser)
                .build();

        return Order.builder()
                .id(orderId)
                .orderCode("ORD-" + orderId)
                .customer(customer)
                .shippingAddress("HCM")
                .subtotal(total)
                .shippingFee(0.0)
                .discount(0.0)
                .total(total)
                .paymentStatus(com.doan.WEB_TMDT.module.order.entity.PaymentStatus.UNPAID)
                .status(status)
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Payment buildPayment(String paymentCode, Order order, Double amount,
                                 com.doan.WEB_TMDT.module.payment.entity.PaymentStatus status) {
        return Payment.builder()
                .id(1000L)
                .paymentCode(paymentCode)
                .order(order)
                .user(order.getCustomer().getUser())
                .amount(amount)
                .method(PaymentMethod.SEPAY)
                .status(status)
                .sepayBankCode("MBBank")
                .sepayAccountNumber("3333315012003")
                .sepayAccountName("CONG TY A")
                .sepayContent(paymentCode)
                .sepayQrCode("https://img.vietqr.io/image/MBBank-3333315012003-qr_only.jpg")
                .createdAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    private BankAccount buildBankAccount(Long id, boolean isDefault, boolean isActive) {
        return BankAccount.builder()
                .id(id)
                .bankCode("MBBank")
                .bankName("MB Bank")
                .accountNumber("3333315012003")
                .accountName("CONG TY A")
                .sepayApiToken("token-test")
                .isDefault(isDefault)
                .isActive(isActive)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private SepayWebhookRequest buildWebhook(String content, Double amount) {
        return SepayWebhookRequest.builder()
                .transactionId("TXN-001")
                .bankCode("MBBank")
                .accountNumber("3333315012003")
                .amount(amount)
                .content(content)
                .status("SUCCESS")
                .signature("demo-sign")
                .build();
    }

    private com.doan.WEB_TMDT.module.accounting.dto.CreatePaymentRequest buildAccountingPaymentRequest(Long payableId, BigDecimal amount) {
        com.doan.WEB_TMDT.module.accounting.dto.CreatePaymentRequest request =
                new com.doan.WEB_TMDT.module.accounting.dto.CreatePaymentRequest();
        request.setPayableId(payableId);
        request.setAmount(amount);
        request.setPaymentDate(LocalDate.now());
        request.setPaymentMethod(com.doan.WEB_TMDT.module.accounting.entity.PaymentMethod.CASH);
        request.setReferenceNumber("REF-" + payableId);
        request.setNote("Test payment");
        return request;
    }

    private SupplierPayable buildSupplierPayable(BigDecimal total, BigDecimal paidAmount) {
        Supplier supplier = Supplier.builder()
                .id(100L)
                .name("Supplier Test")
                .paymentTermDays(30)
                .taxCode("TAX-123")
                .build();

        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .id(1L)
                .poCode("PO-001")
                .supplier(supplier)
                .receivedDate(LocalDateTime.now())
                .status(null)
                .items(new ArrayList<>())
                .build();

        BigDecimal remaining = total.subtract(paidAmount);
        SupplierPayable payable = SupplierPayable.builder()
                .id(1L)
                .payableCode("AP-20260417-0001")
                .supplier(supplier)
                .purchaseOrder(purchaseOrder)
                .totalAmount(total)
                .paidAmount(paidAmount)
                .remainingAmount(remaining)
                .status(null)
                .invoiceDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .paymentTermDays(30)
                .createdAt(LocalDateTime.now())
                .build();
        payable.updateStatus();
        return payable;
    }

    private static class TestCaseStatusLogger implements TestWatcher, AfterAllCallback {
        private final List<String> orderedIds;
        private final Map<String, String> results = new LinkedHashMap<>();

        private TestCaseStatusLogger(List<String> orderedIds) {
            this.orderedIds = orderedIds;
            for (String id : orderedIds) {
                results.put(id, "NOT_RUN");
            }
        }

        private String extractTestId(ExtensionContext context) {
            String displayName = context.getDisplayName();
            int separatorIndex = displayName.indexOf(":");
            return separatorIndex > 0 ? displayName.substring(0, separatorIndex).trim() : displayName.trim();
        }

        @Override
        public void testSuccessful(ExtensionContext context) {
            results.put(extractTestId(context), "PASS");
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            results.put(extractTestId(context), "FAIL");
        }

        @Override
        public void afterAll(ExtensionContext context) {
            System.out.println("=== PAYMENT TEST RESULTS ===");
            for (String id : orderedIds) {
                System.out.println(id + " : " + results.get(id));
            }
        }
    }
}
