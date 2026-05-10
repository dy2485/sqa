package com.doan.WEB_TMDT.module.cart.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.config.TestConfigMock;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.entity.Role;
import com.doan.WEB_TMDT.module.auth.entity.Status;
import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.auth.repository.CustomerRepository;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import com.doan.WEB_TMDT.module.cart.dto.AddToCartRequest;
import com.doan.WEB_TMDT.module.cart.dto.CartItemResponse;
import com.doan.WEB_TMDT.module.cart.dto.CartResponse;
import com.doan.WEB_TMDT.module.cart.dto.UpdateCartItemRequest;
import com.doan.WEB_TMDT.module.cart.entity.Cart;
import com.doan.WEB_TMDT.module.cart.entity.CartItem;
import com.doan.WEB_TMDT.module.cart.repository.CartItemRepository;
import com.doan.WEB_TMDT.module.cart.repository.CartRepository;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.test.mock.mockito.MockBean;
import com.doan.WEB_TMDT.module.shipping.client.GHNApiClient;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional(propagation = Propagation.REQUIRED)
@DisplayName("CART SERVICE TEST")
@ActiveProfiles("test")
@Import(TestConfigMock.class)
@ExtendWith(CartServiceIntegrationTest.ResultReporter.class)
class CartServiceIntegrationTest {

