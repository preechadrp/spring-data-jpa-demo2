package com.mycom.springdatajpademo2.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycom.springdatajpademo2.model.CustOrder;
import com.mycom.springdatajpademo2.repository.CustOrderRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * ตัวอย่างคุม transaction ด้วย PlatformTransactionManager ในโค้ด (programmatic) แทน @Transactional
 * (เทียบกับ CustOrderService ที่ใช้ @Transactional)
 *
 * PlatformTransactionManager มาจากไหน: Spring Boot สร้าง bean ชื่อ transactionManager
 * (JpaTransactionManager) ให้อัตโนมัติ inject มาใช้ได้เลยไม่ต้องสร้าง config เอง
 *
 * ทำไมใช้ได้: เมื่อ transaction manager เปิด transaction จะผูก EntityManager (และ Connection ของมัน)
 * ไว้กับ thread ปัจจุบัน repository ทุกตัวที่เรียกใน thread นั้นจึงใช้ EntityManager เดียวกัน
 *
 * มี 2 แบบ
 *  1. PlatformTransactionManager ตรงๆ : getTransaction() -> commit() / rollback() เขียนเอง
 *  2. TransactionTemplate              : ห่อแบบที่ 1 ไว้ให้ ส่งแค่ lambda (แนะนำ เขียนสั้นและพลาดยากกว่า)
 *
 * เรื่องที่ JPA ต่างจาก JdbcClient
 *  - save() ยังไม่ส่ง SQL ทันที Hibernate เก็บไว้แล้วส่งตอน flush/commit
 *    error อย่าง key ซ้ำกับข้อมูลใน DB จึงอาจโผล่ตอน commit (หลัง lambda จบ) ไม่ใช่ตอน save()
 *    ถ้าต้องการให้ error ทันทีใช้ saveAndFlush()
 *  - entity ที่อ่านมาใน transaction ถ้าแก้ค่า Hibernate จะ UPDATE ให้เองตอน commit ไม่ต้องเรียก save()
 *  - readOnly = true ทำให้ Hibernate ไม่ต้องตรวจว่า entity เปลี่ยนไหม (เร็วขึ้น)
 */
@Slf4j
@Service
public class CustOrderTxService {

	private final CustOrderRepository custOrderRepository;
	private final PlatformTransactionManager txManager;

	private final TransactionTemplate txTemplate;
	private final TransactionTemplate readOnlyTxTemplate;

	public CustOrderTxService(CustOrderRepository custOrderRepository, PlatformTransactionManager txManager) {
		this.custOrderRepository = custOrderRepository;
		this.txManager = txManager;

		// TransactionTemplate เป็น thread-safe สร้างครั้งเดียวแล้วใช้ซ้ำได้
		this.txTemplate = new TransactionTemplate(txManager);
		this.txTemplate.setTimeout(10); // วินาที

		// เทียบได้กับ @Transactional(readOnly = true)
		this.readOnlyTxTemplate = new TransactionTemplate(txManager);
		this.readOnlyTxTemplate.setReadOnly(true);
	}

	// =====================================================================
	// แบบที่ 1: ใช้ PlatformTransactionManager ตรงๆ
	// =====================================================================

	/**
	 * insert หลายรายการใน transaction เดียว ถ้ารายการไหนพัง (เช่น order_id ซ้ำ) จะ rollback ทั้งหมด
	 * เทียบเท่า @Transactional(rollbackFor = Exception.class)
	 *
	 * @return จำนวนรายการที่ insert
	 */
	public int insertAllManual(List<CustOrder> custOrders) {
		// กำหนดคุณสมบัติของ transaction (เทียบกับ attribute ของ @Transactional)
		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setName("CustOrderTxService.insertAllManual");
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		def.setTimeout(10);

		// เริ่ม transaction (เปิด EntityManager + ยืม Connection จาก pool แล้ว setAutoCommit(false))
		TransactionStatus status = txManager.getTransaction(def);

		int total = 0;
		try {
			for (CustOrder custOrder : custOrders) {
				custOrderRepository.save(custOrder);
				total++;
			}
		} catch (RuntimeException | Error ex) {
			log.warn("rollback: {}", ex.getMessage());
			txManager.rollback(status);
			throw ex;
		}
		// commit อยู่นอก try: ถ้า commit เองพัง (JPA เจอบ่อยเพราะส่ง SQL ตอนนี้) manager จัดการ rollback ให้แล้ว
		// ไม่ต้อง (และห้าม) เรียก rollback ซ้ำ ไม่งั้นจะได้ IllegalTransactionStateException
		txManager.commit(status);
		return total;
	}

	// =====================================================================
	// แบบที่ 2: ใช้ TransactionTemplate (แนะนำ)
	// =====================================================================

	/**
	 * เหมือน insertAllManual แต่สั้นกว่า
	 * lambda จบปกติ -> commit, โยน RuntimeException/Error -> rollback แล้วโยนต่อ
	 */
	public int insertAll(List<CustOrder> custOrders) {
		return txTemplate.execute(status -> custOrderRepository.saveAll(custOrders).size());
	}

	/**
	 * rollback โดยไม่ต้องโยน exception ใช้ status.setRollbackOnly()
	 * ตัวอย่าง: แก้ยอดรวม แต่ถ้ายอดใหม่ต่ำกว่าครึ่งของยอดเดิม ให้ยกเลิก
	 *
	 * @return true = commit, false = rollback (หรือไม่พบ order)
	 */
	public boolean updateTotalAmount(int orderId, BigDecimal newAmount) {
		Boolean committed = txTemplate.execute(status -> {
			CustOrder custOrder = custOrderRepository.findById(orderId).orElse(null);
			if (custOrder == null) {
				return false;
			}

			BigDecimal oldAmount = custOrder.getTotalAmount();
			custOrder.setTotalAmount(newAmount); // ไม่ต้องเรียก save() Hibernate UPDATE ให้ตอน commit (dirty checking)

			if (oldAmount != null && newAmount.compareTo(oldAmount.divide(BigDecimal.TWO)) < 0) {
				log.warn("rollback: ยอดใหม่ {} ต่ำกว่าครึ่งของยอดเดิม {}", newAmount, oldAmount);
				status.setRollbackOnly(); // จบ lambda แล้วจะ rollback แทน commit (ไม่มี UPDATE ไปที่ DB)
				return false;
			}
			return true;
		});
		return Boolean.TRUE.equals(committed); // execute() คืน @Nullable จึงไม่ unbox ตรงๆ
	}

	/** transaction แบบ read-only (ถ้าไม่มีค่าคืน ใช้ executeWithoutResult ได้) */
	public List<CustOrder> findAll() {
		return readOnlyTxTemplate.execute(status -> custOrderRepository.findAllByOrderByOrderIdAsc());
	}
}
