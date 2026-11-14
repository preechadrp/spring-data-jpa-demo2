== spring boot demo Spring Data JPA เบื้องต้น ===
- clone มาจาก spring-jdbc-client-demo1 แล้วเปลี่ยนการเข้าถึง database จาก JdbcClient เป็น Spring Data JPA (Hibernate)
  ส่วนอื่น (controller, validate, exception handler, config, schedule) เหมือนเดิม

- สิ่งที่เปลี่ยน
  pom.xml          spring-boot-starter-jdbc -> spring-boot-starter-data-jpa
                   spring-boot-starter-jdbc-test -> spring-boot-starter-data-jpa-test
  model/CustOrder  เป็น @Entity (@Table, @Id, @Column) และ implements Persistable
                   เพราะ order_id กำหนดเอง ไม่ใช่ AUTO_INCREMENT (ดูคำอธิบายในไฟล์)
  repository       จาก class ที่เขียน SQL เอง -> interface extends JpaRepository
                   - derived query  : ตั้งชื่อ method เช่น findByCustomerNameOrderByOrderIdAsc
                   - @Query JPQL    : ใช้ชื่อ class/field ของ Java
                   - @Query native  : SQL ของ MariaDB ตรงๆ
                   - @Modifying     : UPDATE/DELETE ต้องมี @Transactional
  service          insertOrdersByBatch ย้ายจาก repository มาที่ CustOrderService (saveAll)
                   CustOrderTxService ใช้ JpaTransactionManager (Spring Boot สร้างให้)
  application-dev  database ใหม่ spring_data_jpa_demo2 (createDatabaseIfNotExist=true)
                   spring.sql.init.mode=always + schema.sql (CREATE TABLE IF NOT EXISTS)
                   spring.jpa.* (ddl-auto, open-in-view, batch, เก็บ Instant เป็นเวลาไทย)
  logback          ดู SQL ด้วย logger org.hibernate.SQL

- เรื่องที่ JPA ต่างจาก JdbcClient
  1. save() ยังไม่ส่ง SQL ทันที Hibernate ส่งตอน flush/commit
     error เช่น key ซ้ำกับข้อมูลใน DB จึงโผล่ตอน commit ถ้าต้องการให้ error ทันทีใช้ saveAndFlush()
  2. entity ที่อ่านมาใน transaction ถ้าแก้ค่า Hibernate UPDATE ให้เองตอน commit (dirty checking)
     นอก transaction ต้องเรียก save() เอง
  3. Hibernate เก็บ Instant เป็น UTC เป็นค่าเริ่มต้น ตั้ง hibernate.type.preferred_instant_jdbc_type=TIMESTAMP
     ให้เก็บเป็นเวลาไทยตาม connectionTimeZone เหมือนเวอร์ชัน JdbcClient
  4. open-in-view=false ต้องโหลดข้อมูลให้ครบใน service (ถ้ามี relation แบบ lazy)
  5. ใช้ JdbcClient คู่กับ JPA ได้ (Spring Boot สร้าง JdbcClient ให้ด้วย) เหมาะกับ query ที่ซับซ้อน

- relax binding  (การ map ชื่อระหว่าง java กับ config/environment varible/database table field)

  camel case = companyTaxId    เริ่มด้วยตัวพิมพ์เล็กและแบ่งคำด้วยตัวใหญ่  (ใช้ตั้งชื่อตัวแปร)
  kebab case = company-tax-id  ขีดกลาง     (map กับ application.properties/yml)
  snake case = company_tax_id  ใช้ขีดล่าง     (map ชื่อตัวแปรไปยังชื่อฟิลด์ใน table ของ database)
  pascal case = CompanyTaxId   เริ่มด้วยตัวพิมพ์ใหญ่และแบ่งคำด้วยตัวใหญ่  (ใช้ตั้งชื่อ class)
  upper snake case = COMPANY_TAX_ID  ใช้สร้างตัวแปรใน environment varible ใน docker-compose.yml

- การอ่านค่าจาก config หรือ application.properties

  แบบใช้ @Value("${property.name}")  // property.name = value
  private String propertyName;

  แบบใช้ @ConfigurationProperties(prefix = "property")  // property.name = value

  ลำดับการอ่านค่า config
    -D > Environment Varible > application.properties/.yml > (./config > ./ > src/main/resources)

- junit
  @SpringBootTest
  CustOrderTest          CRUD ผ่าน repository + ตรวจเวลาที่เก็บใน DB
  CustOrderTxServiceTest transaction ด้วย PlatformTransactionManager / TransactionTemplate

- schedule
  แบบ fixedRate
  แบบ fixedDelay
  แบบ cron

== หลักการ ==
HTTP Request  //Client Request
   ↓
Controller,RestController   //HTTP
   ↓
Service (@Transactional)    //business logic ควรใช้ @Transactional(rollbackFor = Exception.class) เพื่อให้ rollbak ทุกกรณี
   ↓
Repository (Spring Data JPA interface)
   ↓
EntityManager (Hibernate)   //แปลง entity <-> SQL
   ↓
DataSource (HikariCP)
   ↓
Database