    @MockBean
    private GHNApiClient ghnApiClient;

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
            if (displayName.startsWith("TC_CART_")) {
                try {
                    int id = Integer.parseInt(displayName.substring(8, 11));
                    testResults.put(id, status);
                } catch (Exception ignored) {}
            }
        }
    }

    @AfterAll
    static void printSummary() {
        System.out.println("\n========================================================================");
        System.out.println("TEST SUMMARY REPORT (Hardened Mode)");
        System.out.println("========================================================================");
        for (int i = 1; i <= 30; i++) {
            String status = testResults.getOrDefault(i, "NA");
            System.out.printf("TC_CART_%03d : %s\n", i, status);
        }
        System.out.println("========================================================================\n");
    }

    @Autowired private CartServiceImpl cartService;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("TC_CART_001 - Lay ID khach hang thanh cong khi email ton tai")
    void TC_CART_001_getCustomerIdByEmail_success() {
        User user = userRepository.save(User.builder().email("u1@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer customer = customerRepository.save(Customer.builder().user(user).fullName("A").phone("1").build());
        Long resultId = cartService.getCustomerIdByEmail("u1@g.com");
        assertNotNull(resultId, "ID khach hang bi null");
        assertEquals(customer.getId(), resultId, "ID khach hang khong khop");
    }

    @Test
    @DisplayName("TC_CART_002 - Lay ID khach hang that bai khi email khong ton tai")
    void TC_CART_002_getCustomerIdByEmail_notFound() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> cartService.getCustomerIdByEmail("wrong@g.com"));
        // SỦA LỖI CHÍNH TẢ: Khớp với chuỗi có dấu của hệ thống
        assertEquals("Không tìm thấy khách hàng với email: wrong@g.com", ex.getMessage(), "Message loi sai");
    }

    @Test
    @DisplayName("TC_CART_003 - Lay ID khach hang that bai khi tham so email la null")
    void TC_CART_003_getCustomerIdByEmail_nullParam() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> cartService.getCustomerIdByEmail(null));
        // SỦA LỖI CHÍNH TẢ
        assertEquals("Không tìm thấy khách hàng với email: null", ex.getMessage(), "Message loi sai khi email null");
    }

    @Test
    @DisplayName("TC_CART_004 - Lay gio hang that bai khi Khach hang khong ton tai")
    void TC_CART_004_getCart_customerNotFound() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> cartService.getCart(99999L));
        // SỦA LỖI CHÍNH TẢ
        assertEquals("Không tìm thấy khách hàng", ex.getMessage(), "Message loi sai khi ID khong ton tai");
    }

    @Test
    @DisplayName("TC_CART_005 - Tu dong tao gio hang moi khi Khach truy cap lan dau")
    void TC_CART_005_getCart_createFirstTime() {
        User u = userRepository.save(User.builder().email("u5@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U5").phone("05").build());
        ApiResponse res = cartService.getCart(c.getId());
        assertTrue(res.isSuccess());
    }

    @Test
    @DisplayName("TC_CART_006 - Lay gio hang thanh cong khi da co do")
    void TC_CART_006_getCart_success_withItems() {
        User u = userRepository.save(User.builder().email("u6@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U6").phone("06").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("Laptop").sku("L6").stockQuantity(10L).reservedQuantity(0L).price(1000.0).build());
        cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(2).price(1000.0).build());

        ApiResponse res = cartService.getCart(c.getId());
        CartResponse dto = (CartResponse) res.getData();
        
        // BUG MAPPING: Code Dev tra ve items.size() = 0 du DB co data
        assertEquals(1, dto.getItems().size(), "Size list items bi sai (Mapping Bug)");
    }

    @Test
    @DisplayName("TC_CART_007 - Lay gio hang thanh cong khi san pham khong co anh")
    void TC_CART_007_getCart_success_nullImage() {
        User u = userRepository.save(User.builder().email("u7@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U7").phone("07").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P7").sku("S7").stockQuantity(10L).reservedQuantity(0L).price(100.0).build());
        cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(1).price(100.0).build());

        ApiResponse res = cartService.getCart(c.getId());
        CartResponse dto = (CartResponse) res.getData();
        
        // BUG CRASH: Code Dev vang loi IndexOutOfBounds thay vi handle null
        assertNotNull(dto.getItems(), "Items list bi null");
        assertFalse(dto.getItems().isEmpty(), "Items list bi rong (Mapping Bug)");
        assertNull(dto.getItems().get(0).getProductImage(), "Anh san pham phai null");
    }

    @Test
    @DisplayName("TC_CART_008 - Them san pham that bai do SP khong ton tai")
    void TC_CART_008_addToCart_productNotFound() {
        User u = userRepository.save(User.builder().email("u8@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U8").phone("08").build());
        AddToCartRequest req = new AddToCartRequest(); req.setProductId(999L); req.setQuantity(1);
        assertThrows(RuntimeException.class, () -> cartService.addToCart(c.getId(), req));
    }

    @Test
    @DisplayName("TC_CART_009 - SP MOI: Them that bai do So luong mua > Ton kho kha dung")
    void TC_CART_009_addToCart_newProduct_exceedStock() {
        User u = userRepository.save(User.builder().email("u9@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U9").phone("09").build());
        Product p = productRepository.save(Product.builder().name("P9").sku("S9").stockQuantity(5L).reservedQuantity(4L).price(10.0).build());
        AddToCartRequest req = new AddToCartRequest(); req.setProductId(p.getId()); req.setQuantity(2);

        ApiResponse res = cartService.addToCart(c.getId(), req);
        // SỦA LỖI CHÍNH TẢ
        assertFalse(res.isSuccess());
        assertEquals("Sản phẩm không đủ số lượng. Còn lại: 1 sản phẩm", res.getMessage(), "Message ton kho sai");
    }

    @Test
    @DisplayName("TC_CART_010 - SP MOI: Them hop le vao DB")
    void TC_CART_010_addToCart_newProduct_success() {
        User u = userRepository.save(User.builder().email("u10@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U10").phone("10").build());
        Product p = productRepository.save(Product.builder().name("P10").sku("S10").stockQuantity(100L).reservedQuantity(0L).price(500.0).build());
        AddToCartRequest req = new AddToCartRequest(); req.setProductId(p.getId()); req.setQuantity(3);

        ApiResponse res = cartService.addToCart(c.getId(), req);
        assertTrue(res.isSuccess());
        CartResponse dto = (CartResponse) res.getData();
        
        // BUG MAPPING: Code Dev tra ve TotalItems = 0
        assertEquals(3, dto.getTotalItems(), "DTO TotalItems bi sai (Mapping Bug)");
        
        CartItem dbItem = cartItemRepository.findAll().stream()
                .filter(i -> i.getProduct().getId().equals(p.getId()))
                .findFirst().orElseThrow(() -> new AssertionError("DB khong luu record"));
        assertEquals(3, dbItem.getQuantity(), "DB Quantity bi sai");
    }

    @Test
    @DisplayName("TC_CART_011 - SP DA CO: Them that bai do Tong SL (Cu + Moi) > Ton kho")
    void TC_CART_011_addToCart_existingProduct_exceedStock() {
        User u = userRepository.save(User.builder().email("u11@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U11").phone("11").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P11").sku("S11").stockQuantity(10L).reservedQuantity(5L).price(100.0).build());
        cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(4).price(100.0).build());
        AddToCartRequest req = new AddToCartRequest(); req.setProductId(p.getId()); req.setQuantity(2);
        ApiResponse res = cartService.addToCart(c.getId(), req);
        assertFalse(res.isSuccess());
    }

    @Test
    @DisplayName("TC_CART_012 - SP DA CO: Cong don SL thanh cong")
    void TC_CART_012_addToCart_existingProduct_success() {
        User u = userRepository.save(User.builder().email("u12@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U12").phone("12").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P12").sku("S12").stockQuantity(100L).reservedQuantity(0L).price(100.0).build());
        cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(2).price(100.0).build());

        AddToCartRequest req = new AddToCartRequest(); req.setProductId(p.getId()); req.setQuantity(3);
        ApiResponse res = cartService.addToCart(c.getId(), req);
        assertTrue(res.isSuccess());
    }

    @Test
    @DisplayName("TC_CART_013 - Xu ly an toan khi Data Kho bi NULL")
    void TC_CART_013_addToCart_nullStockHandling() {
        User u = userRepository.save(User.builder().email("u13@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U13").phone("13").build());
        Product p = productRepository.save(Product.builder().name("P").sku("S13").stockQuantity(null).reservedQuantity(null).price(100.0).build());
        AddToCartRequest req = new AddToCartRequest(); req.setProductId(p.getId()); req.setQuantity(1);
        ApiResponse res = cartService.addToCart(c.getId(), req);
        assertFalse(res.isSuccess());
    }

    @Test
    @DisplayName("TC_CART_014 - [BAO MAT] Them san pham voi so luong am")
    void TC_CART_014_addToCart_negativeQuantity() {
        User u = userRepository.save(User.builder().email("u14@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U14").phone("14").build());
        Product p = productRepository.save(Product.builder().name("P").sku("S14").stockQuantity(100L).reservedQuantity(0L).price(100.0).build());
        AddToCartRequest req = new AddToCartRequest(); req.setProductId(p.getId()); req.setQuantity(-5);

        ApiResponse res = cartService.addToCart(c.getId(), req);
        // BUG BAO MAT: He thong cho phep so luong am
        assertFalse(res.isSuccess(), "He thong phai chan so luong am (Security Bug)");
    }

    @Test
    @DisplayName("TC_CART_015 - [BAO MAT] Them san pham voi so luong tran so")
    void TC_CART_015_addToCart_overflowQuantity() {
        User u = userRepository.save(User.builder().email("u15@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U15").phone("15").build());
        Product p = productRepository.save(Product.builder().name("P").sku("S15").stockQuantity(100L).reservedQuantity(0L).price(100.0).build());
        AddToCartRequest req = new AddToCartRequest(); req.setProductId(p.getId()); req.setQuantity(Integer.MAX_VALUE);
        ApiResponse res = cartService.addToCart(c.getId(), req);
        assertFalse(res.isSuccess());
    }

    @Test
    @DisplayName("TC_CART_016 - Cap nhat that bai do ID Item khong ton tai")
    void TC_CART_016_updateCartItem_itemNotFound() {
        User u = userRepository.save(User.builder().email("u16@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U16").phone("16").build());
        UpdateCartItemRequest req = new UpdateCartItemRequest(); req.setQuantity(2);
        assertThrows(RuntimeException.class, () -> cartService.updateCartItem(c.getId(), 999L, req));
    }

    @Test
    @DisplayName("TC_CART_017 - [BAO MAT] BOLA Attack - Sua so luong cua khach khac")
    void TC_CART_017_updateCartItem_wrongCustomer() {
        User uA = userRepository.save(User.builder().email("ua@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        User uB = userRepository.save(User.builder().email("ub@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer cA = customerRepository.save(Customer.builder().user(uA).fullName("A").phone("171").build());
        Customer cB = customerRepository.save(Customer.builder().user(uB).fullName("B").phone("172").build());
        Cart cartA = cartRepository.save(Cart.builder().customer(cA).build());
        Product p = productRepository.save(Product.builder().name("P").sku("S17").stockQuantity(10L).reservedQuantity(0L).price(100.0).build());
        CartItem itemA = cartItemRepository.save(CartItem.builder().cart(cartA).product(p).quantity(1).price(100.0).build());

        UpdateCartItemRequest req = new UpdateCartItemRequest(); req.setQuantity(5);
        ApiResponse res = cartService.updateCartItem(cB.getId(), itemA.getId(), req);

        // SỦA LỖI CHÍNH TẢ & KIEM TRA BUG PHAN QUYEN
        assertFalse(res.isSuccess(), "He thong cho phep hacker sua do nguoi khac (Security Bug)");
        assertEquals("Bạn không có quyền sửa sản phẩm này", res.getMessage(), "Message bao mat sai");
    }

    @Test
    @DisplayName("TC_CART_018 - Cap nhat that bai do So luong moi > Ton kho")
    void TC_CART_018_updateCartItem_exceedStock() {
        User u = userRepository.save(User.builder().email("u18@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U18").phone("18").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P").sku("S18").stockQuantity(10L).reservedQuantity(8L).price(100.0).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(1).price(100.0).build());
        UpdateCartItemRequest req = new UpdateCartItemRequest(); req.setQuantity(5);
        ApiResponse res = cartService.updateCartItem(c.getId(), item.getId(), req);
        assertFalse(res.isSuccess());
    }

    @Test
    @DisplayName("TC_CART_019 - Cap nhat THANH CONG so luong hop le")
    void TC_CART_019_updateCartItem_success() {
        User u = userRepository.save(User.builder().email("u19@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U19").phone("19").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("Mac").sku("S19").stockQuantity(50L).reservedQuantity(0L).price(2000.0).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(1).price(2000.0).build());

        UpdateCartItemRequest req = new UpdateCartItemRequest(); req.setQuantity(4);
        ApiResponse res = cartService.updateCartItem(c.getId(), item.getId(), req);
        assertTrue(res.isSuccess());
        CartResponse dto = (CartResponse) res.getData();
        
        // BUG LOGIC: Subtotal tra ve 0.0 sau khi update
        assertEquals(8000.0, dto.getSubtotal(), "Subtotal bi sai sau khi update (Logic Bug)");
    }

    @Test
    @DisplayName("TC_CART_020 - [BAO MAT] Cap nhat so luong ve 0")
    void TC_CART_020_updateCartItem_zeroQuantity() {
        User u = userRepository.save(User.builder().email("u20@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U20").phone("20").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P").sku("S20").stockQuantity(10L).reservedQuantity(0L).price(100.0).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(2).price(100.0).build());
        UpdateCartItemRequest req = new UpdateCartItemRequest(); req.setQuantity(0);
        ApiResponse res = cartService.updateCartItem(c.getId(), item.getId(), req);
        assertFalse(res.isSuccess(), "He thong cho phep update ve 0 (Security Bug)");
    }

    @Test
    @DisplayName("TC_CART_021 - [BAO MAT] Cap nhat so luong ve so am")
    void TC_CART_021_updateCartItem_negativeQuantity() {
        User u = userRepository.save(User.builder().email("u21@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U21").phone("21").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P").sku("S21").stockQuantity(10L).reservedQuantity(0L).price(100.0).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(2).price(100.0).build());
        UpdateCartItemRequest req = new UpdateCartItemRequest(); req.setQuantity(-3);
        ApiResponse res = cartService.updateCartItem(c.getId(), item.getId(), req);
        assertFalse(res.isSuccess(), "He thong cho phep update ve am (Security Bug)");
    }

    @Test
    @DisplayName("TC_CART_022 - Xoa that bai do ID Item khong ton tai")
    void TC_CART_022_removeCartItem_notFound() {
        User u = userRepository.save(User.builder().email("u22@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U22").phone("22").build());
        assertThrows(RuntimeException.class, () -> cartService.removeCartItem(c.getId(), 999L));
    }

    @Test
    @DisplayName("TC_CART_023 - [BAO MAT] Xoa that bai do BOLA Attack")
    void TC_CART_023_removeCartItem_wrongCustomer() {
        User uA = userRepository.save(User.builder().email("ua23@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        User uB = userRepository.save(User.builder().email("ub23@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer cA = customerRepository.save(Customer.builder().user(uA).fullName("A").phone("231").build());
        Customer cB = customerRepository.save(Customer.builder().user(uB).fullName("B").phone("232").build());
        Cart cartA = cartRepository.save(Cart.builder().customer(cA).build());
        Product p = productRepository.save(Product.builder().name("P").sku("S23").stockQuantity(10L).reservedQuantity(0L).price(100.0).build());
        CartItem itemA = cartItemRepository.save(CartItem.builder().cart(cartA).product(p).quantity(1).price(100.0).build());

        ApiResponse res = cartService.removeCartItem(cB.getId(), itemA.getId());
        // SỦA LỖI CHÍNH TẢ & KIEM TRA BUG PHAN QUYEN
        assertFalse(res.isSuccess(), "He thong cho phep hacker xoa do nguoi khac (Security Bug)");
        assertEquals("Bạn không có quyền xóa sản phẩm này", res.getMessage(), "Message bao mat sai");
    }

    @Test
    @DisplayName("TC_CART_024 - Xoa san pham THANH CONG")
    void TC_CART_024_removeCartItem_success() {
        User u = userRepository.save(User.builder().email("u24@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U24").phone("24").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P").sku("S24").stockQuantity(10L).reservedQuantity(0L).price(100.0).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(1).price(100.0).build());
        cart.getItems().add(item); cartRepository.save(cart);
        ApiResponse res = cartService.removeCartItem(c.getId(), item.getId());
        assertTrue(res.isSuccess());
    }

    @Test
    @DisplayName("TC_CART_025 - Xoa san pham cuoi cung trong gio -> Ve rong")
    void TC_CART_025_removeCartItem_lastItem() {
        User u = userRepository.save(User.builder().email("u25@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U25").phone("25").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P").sku("S25").stockQuantity(10L).reservedQuantity(0L).price(100.0).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(1).price(100.0).build());
        cart.getItems().add(item); cartRepository.save(cart);
        ApiResponse res = cartService.removeCartItem(c.getId(), item.getId());
        assertTrue(res.isSuccess());
    }

    @Test
    @DisplayName("TC_CART_026 - Clear sach gio hang dang chua nhieu do")
    void TC_CART_026_clearCart_withItems() {
        User u = userRepository.save(User.builder().email("u26@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U26").phone("26").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P").sku("S26").stockQuantity(10L).reservedQuantity(0L).price(100.0).build());
        
        // Nap 2 item vao gio
        cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(1).price(100.0).build());
        cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(2).price(100.0).build());

        ApiResponse res = cartService.clearCart(c.getId());
        assertTrue(res.isSuccess());
        
        // TRUY VAN NGHIEM KHAC: Phai check theo CartId de xem du lieu co thuc su bien mat khong
        long countAfter = cartItemRepository.findAll().stream()
                .filter(item -> item.getCart().getId().equals(cart.getId()))
                .count();
        
        assertEquals(0, countAfter, "Loi nghiep vu thuc te: Sau khi gọi clearCart, DB van con ban ghi cua cart nay");
    }

    @Test
    @DisplayName("TC_CART_027 - Clear gio hang da rong san")
    void TC_CART_027_clearCart_emptyCart() {
        User u = userRepository.save(User.builder().email("u27@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("U27").phone("27").build());
        cartRepository.save(Cart.builder().customer(c).build());
        ApiResponse res = cartService.clearCart(c.getId());
        assertTrue(res.isSuccess());
    }

    @Test
    @DisplayName("TC_CART_028 - Clear gio hang that bai do Customer null")
    void TC_CART_028_clearCart_nullCustomer() {
        assertThrows(RuntimeException.class, () -> cartService.clearCart(null));
    }

    @Test
    @DisplayName("TC_CART_029 - [NGHIEP VU] Phi ship phai duoc tinh toan (Khong duoc luon bang 0)")
    void TC_CART_029_calculateShippingFee_notAlwaysZero() {
        // [MUC DICH] Bat loi gán cứng phí ship = 0.0 trong code.
        User u = userRepository.save(User.builder().email("u29@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("A").phone("29").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("P").sku("S29").stockQuantity(10L).reservedQuantity(0L).price(100.0).build());
        cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(1).price(100.0).build());

        ApiResponse res = cartService.getCart(c.getId());
        CartResponse dto = (CartResponse) res.getData();

        // Gia su voi don hang 100k, phi ship mac dinh phai > 0 (vi du 30k)
        // Neu code van return 0.0 -> Test Fail
        assertNotEquals(0.0, dto.getShippingFee(), "Loi nghiep vu: Phi ship dang bi gan cung bang 0");
    }

    @Test
    @DisplayName("TC_CART_030 - [MAPPING] Kiem tra chi tiet Mapping san pham trong gio")
    void TC_CART_030_deepMappingCheck() {
        // [MUC DICH] Ep he thong phai chay qua ham toCartItemResponse (phu dong 222, 223, 226, 232)
        User u = userRepository.save(User.builder().email("u30@g.com").password("p").role(Role.CUSTOMER).status(Status.ACTIVE).build());
        Customer c = customerRepository.save(Customer.builder().user(u).fullName("A").phone("30").build());
        Cart cart = cartRepository.save(Cart.builder().customer(c).build());
        Product p = productRepository.save(Product.builder().name("Dien thoai").sku("S30").stockQuantity(50L).reservedQuantity(10L).price(500.0).build());
        cartItemRepository.save(CartItem.builder().cart(cart).product(p).quantity(2).price(500.0).build());

        ApiResponse res = cartService.getCart(c.getId());
        CartResponse dto = (CartResponse) res.getData();

        assertFalse(dto.getItems().isEmpty(), "Mapping loi: Gio hang trong DB co do nhung DTO tra ve rong");
        CartItemResponse item = dto.getItems().get(0);
        
        assertEquals("Dien thoai", item.getProductName(), "Mapping ten san pham sai");
        assertEquals(40, item.getStockQuantity(), "Mapping logic tinh ton kho kha dung (50-10) bi sai");
        assertTrue(item.getAvailable(), "Mapping logic available bi sai");
    }
}