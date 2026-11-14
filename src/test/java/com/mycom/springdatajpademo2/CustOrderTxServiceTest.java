package com.mycom.springdatajpademo2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.mycom.springdatajpademo2.config.AppTimeZone;
import com.mycom.springdatajpademo2.model.CustOrder;
import com.mycom.springdatajpademo2.repository.CustOrderRepository;
import com.mycom.springdatajpademo2.service.CustOrderTxService;

import lombok.extern.slf4j.Slf4j;

/**
 * ทดสอบ CustOrderTxService (transaction ด้วย PlatformTransactionManager / TransactionTemplate)
 * ใช้ order_id 3001-3002 ลบทิ้งก่อนและหลังทุก test ไม่ชนกับข้อมูลของ CustOrderTest (2011-2020)
 */
@Slf4j
@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class CustOrderTxServiceTest {

	private static final int ID1 = 3001;
	private static final int ID2 = 3002;

	@Autowired
	CustOrderTxService txService;

	@Autowired
	CustOrderRepository custOrderRepository;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		custOrderRepository.deleteByOrderId(ID1);
		custOrderRepository.deleteByOrderId(ID2);
	}

	private static CustOrder newOrder(int orderId, String amount) {
		return new CustOrder()
				.setOrderId(orderId)
				.setCustomerName("tx_customer" + orderId)
				.setTotalAmount(new BigDecimal(amount))
				.setOrderDate(LocalDate.now(AppTimeZone.ZONE_ID))
				.setInsertDatetime(Instant.now());
	}

	@Test
	void insertAllManual_commit() {
		int rows = txService.insertAllManual(List.of(newOrder(ID1, "1000"), newOrder(ID2, "2000")));

		assertEquals(2, rows);
		assertTrue(custOrderRepository.findById(ID1).isPresent());
		assertTrue(custOrderRepository.findById(ID2).isPresent());
	}

	@Test
	void insertAllManual_rollback() {
		// รายการที่ 2 order_id ซ้ำกับรายการแรก -> DataIntegrityViolationException
		assertThrows(DataIntegrityViolationException.class,
				() -> txService.insertAllManual(List.of(newOrder(ID1, "1000"), newOrder(ID1, "2000"))));

		// รายการแรกต้องถูก rollback ด้วย
		assertTrue(custOrderRepository.findById(ID1).isEmpty());
	}

	@Test
	void insertAll_rollback() {
		assertThrows(DataIntegrityViolationException.class,
				() -> txService.insertAll(List.of(newOrder(ID1, "1000"), newOrder(ID1, "2000"))));

		assertTrue(custOrderRepository.findById(ID1).isEmpty());
	}

	/**
	 * ต่างจาก JdbcClient: ID1 มีใน DB อยู่แล้ว (ไม่ได้อยู่ใน transaction นี้)
	 * save() ยังไม่ส่ง INSERT ทันที error จึงเกิดตอน commit หลัง lambda จบ แต่ก็ยัง rollback ทั้งชุด
	 */
	@Test
	void insertAll_duplicateWithDb_errorAtCommit() {
		txService.insertAll(List.of(newOrder(ID1, "1000")));

		assertThrows(DataIntegrityViolationException.class,
				() -> txService.insertAll(List.of(newOrder(ID2, "2000"), newOrder(ID1, "9999"))));

		assertTrue(custOrderRepository.findById(ID2).isEmpty()); // ID2 ถูก rollback
		assertAmount(ID1, "1000");                                // ID1 ยังเป็นค่าเดิม ไม่ถูกทับ
	}

	@Test
	void updateTotalAmount_setRollbackOnly() {
		txService.insertAll(List.of(newOrder(ID1, "1000")));

		// 1000 -> 100 ต่ำกว่าครึ่ง -> setRollbackOnly() ยอดต้องยังเป็น 1000
		assertFalse(txService.updateTotalAmount(ID1, new BigDecimal("100")));
		assertAmount(ID1, "1000");

		// 1000 -> 800 ผ่านเงื่อนไข -> commit (dirty checking ไม่ต้องเรียก save)
		assertTrue(txService.updateTotalAmount(ID1, new BigDecimal("800")));
		assertAmount(ID1, "800");
	}

	@Test
	void findAll_readOnly() {
		txService.insertAll(List.of(newOrder(ID1, "1000")));

		var datas = txService.findAll();
		datas.forEach(data -> log.info("custOrder={}", data));
		assertTrue(datas.stream().anyMatch(o -> o.getOrderId() == ID1));
	}

	private void assertAmount(int orderId, String expected) {
		BigDecimal actual = custOrderRepository.findById(orderId).orElseThrow().getTotalAmount();
		// DB คืน 1000.00 ใช้ compareTo เพื่อไม่สนจำนวนทศนิยม
		assertEquals(0, new BigDecimal(expected).compareTo(actual), "totalAmount=" + actual);
	}
}
