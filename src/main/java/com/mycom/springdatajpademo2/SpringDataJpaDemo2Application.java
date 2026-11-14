package com.mycom.springdatajpademo2;

import java.util.TimeZone;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.mycom.springdatajpademo2.config.AppTimeZone;
import com.mycom.springdatajpademo2.model.CustOrder;
import com.mycom.springdatajpademo2.repository.CustOrderRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class SpringDataJpaDemo2Application {

	public static void main(String[] args) {
		// ล็อก timezone ของ JVM เป็นเวลาไทยก่อน Spring start มีผลกับ LocalDate.now(), LocalDateTime.now() และเวลาใน log
		// ไม่งั้นผลจะขึ้นกับเครื่องที่รัน (เช่น server/docker ที่เป็น UTC ช่วง 00:00-07:00 น. LocalDate.now() จะได้วันของเมื่อวาน)
		// ส่วนการแปลง Instant <-> DATETIME ใน DB ใช้ connectionTimeZone ใน datasource url ก่อน ค่านี้เป็นแค่ค่าสำรอง
		// กำหนด default timezone ของ JVM เป็น Asia/Bangkok ก่อน Spring เริ่มทำงาน
		log.info("==setdefaulTimeZone==");
		TimeZone.setDefault(TimeZone.getTimeZone(AppTimeZone.ZONE_ID));
		SpringApplication.run(SpringDataJpaDemo2Application.class, args);
	}

	@Bean
	CommandLineRunner custOrder(CustOrderRepository custOrderRepository) {
		//ตัวอย่างการใช้ CommandLineRunner นี้สามารถนำไปประยุกต์สร้างโปรแกรมแบบ batch/command-line application ที่รันและจบในครั้งเดียว
		return (args) -> {
			log.info("Running CommandLineRunner.....test");
			var datas = custOrderRepository.findByCustomerNameOrderByOrderIdAsc("customer_name" + 2011);
			if (datas.isEmpty()) {
				log.info("Not found data.");
				return;
			}
			for (CustOrder data : datas) {
				log.info("custOrder={}", data.toString());
			}
		};
	}

}
