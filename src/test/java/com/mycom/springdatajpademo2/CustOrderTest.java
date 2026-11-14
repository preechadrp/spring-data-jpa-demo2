package com.mycom.springdatajpademo2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.mycom.springdatajpademo2.component.AppConfig;
import com.mycom.springdatajpademo2.config.AppTimeZone;
import com.mycom.springdatajpademo2.model.CustOrder;
import com.mycom.springdatajpademo2.repository.CustOrderRepository;
import com.mycom.springdatajpademo2.service.CustOrderService;

import lombok.extern.slf4j.Slf4j;

//useMainMethod = ALWAYS ให้ test start ผ่าน main() เพื่อให้ TimeZone.setDefault("Asia/Bangkok") ทำงานเหมือนตอนรันจริง
//(ค่า default คือ NEVER = ไม่เรียก main() timezone จะขึ้นกับเครื่องที่รัน test)

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
public class CustOrderTest {

	@Autowired
	public CustOrderRepository custOrderRepository;

	@Autowired
	public CustOrderService custOrderService;

	// starter-data-jpa มี spring-jdbc มาด้วย Spring Boot จึงสร้าง JdbcClient ให้ ใช้คู่กับ JPA ได้
	@Autowired
	public JdbcClient jdbcClient;

	private final int start = 2011;
	private final int to = start + 5;

	@Test
	@Order(1)
	void delete() {

		//แบบไม่ต้องใส่ตอน constructor class ทดสอบแสดงข้อมูลจากการใช้ @DependsOn("AppConfig")
		log.info("AppConfig.getInstance().getName() = {}", AppConfig.getInstance().getName());

		log.info("====== start delete");
		for (int idx = start; idx < to + 5; idx++) {
			int eff_row = custOrderRepository.deleteByOrderId(idx);
			log.info("delete idx={}, effect row={}", idx, eff_row);
		}
	}

	@Test
	@Order(2)
	void insert() {
		log.info("====== start insert");
		for (int idx = start; idx < to; idx++) {
			var custorder = new CustOrder()
					.setOrderId(idx)
					.setCustomerName("customer_name" + idx)
					.setTotalAmount(new BigDecimal(idx + "00"))
					.setOrderDate(LocalDate.now())
					.setInsertDatetime(Instant.now());

			custOrderRepository.save(custorder);
		}
	}

	@Test
	@Order(3)
	void insertByBatch() {
		log.info("====== start insertByBatch");
		int rows = custOrderService.insertOrdersByBatch(to, 5);
		log.info("insertByBatch rows={}", rows);
		assertEquals(5, rows);
	}

	@Test
	@Order(4)
	void findAll() {
		log.info("====== start findAll");
		var datas = custOrderRepository.findAllByOrderByOrderIdAsc();
		for (CustOrder custOrder : datas) {
			log.info("custOrder={}", custOrder.toString());
		}
	}

	@Test
	@Order(5)
	void findById() {
		log.info("====== start findById");
		// findById คืน Optional จึงใช้ ifPresentOrElse แทนการเช็ค null
		custOrderRepository.findById(this.start).ifPresentOrElse(
				data -> log.info("custOrder={}", data.toString()),
				() -> log.info("Not found data."));
	}

	@Test
	@Order(6)
	void findByCustomerName() {
		log.info("====== start findByCustomerName");
		var datas = custOrderRepository.findByCustomerNameOrderByOrderIdAsc("customer_name" + start);
		if (datas.isEmpty()) {
			log.info("Not found data.");
			return;
		}
		for (CustOrder data : datas) {
			log.info("custOrder={}", data.toString());
		}
	}

	@Test
	@Order(7)
	void getCustName() {
		log.info("====== start getCustName");
		assertEquals("customer_name" + start, custOrderRepository.getCustName(start));
		assertNull(custOrderRepository.getCustName(-1));
	}

	@Test
	@Order(8)
	void summarizeByCustomer() {
		log.info("====== start summarizeByCustomer");
		custOrderRepository.summarizeByCustomer().forEach(s -> log.info("summary={}", s));
	}

	@Test
	@Order(9)
	void update() {
		log.info("====== start update");
		var found = custOrderRepository.findById(this.start);
		if (found.isPresent()) {
			var data = found.get();
			data.setTotalAmount(new BigDecimal("200"));
			// อยู่นอก transaction entity จึงหลุดจาก Hibernate แล้ว ต้องเรียก save() (merge -> UPDATE)
			custOrderRepository.save(data);
			log.info("update={}", data.toString());
		} else {
			log.info("Not found data.");
		}
	}

	/** ตรวจว่า Instant ถูกเก็บใน DATETIME เป็นเวลาไทย (ไม่ใช่ UTC ที่เป็นค่าเริ่มต้นของ Hibernate) */
	@Test
	@Order(10)
	void insertDatetime_storedAsBangkokTime() {
		log.info("====== start insertDatetime_storedAsBangkokTime");
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS); // DATETIME(6) เก็บได้ถึงไมโครวินาที
		custOrderRepository.save(custOrderRepository.findById(start).orElseThrow().setInsertDatetime(now));

		// อ่านค่าดิบในคอลัมน์ด้วย SQL ตรงๆ (ไม่ผ่านการแปลงของ Hibernate)
		LocalDateTime rawInDb = jdbcClient
				.sql("SELECT insert_datetime FROM cust_order WHERE order_id = :id")
				.param("id", start)
				.query(LocalDateTime.class)
				.single();
		log.info("Instant={}, DATETIME in DB={}", now, rawInDb);
		assertEquals(LocalDateTime.ofInstant(now, AppTimeZone.ZONE_ID), rawInDb);

		// อ่านกลับผ่าน JPA ต้องได้ Instant เดิม
		assertEquals(now, custOrderRepository.findById(start).orElseThrow().getInsertDatetime());
	}
}
