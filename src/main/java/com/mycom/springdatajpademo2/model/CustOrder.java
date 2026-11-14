package com.mycom.springdatajpademo2.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.domain.Persistable;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mycom.springdatajpademo2.config.AppTimeZone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Entity ของตาราง cust_order
 *
 * ต่างจากเวอร์ชัน JdbcClient (model ธรรมดา) ตรงที่ต้องมี
 *  - @Entity / @Table / @Id ให้ Hibernate รู้ว่า map กับตารางไหน คอลัมน์ไหนเป็น primary key
 *  - @Column(name = "...") ถ้าไม่ใส่ Spring Boot แปลงชื่อ camelCase -> snake_case ให้เอง
 *    (orderId -> order_id) ในตัวอย่างนี้ใส่ไว้ให้เห็นชัด
 *  - constructor ว่าง (@NoArgsConstructor) Hibernate ใช้สร้าง object ตอนอ่านจาก DB
 *
 * ทำไม implements Persistable:
 *   order_id เรากำหนดเอง (ไม่ใช่ AUTO_INCREMENT) Spring Data จึงแยกไม่ออกว่าเป็นข้อมูลใหม่หรือของเดิม
 *   ถ้าไม่บอก repository.save() จะใช้ merge (SELECT ก่อน แล้ว INSERT หรือ UPDATE)
 *   ผลคือ insert order_id ซ้ำจะกลายเป็น UPDATE ทับของเดิมเงียบๆ แทนที่จะ error
 *   isNew() จึงบอกว่า object ที่สร้างด้วย new = ใหม่ (persist -> INSERT, ซ้ำ = error)
 *   ส่วน object ที่อ่านจาก DB (@PostLoad) หรือบันทึกแล้ว (@PostPersist) = ของเดิม (merge -> UPDATE)
 */
@Entity
@Table(name = "cust_order")
@ToString
@Accessors(chain = true)
@NoArgsConstructor
@Setter
@Getter
public class CustOrder implements Persistable<Integer> {

	@Id
	@Column(name = "order_id")
	private Integer orderId;

	@Column(name = "customer_name", nullable = false, length = 250)
	private String customerName;

	@Column(name = "total_amount", precision = 18, scale = 2)
	private BigDecimal totalAmount;

	@JsonFormat(pattern = "yyyy-MM-dd") // กำหนดรูปแบบวันที่ในการแสดงผล
	@Column(name = "order_date")
	private LocalDate orderDate;

	/*
	 * Instant = จุดเวลาที่แน่นอนบนเส้นเวลา (ไม่ผูกกับ timezone ใด) ต่างจาก LocalDateTime ที่เป็นแค่
	 * "วันที่+เวลาบนนาฬิกา" โดยไม่รู้ว่าเป็นเวลาของประเทศไหน
	 * JSON ต้องมี offset เสมอ เช่น 2026-09-05T19:41:30.085+07:00 (ส่งเข้าหรือแสดงผล)
	 * timezone = "Asia/Bangkok" ทำให้แสดงผลเป็นเวลาไทย
	 * การเก็บลง DATETIME เป็นเวลาไทย ดู hibernate.type.preferred_instant_jdbc_type ใน application-dev.yml
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = AppTimeZone.ZONE_ID_STR)
	@Column(name = "insert_datetime")
	private Instant insertDatetime;

	// ---- Persistable ----

	@Transient // ไม่ใช่คอลัมน์ใน DB
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	@ToString.Exclude
	private boolean newEntity = true;

	@Override
	@JsonIgnore // ไม่ให้โผล่ใน JSON ซ้ำกับ orderId
	public Integer getId() {
		return orderId;
	}

	@Override
	@JsonIgnore
	public boolean isNew() {
		return newEntity;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.newEntity = false;
	}
}
