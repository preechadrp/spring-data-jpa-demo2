package com.mycom.springdatajpademo2.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mycom.springdatajpademo2.dto.CustOrderSummary;
import com.mycom.springdatajpademo2.dto.CustomerNameRequest;
import com.mycom.springdatajpademo2.exception.CustomException;
import com.mycom.springdatajpademo2.exception.ErrorCode;
import com.mycom.springdatajpademo2.model.CustOrder;
import com.mycom.springdatajpademo2.repository.CustOrderRepository;
import com.mycom.springdatajpademo2.service.CustOrderService;

@RestController
public class CustOrderController {

	private final CustOrderRepository custOrderRepository;
	private final CustOrderService custOrderService;

	public CustOrderController(CustOrderRepository custOrderRepository, CustOrderService custOrderService) {
		this.custOrderRepository = custOrderRepository;
		this.custOrderService = custOrderService;
	}

	@GetMapping("/custorder")
	public List<CustOrder> getAllCustOrders() {
		List<CustOrder> custOrders = custOrderRepository.findAllByOrderByOrderIdAsc();
		return custOrders;
	}

	@GetMapping("/custorder/id/{orderId}")
	public CustOrder getCustOrderByOrderId(@PathVariable Integer orderId) {
		// findById คืน Optional: ถ้าไม่พบให้ตอบ 404 ผ่าน GlobalExceptionHandler
		return custOrderRepository.findById(orderId)
				.orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND, "ไม่พบ order id " + orderId));
	}

	@GetMapping("/custorder/{customerName}")
	public List<CustOrder> getCustOrderByCustomerName(@PathVariable String customerName) {
		return custOrderRepository.findByCustomerNameOrderByOrderIdAsc(customerName);
	}

	@PostMapping("/custorder-find-by-customer-name")
	public List<CustOrder> getCustOrderByCustomerNameByPost(@RequestBody CustomerNameRequest customerNameDto) {
		return custOrderRepository.findByCustomerNameOrderByOrderIdAsc(customerNameDto.customerName());
	}

	/** ยอดรวมแยกตามลูกค้า (ตัวอย่าง JPQL constructor expression) */
	@GetMapping("/custorder-summary")
	public List<CustOrderSummary> getCustOrderSummary() {
		return custOrderRepository.summarizeByCustomer();
	}

	@PostMapping("/custorder")
	public ResponseEntity<CustOrder> createCustOrder(@RequestBody CustOrder custOrder) {
		// object จาก JSON ถือเป็นข้อมูลใหม่ (CustOrder.isNew() = true) -> INSERT, order_id ซ้ำ = error
		CustOrder saved = custOrderService.insert(custOrder);
		//return ResponseEntity.ok(saved);
		return ResponseEntity.status(HttpStatus.CREATED).body(saved);// 201 Created status code
	}
}
