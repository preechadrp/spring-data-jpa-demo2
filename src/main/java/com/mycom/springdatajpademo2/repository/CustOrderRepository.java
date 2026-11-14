package com.mycom.springdatajpademo2.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.springdatajpademo2.dto.CustOrderSummary;
import com.mycom.springdatajpademo2.model.CustOrder;

/**
 * Spring Data JPA: เขียนแค่ interface Spring สร้าง class ให้ตอน runtime
 *
 * เทียบกับเวอร์ชัน JdbcClient (spring-jdbc-client-demo1)
 *
 *   JdbcClient                                  Spring Data JPA
 *   insert(custOrder)                       ->  save(custOrder)            (มีให้แล้วจาก JpaRepository)
 *   update(custOrder)                       ->  save(custOrder)            (ของเดิม -> UPDATE)
 *   findById(orderId)                       ->  findById(orderId)          (มีให้แล้ว คืน Optional)
 *   findAll() ORDER BY order_id             ->  findAllByOrderByOrderIdAsc()  (สร้าง query จากชื่อ method)
 *   findByCustomerName(name)                ->  findByCustomerNameOrderByOrderIdAsc(name)
 *   getCustName(orderId)                    ->  @Query (JPQL) เลือกฟิลด์เดียว
 *   deleteById(orderId) คืนจำนวนแถว          ->  deleteByOrderId(orderId)   (@Modifying @Query)
 *
 * วิธีสร้าง query มี 3 แบบ
 *  1. ตั้งชื่อ method ตามรูปแบบ (derived query) เช่น findByCustomerName
 *  2. @Query ด้วย JPQL: เขียนด้วยชื่อ class/field ของ Java ไม่ใช่ชื่อตาราง/คอลัมน์
 *  3. @Query(nativeQuery = true): เขียน SQL ตรงๆ ของ database
 *
 * transaction: method ที่มากับ JpaRepository (save, findById, delete ...) มี @Transactional อยู่แล้ว
 * แต่ method ที่ประกาศเองใน interface นี้ไม่มี ถ้าเป็นการแก้ข้อมูล (@Modifying) ต้องใส่ @Transactional เอง
 */
public interface CustOrderRepository extends JpaRepository<CustOrder, Integer> {

	// ===== 1. derived query: Spring อ่านชื่อ method แล้วสร้าง JPQL ให้ =====

	/** SELECT ... FROM cust_order ORDER BY order_id */
	List<CustOrder> findAllByOrderByOrderIdAsc();

	/** SELECT ... FROM cust_order WHERE customer_name = ? ORDER BY order_id */
	List<CustOrder> findByCustomerNameOrderByOrderIdAsc(String customerName);

	/** SELECT ... WHERE total_amount > ? ORDER BY total_amount DESC */
	List<CustOrder> findByTotalAmountGreaterThanOrderByTotalAmountDesc(BigDecimal minAmount);

	// ===== 2. @Query ด้วย JPQL (ใช้ชื่อ entity CustOrder และ field customerName ไม่ใช่ชื่อตาราง) =====

	/** ดึงค่าฟิลด์เดียว: 0 แถว -> Optional.empty() */
	@Query("SELECT c.customerName FROM CustOrder c WHERE c.orderId = :orderId")
	Optional<String> findCustomerNameByOrderId(@Param("orderId") int orderId);

	/** เหมือน getCustName ของเวอร์ชัน JdbcClient: คืน customer_name หรือ null ถ้าไม่พบ */
	default String getCustName(int orderId) {
		return findCustomerNameByOrderId(orderId).orElse(null);
	}

	/** สร้าง DTO จากผลรวม (constructor expression): ใช้ constructor CustOrderSummary(String, BigDecimal) */
	@Query("""
			SELECT new com.mycom.springdatajpademo2.dto.CustOrderSummary(c.customerName, SUM(c.totalAmount))
			FROM CustOrder c
			GROUP BY c.customerName
			ORDER BY c.customerName
			""")
	List<CustOrderSummary> summarizeByCustomer();

	/**
	 * ลบแล้วคืนจำนวนแถว (deleteById ของ JpaRepository คืน void)
	 * @Modifying = เป็นคำสั่ง UPDATE/DELETE ไม่ใช่ SELECT ต้องมี transaction
	 * clearAutomatically: ล้าง entity ที่ค้างใน persistence context หลังลบ ไม่ให้อ่านได้ค่าเก่า
	 */
	@Transactional
	@Modifying(clearAutomatically = true)
	@Query("DELETE FROM CustOrder c WHERE c.orderId = :orderId")
	int deleteByOrderId(@Param("orderId") int orderId);

	// ===== 3. native query: SQL ของ MariaDB ตรงๆ =====

	@Query(value = "SELECT * FROM cust_order WHERE order_date = CURDATE() ORDER BY order_id", nativeQuery = true)
	List<CustOrder> findTodayOrdersNative();
}
