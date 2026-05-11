package com.doan.WEB_TMDT.module.payment.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.payment.dto.BankAccountRequest;
import com.doan.WEB_TMDT.module.payment.dto.BankAccountResponse;
import com.doan.WEB_TMDT.module.payment.entity.BankAccount;
import com.doan.WEB_TMDT.module.payment.repository.BankAccountRepository;
import com.doan.WEB_TMDT.module.payment.service.impl.BankAccountServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BankAccountServiceImplTest - Kiểm thử toàn diện dịch vụ Tài khoản ngân hàng
 * PHẦN 1: TC_PAYMENT_001 -> TC_PAYMENT_010
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("BANK ACCOUNT SERVICE TEST")
@ExtendWith(BankAccountServiceImplTest.ResultReporter.class)
class BankAccountServiceImplTest {

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
                    // Cắt lấy 3 số sau "TC_PAYMENT_" (vị trí 11 đến 14)
                    int id = Integer.parseInt(displayName.substring(11, 14));
                    testResults.put(id, status);
                } catch (Exception ignored) {}
            }
        }
    }

    @AfterAll
    static void printSummary() {
        System.out.println("\n========================================================================");
        System.out.println("TEST SUMMARY REPORT: BANK ACCOUNT MODULE (TC_001 - TC_031)");
        System.out.println("========================================================================");
        for (int i = 1; i <= 31; i++) {
            String status = testResults.getOrDefault(i, "NA");
            System.out.printf("TC_PAYMENT_%03d : %s\n", i, status);
        }
        System.out.println("========================================================================\n");
    }

    @Autowired
    private BankAccountServiceImpl bankAccountService;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @BeforeEach
    void setup() {
        bankAccountRepository.deleteAll();
    }

    // Helper: Create raw entity
    private BankAccount createRaw(String code, String num, String name, boolean active, boolean isDefault) {
        return bankAccountRepository.save(BankAccount.builder()
                .bankCode(code)
                .bankName("Bank " + code)
                .accountNumber(num)
                .accountName(name)
                .description("Desc " + code)
                .sepayApiToken("token_" + code)
                .sepayMerchantId("merchant_" + code)
                .isActive(active)
                .isDefault(isDefault)
                .build());
    }

    // Assert ALL fields between entity and response
    private void assertResponseMatchesEntity(BankAccount entity, BankAccountResponse response) {
        assertEquals(entity.getId(), response.getId());
        assertEquals(entity.getBankCode(), response.getBankCode());
        assertEquals(entity.getBankName(), response.getBankName());
        assertEquals(entity.getAccountNumber(), response.getAccountNumber());
        assertEquals(entity.getAccountName(), response.getAccountName());
        assertEquals(entity.getDescription(), response.getDescription());
        assertEquals(entity.getSepayApiToken(), response.getSepayApiToken());
        assertEquals(entity.getSepayMerchantId(), response.getSepayMerchantId());
        assertEquals(entity.getIsActive(), response.getIsActive());
        assertEquals(entity.getIsDefault(), response.getIsDefault());
    }

    // =========================================================================================
    // I. getAll()
    // =========================================================================================

    @Test
    @DisplayName("TC_PAYMENT_001 - Lấy danh sách tài khoản, kiểm tra thứ tự (Mặc định trước, sau đó là ngày tạo)")
    void TC_PAYMENT_001_lấy_tất_cả_tài_khoản_kiểm_tra_thứ_tự() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra việc lấy danh sách tất cả tài khoản ngân hàng.
        // Dữ liệu phải được sắp xếp theo: isDefault DESC, sau đó là createdAt DESC.
        // Mỗi phần tử trong danh sách phải đầy đủ 10 thuộc tính.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 26: bankAccountRepository.findAllByOrderByIsDefaultDescCreatedAtDesc()

        BankAccount acc1 = createRaw("VCB", "111", "ACC 1", true, false);
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        BankAccount acc2 = createRaw("ACB", "222", "ACC 2", true, true);
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        BankAccount acc3 = createRaw("MB", "333", "ACC 3", true, false);

        ApiResponse response = bankAccountService.getAll();
        List<BankAccountResponse> list = (List<BankAccountResponse>) response.getData();
        
        assertEquals(3, list.size());
        assertEquals(acc2.getId(), list.get(0).getId());
        assertEquals(acc3.getId(), list.get(1).getId());
        assertEquals(acc1.getId(), list.get(2).getId());

        assertResponseMatchesEntity(acc2, list.get(0));
        assertResponseMatchesEntity(acc3, list.get(1));
        assertResponseMatchesEntity(acc1, list.get(2));
    }

    // =========================================================================================
    // II. getById(Long id)
    // =========================================================================================

    @Test
    @DisplayName("TC_PAYMENT_002 - Lấy chi tiết tài khoản theo ID thành công")
    void TC_PAYMENT_002_lấy_chi_tiết_id_thành_công() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Truy xuất thông tin chi tiết của một tài khoản ngân hàng cụ thể bằng ID.
        // Đảm bảo dữ liệu trả về khớp hoàn toàn với dữ liệu trong DB (10 fields).
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 35: bankAccountRepository.findById(id)

        BankAccount acc = createRaw("VCB", "123", "NGUYEN VAN A", true, false);
        long countBefore = bankAccountRepository.count();
        ApiResponse response = bankAccountService.getById(acc.getId());
        
        assertTrue(response.isSuccess());
        assertEquals(countBefore, bankAccountRepository.count());
        assertResponseMatchesEntity(acc, (BankAccountResponse) response.getData());
    }

    @Test
    @DisplayName("TC_PAYMENT_003 - Lấy chi tiết thất bại khi ID không tồn tại trong hệ thống")
    void TC_PAYMENT_003_lấy_chi_tiết_id_không_tồn_tại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra xử lý lỗi khi người dùng yêu cầu ID không có trong hệ thống.
        // Hệ thống phải ném ra RuntimeException và không làm thay đổi dữ liệu DB.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 36: .orElseThrow(...)

        long countBefore = bankAccountRepository.count();
        assertThrows(RuntimeException.class, () -> bankAccountService.getById(9999L));
        assertEquals(countBefore, bankAccountRepository.count());
    }

    // =========================================================================================
    // III. getDefault()
    // =========================================================================================

    @Test
    @DisplayName("TC_PAYMENT_004 - Lấy tài khoản mặc định thành công")
    void TC_PAYMENT_004_lấy_tài_khoản_mặc_định_thành_công() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Truy xuất tài khoản được đánh dấu là mặc định (isDefault = true).
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 41: bankAccountRepository.findByIsDefaultTrue()

        BankAccount acc = createRaw("VCB", "123", "DEFAULT ACC", true, true);
        ApiResponse response = bankAccountService.getDefault();
        assertTrue(response.isSuccess());
        assertResponseMatchesEntity(acc, (BankAccountResponse) response.getData());
    }

    @Test
    @DisplayName("TC_PAYMENT_005 - Lấy tài khoản mặc định thất bại khi chưa có cái nào")
    void TC_PAYMENT_005_lấy_tài_khoản_mặc_định_khi_chưa_có() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra trường hợp hệ thống chưa cấu hình tài khoản mặc định nào.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 43: if (account == null)

        ApiResponse response = bankAccountService.getDefault();
        assertFalse(response.isSuccess());
        assertEquals("Chưa có tài khoản mặc định", response.getMessage());
    }

    // =========================================================================================
    // IV. create(BankAccountRequest request)
    // =========================================================================================

    @Test
    @DisplayName("TC_PAYMENT_006 - Tạo mới tài khoản thường thành công")
    void TC_PAYMENT_006_tạo_mới_tài_khoản_thường_thành_công() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Tạo một tài khoản ngân hàng mới không phải là mặc định.
        // Kiểm tra tất cả các thuộc tính được lưu đúng vào DB và record count tăng 1.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 73: bankAccountRepository.save(account)

        long countBefore = bankAccountRepository.count();
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("VCB").bankName("Vietcombank").accountNumber("123").accountName("ACC TEST")
                .description("D").sepayApiToken("T").sepayMerchantId("M").isActive(true).isDefault(false)
                .build();
        
        ApiResponse response = bankAccountService.create(req);
        assertTrue(response.isSuccess());
        assertEquals(countBefore + 1, bankAccountRepository.count());
        
        BankAccount dbAcc = bankAccountRepository.findById(((BankAccountResponse)response.getData()).getId()).get();
        assertEquals(req.getBankCode(), dbAcc.getBankCode());
        assertEquals(req.getAccountNumber(), dbAcc.getAccountNumber());
        assertTrue(dbAcc.getIsActive());
        assertFalse(dbAcc.getIsDefault());
    }

    @Test
    @DisplayName("TC_PAYMENT_007 - Tạo mới với giá trị flags là null (Mặc định isActive=true, isDefault=false)")
    void TC_PAYMENT_007_tạo_mới_với_flags_null() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra xử lý null cho các trường logic để đảm bảo không lỗi và sử dụng giá trị mặc định của hệ thống.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 60-61: isActive != null ? ... : true

        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("A").bankName("B").accountNumber("1").accountName("N")
                .isActive(null).isDefault(null).build();
        
        ApiResponse response = bankAccountService.create(req);
        BankAccountResponse data = (BankAccountResponse) response.getData();
        assertTrue(data.getIsActive());
        assertFalse(data.getIsDefault());
    }

    @Test
    @DisplayName("TC_PAYMENT_008 - Tạo tài khoản mặc định mới và tự động gỡ bỏ mặc định của tài khoản cũ")
    void TC_PAYMENT_008_tạo_mới_mặc_định_gỡ_bỏ_cũ() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Đảm bảo tính duy nhất của tài khoản mặc định. Khi tạo mới 1 cái default, cái cũ phải bị gỡ bỏ.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 66: bankAccountRepository.findByIsDefaultTrue().ifPresent(...)

        BankAccount old = createRaw("OLD", "000", "OLD ACC", true, true);
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("NEW").bankName("NEW").accountNumber("111").accountName("NEW ACC")
                .isDefault(true).build();
        
        bankAccountService.create(req);
        assertFalse(bankAccountRepository.findById(old.getId()).get().getIsDefault());
    }

    @Test
    @DisplayName("TC_PAYMENT_009 - Tạo tài khoản mặc định khi chưa có bất kỳ tài khoản nào trước đó")
    void TC_PAYMENT_009_tạo_mặc_định_đầu_tiên() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Đảm bảo hệ thống hoạt động đúng khi DB trống và tài khoản đầu tiên được set làm mặc định.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 65: if (account.getIsDefault())

        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("F").bankName("F").accountNumber("1").accountName("F")
                .isDefault(true).build();
        ApiResponse response = bankAccountService.create(req);
        assertTrue(((BankAccountResponse)response.getData()).getIsDefault());
    }

    @Test
    @DisplayName("TC_PAYMENT_010 - Cập nhật thông tin cơ bản của tài khoản ngân hàng")
    void TC_PAYMENT_010_cập_nhật_thông_tin_cơ_bản() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Thay đổi các thông tin định danh của tài khoản. Kiểm tra DB lưu đúng giá trị mới và record count không đổi.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 88-94: account.set...

        BankAccount acc = createRaw("VCB", "123", "OLD NAME", true, false);
        long countBefore = bankAccountRepository.count();
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("ACB").bankName("ACB BANK").accountNumber("456").accountName("NEW NAME")
                .description("ND").sepayApiToken("NT").sepayMerchantId("NM")
                .isActive(true).isDefault(false).build();
        
        bankAccountService.update(acc.getId(), req);
        assertEquals(countBefore, bankAccountRepository.count());
        
        BankAccount dbAcc = bankAccountRepository.findById(acc.getId()).get();
        assertEquals("ACB", dbAcc.getBankCode());
        assertEquals("NEW NAME", dbAcc.getAccountName());
    }

    @Test
    @DisplayName("TC_PAYMENT_011 - Cập nhật trạng thái isActive từ true sang false")
    void TC_PAYMENT_011_cập_nhật_active_sang_false() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra khả năng vô hiệu hóa tài khoản qua lệnh update.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 97: account.setIsActive(request.getIsActive())

        BankAccount acc = createRaw("A", "1", "N", true, false);
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("A").bankName("B").accountNumber("1").accountName("N")
                .isActive(false).build();
        
        bankAccountService.update(acc.getId(), req);
        assertFalse(bankAccountRepository.findById(acc.getId()).get().getIsActive());
    }

    @Test
    @DisplayName("TC_PAYMENT_012 - Cập nhật tài khoản khác thành mặc định, kiểm tra gỡ bỏ mặc định cũ")
    void TC_PAYMENT_012_cập_nhật_mặc_định_gỡ_bỏ_cũ() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Chuyển quyền mặc định từ tài khoản này sang tài khoản khác qua lệnh update.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 102: bankAccountRepository.findByIsDefaultTrue().ifPresent(...)

        BankAccount old = createRaw("OLD", "0", "O", true, true);
        BankAccount target = createRaw("TARGET", "1", "T", true, false);
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("T").bankName("T").accountNumber("1").accountName("T")
                .isDefault(true).build();
        
        bankAccountService.update(target.getId(), req);
        assertFalse(bankAccountRepository.findById(old.getId()).get().getIsDefault());
        assertTrue(bankAccountRepository.findById(target.getId()).get().getIsDefault());
    }

    @Test
    @DisplayName("TC_PAYMENT_013 - Cập nhật chính tài khoản đang mặc định vẫn giữ nguyên mặc định")
    void TC_PAYMENT_013_cập_nhật_đang_mặc_định() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Khi update một tài khoản đang là default và request vẫn gửi isDefault=true, hệ thống phải xử lý ổn định.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 104: if (!existing.getId().equals(id))

        BankAccount acc = createRaw("A", "1", "N", true, true);
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("A").bankName("B").accountNumber("1").accountName("N")
                .isDefault(true).build();
        
        bankAccountService.update(acc.getId(), req);
        assertTrue(bankAccountRepository.findById(acc.getId()).get().getIsDefault());
    }

    @Test
    @DisplayName("TC_PAYMENT_014 - Cập nhật thất bại khi ID tài khoản không tồn tại")
    void TC_PAYMENT_014_cập_nhật_id_không_tồn_tại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Kiểm tra xử lý lỗi khi update ID không tồn tại. Dữ liệu DB phải được bảo toàn.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 86: .orElseThrow(...)

        BankAccountRequest req = BankAccountRequest.builder().bankCode("A").build();
        long countBefore = bankAccountRepository.count();
        assertThrows(RuntimeException.class, () -> bankAccountService.update(9999L, req));
        assertEquals(countBefore, bankAccountRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_015 - Xóa tài khoản ngân hàng thành công")
    void TC_PAYMENT_015_xóa_thành_công() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Xóa một tài khoản thường. Kiểm tra record count giảm 1 và không thể find lại được.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 124: bankAccountRepository.delete(account)

        BankAccount acc = createRaw("DEL", "1", "D", true, false);
        long countBefore = bankAccountRepository.count();
        
        ApiResponse response = bankAccountService.delete(acc.getId());
        assertTrue(response.isSuccess());
        assertEquals(countBefore - 1, bankAccountRepository.count());
        assertFalse(bankAccountRepository.findById(acc.getId()).isPresent());
    }

    @Test
    @DisplayName("TC_PAYMENT_016 - Ngăn chặn xóa tài khoản đang là mặc định")
    void TC_PAYMENT_016_không_cho_xóa_mặc_định() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Tài khoản mặc định là nòng cốt, không được phép xóa.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 120: if (account.getIsDefault()) return error

        BankAccount acc = createRaw("DEF", "1", "D", true, true);
        long countBefore = bankAccountRepository.count();
        
        ApiResponse response = bankAccountService.delete(acc.getId());
        assertFalse(response.isSuccess());
        assertEquals("Không thể xóa tài khoản mặc định", response.getMessage());
        assertEquals(countBefore, bankAccountRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_017 - Xóa thất bại khi ID không tồn tại")
    void TC_PAYMENT_017_xóa_id_không_tồn_tại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Xóa ID ảo. Kiểm tra ném Exception và rollback.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 118: .orElseThrow(...)

        long countBefore = bankAccountRepository.count();
        assertThrows(RuntimeException.class, () -> bankAccountService.delete(9999L));
        assertEquals(countBefore, bankAccountRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_018 - Thiết lập tài khoản bất kỳ làm mặc định, kiểm tra gỡ bỏ cũ và tự kích hoạt")
    void TC_PAYMENT_018_set_default_thành_công() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Sử dụng hàm chuyên dụng setDefault. Kiểm tra gỡ cũ và tự động active tài khoản mới.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 145: account.setIsActive(true)

        BankAccount old = createRaw("OLD", "0", "O", true, true);
        BankAccount target = createRaw("TARGET", "1", "T", false, false);
        
        bankAccountService.setDefault(target.getId());
        assertFalse(bankAccountRepository.findById(old.getId()).get().getIsDefault());
        BankAccount dbTarget = bankAccountRepository.findById(target.getId()).get();
        assertTrue(dbTarget.getIsDefault());
        assertTrue(dbTarget.getIsActive());
    }

    @Test
    @DisplayName("TC_PAYMENT_019 - setDefault thất bại khi ID không tồn tại")
    void TC_PAYMENT_019_set_default_id_không_tồn_tại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Thao tác trên ID ảo.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 134: .orElseThrow(...)

        long countBefore = bankAccountRepository.count();
        assertThrows(RuntimeException.class, () -> bankAccountService.setDefault(9999L));
        assertEquals(countBefore, bankAccountRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_020 - Chuyển trạng thái từ Kích hoạt sang Vô hiệu hóa")
    void TC_PAYMENT_020_toggle_active_sang_false() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Sử dụng toggleActive để đảo trạng thái.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 159: account.setIsActive(!account.getIsActive())

        BankAccount acc = createRaw("A", "1", "N", true, false);
        bankAccountService.toggleActive(acc.getId());
        assertFalse(bankAccountRepository.findById(acc.getId()).get().getIsActive());
    }

    @Test
    @DisplayName("TC_PAYMENT_021 - Chuyển trạng thái từ Vô hiệu hóa sang Kích hoạt")
    void TC_PAYMENT_021_toggle_active_sang_true() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Đảo ngược trạng thái từ False sang True.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 159: account.setIsActive(!account.getIsActive())

        BankAccount acc = createRaw("A", "1", "N", false, false);
        bankAccountService.toggleActive(acc.getId());
        assertTrue(bankAccountRepository.findById(acc.getId()).get().getIsActive());
    }

    @Test
    @DisplayName("TC_PAYMENT_022 - toggleActive thất bại khi ID không tồn tại")
    void TC_PAYMENT_022_toggle_active_id_không_tồn_tại() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Đảm bảo không lỗi crash khi truyền ID sai vào toggle.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 157: .orElseThrow(...)

        long countBefore = bankAccountRepository.count();
        assertThrows(RuntimeException.class, () -> bankAccountService.toggleActive(9999L));
        assertEquals(countBefore, bankAccountRepository.count());
    }

    @Test
    @DisplayName("TC_PAYMENT_023 - setDefault thành công khi hệ thống chưa có mặc định nào")
    void TC_PAYMENT_023_set_default_khi_trống() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Thiết lập default đầu tiên cho hệ thống.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 144: account.setIsDefault(true)

        BankAccount target = createRaw("A", "1", "N", true, false);
        bankAccountService.setDefault(target.getId());
        assertTrue(bankAccountRepository.findById(target.getId()).get().getIsDefault());
    }

    @Test
    @DisplayName("TC_PAYMENT_024 - Tạo tài khoản với trạng thái vô hiệu hóa (Success Case)")
    void TC_PAYMENT_024_tạo_mới_vô_hiệu_hóa_thành_công() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Cho phép tạo tài khoản nhưng chưa kích hoạt ngay.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 60: .isActive(request.getIsActive() != null ? request.getIsActive() : true)

        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("A").bankName("B").accountNumber("1").accountName("N")
                .isActive(false).isDefault(false).build();
        
        ApiResponse response = bankAccountService.create(req);
        assertFalse(((BankAccountResponse)response.getData()).getIsActive());
    }

    @Test
    @DisplayName("TC_PAYMENT_025 - Cập nhật tài khoản thành mặc định khi hệ thống chưa có tài khoản mặc định nào")
    void TC_PAYMENT_025_update_default_khi_trống() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Nâng cấp 1 tài khoản lên làm mặc định qua lệnh update.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 102: if (request.getIsDefault() != null && request.getIsDefault())

        BankAccount acc = createRaw("A", "1", "N", true, false);
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("A").bankName("B").accountNumber("1").accountName("N")
                .isDefault(true).build();
        
        bankAccountService.update(acc.getId(), req);
        assertTrue(bankAccountRepository.findById(acc.getId()).get().getIsDefault());
    }

    @Test
    @DisplayName("TC_PAYMENT_026 - Kiểm tra hành vi khi gửi isDefault=false trong lệnh update")
    void TC_PAYMENT_026_update_default_false_không_thay_đổi() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Code hiện tại chỉ xử lý IF isDefault=true. Nếu gửi false, nó không gỡ bỏ default cũ. 
        // Test này xác nhận hành vi hiện tại của code.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 101: if (request.getIsDefault() != null && request.getIsDefault())

        BankAccount acc = createRaw("A", "1", "N", true, true);
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("A").bankName("B").accountNumber("1").accountName("N")
                .isDefault(false).build();
        
        bankAccountService.update(acc.getId(), req);
        assertTrue(bankAccountRepository.findById(acc.getId()).get().getIsDefault());
    }

    @Test
    @DisplayName("TC_PAYMENT_027 - Cập nhật trạng thái isActive từ false sang true")
    void TC_PAYMENT_027_update_active_sang_true() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Tái kích hoạt tài khoản.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 97: account.setIsActive(request.getIsActive())

        BankAccount acc = createRaw("A", "1", "N", false, false);
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("A").bankName("B").accountNumber("1").accountName("N")
                .isActive(true).build();
        
        bankAccountService.update(acc.getId(), req);
        assertTrue(bankAccountRepository.findById(acc.getId()).get().getIsActive());
    }

    @Test
    @DisplayName("TC_PAYMENT_028 - [BUG HUNT] Ngăn chặn vô hiệu hóa tài khoản đang là mặc định (via toggle)")
    void TC_PAYMENT_028_không_cho_toggle_inactive_mặc_định() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Gatekeeper: Chặn vô hiệu hóa tài khoản default để tránh hệ thống không có tài khoản thanh toán nào active.
        //
        // [ÁNH XẠ LOGIC CODE]
        // Logic này chưa có trong code. Test case này đóng vai trò săn lỗi.

        BankAccount acc = createRaw("DEF", "1", "D", true, true);
        ApiResponse response = bankAccountService.toggleActive(acc.getId());
        
        assertFalse(response.isSuccess(), "Hệ thống không được phép vô hiệu hóa tài khoản mặc định");
        assertTrue(bankAccountRepository.findById(acc.getId()).get().getIsActive());
    }

    @Test
    @DisplayName("TC_PAYMENT_029 - [BUG HUNT] Ngăn chặn việc vô hiệu hóa tài khoản đang là mặc định (via update)")
    void TC_PAYMENT_029_không_cho_update_inactive_mặc_định() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Chặn vô hiệu hóa qua lệnh update.
        //
        // [ÁNH XẠ LOGIC CODE]
        // Logic này chưa có trong code.

        BankAccount acc = createRaw("D", "1", "D", true, true);
        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("D").bankName("D").accountNumber("1").accountName("D")
                .isActive(false).build();
        
        ApiResponse response = bankAccountService.update(acc.getId(), req);
        assertFalse(response.isSuccess(), "Hệ thống không được phép cho vô hiệu hóa tài khoản mặc định");
        assertTrue(bankAccountRepository.findById(acc.getId()).get().getIsActive());
    }

    @Test
    @DisplayName("TC_PAYMENT_030 - [BUG HUNT] Ngăn chặn tạo tài khoản mặc định nhưng ở trạng thái vô hiệu hóa")
    void TC_PAYMENT_030_không_cho_tạo_mặc_định_inactive() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Chặn ngay từ khâu tạo mới.
        //
        // [ÁNH XẠ LOGIC CODE]
        // Logic này chưa có trong code.

        BankAccountRequest req = BankAccountRequest.builder()
                .bankCode("BUG").bankName("B").accountNumber("1").accountName("N")
                .isActive(false).isDefault(true).build();
        
        ApiResponse response = bankAccountService.create(req);
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("TC_PAYMENT_031 - [BUG HUNT] Kiểm tra cập nhật từng phần (Partial Update) không làm mất dữ liệu cũ")
    void TC_PAYMENT_031_update_từng_phần_không_mất_data() {
        // [MỤC ĐÍCH NGHIỆP VỤ]
        // Đảm bảo API hỗ trợ Partial Update, không ghi đè null lên các trường không gửi trong request.
        //
        // [ÁNH XẠ LOGIC CODE]
        // BankAccountServiceImpl.java line 88-94: Kiểm tra xem các setter có check null không.

        BankAccount acc = createRaw("VCB", "123", "NAME", true, false);
        BankAccountRequest req = BankAccountRequest.builder().isActive(false).build();
        
        bankAccountService.update(acc.getId(), req);
        BankAccount updated = bankAccountRepository.findById(acc.getId()).get();
        
        assertFalse(updated.getIsActive());
        assertEquals("VCB", updated.getBankCode());
        assertEquals("NAME", updated.getAccountName());
    }
}
