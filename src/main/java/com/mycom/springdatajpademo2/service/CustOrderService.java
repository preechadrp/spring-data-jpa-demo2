package com.mycom.springdatajpademo2.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.springdatajpademo2.config.AppTimeZone;
import com.mycom.springdatajpademo2.model.CustOrder;
import com.mycom.springdatajpademo2.repository.CustOrderRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CustOrderService {

	private final CustOrderRepository custOrderRepository;

	public CustOrderService(CustOrderRepository custOrderRepository) {
		this.custOrderRepository = custOrderRepository;
	}

	@Transactional(rollbackFor = Exception.class) //เพื่อให้ db commit
	public CustOrder insert(CustOrder custOrder) {
		return this.custOrderRepository.save(custOrder);

		//      กรณีต้องการให้ rollbak แบบ manaul ไม่ต้องสร้าง exception
		//        if (somethingWrong) {
		//
		//            TransactionAspectSupport
		//                .currentTransactionStatus()
		//                .setRollbackOnly();
		//
		//            return;
		//        }
	}

	/**
	 * ตัวอย่าง insert หลายรายการ (ย้ายมาจาก repository ของเวอร์ชัน JdbcClient)
	 * saveAll ใน transaction เดียวกัน: ทั้งหมดสำเร็จ หรือ rollback ทั้งหมด
	 *
	 * ต่างจาก JdbcClient: Hibernate ส่ง INSERT เป็น batch ได้จริง
	 * (ตั้ง hibernate.jdbc.batch_size ใน application-dev.yml และ id ต้องไม่ใช่ AUTO_INCREMENT)
	 */
	@Transactional(rollbackFor = Exception.class)
	public int insertOrdersByBatch(int start, int size) {
		Instant now = Instant.now();
		LocalDate today = LocalDate.now(AppTimeZone.ZONE_ID);
		List<CustOrder> custOrders = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			int idx = start + i;
			custOrders.add(new CustOrder()
					.setOrderId(idx)
					.setCustomerName("customer_name" + idx)
					.setTotalAmount(new BigDecimal(idx + "00"))
					.setOrderDate(today)
					.setInsertDatetime(now));
		}
		return custOrderRepository.saveAll(custOrders).size();
	}
}
