package com.coresystem.coresystembackend.masterdata.operational;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.operational.NumberFormat.ResetPeriod;
import com.coresystem.coresystembackend.masterdata.operational.NumberFormatService.NumberFormatNotFoundException;

@RestController
@RequestMapping("/number-formats")
public class NumberFormatController {

	private final NumberFormatService numberFormatService;

	public NumberFormatController(NumberFormatService numberFormatService) {
		this.numberFormatService = numberFormatService;
	}

	@GetMapping
	public PageResponse<NumberFormat> listNumberFormats(
			@RequestParam(name = "code_type", required = false) String codeType,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		return PageResponse.of(
				numberFormatService.listNumberFormats(codeType, pageable), page, size);
	}

	@GetMapping("/{id}")
	public ResponseEntity<NumberFormat> getNumberFormat(@PathVariable Long id) {
		return numberFormatService.getNumberFormat(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<MasterChangeRequest> createNumberFormat(
			@RequestBody NumberFormatCreateRequest request,
			@RequestParam(name = "maker_nik", defaultValue = "SYSTEM") String makerNik) {
		MasterChangeRequest result = numberFormatService.createNumberFormat(
				request.codeType(), request.companyId(), request.branchId(),
				request.formatTemplate(), request.resetPeriod(), request.sequenceName(),
				request.effectiveFrom(), request.effectiveTo(), makerNik);
		if (result.getStatus() == MasterChangeRequest.Status.applied) {
			return ResponseEntity.status(HttpStatus.CREATED).body(result);
		}
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
	}

	@PatchMapping("/{id}")
	public ResponseEntity<MasterChangeRequest> updateNumberFormat(
			@PathVariable Long id,
			@RequestBody NumberFormatCreateRequest request,
			@RequestParam(name = "maker_nik", defaultValue = "SYSTEM") String makerNik) {
		MasterChangeRequest result = numberFormatService.updateNumberFormat(
				id, request.codeType(), request.companyId(), request.branchId(),
				request.formatTemplate(), request.resetPeriod(), request.sequenceName(),
				request.effectiveFrom(), request.effectiveTo(), makerNik);
		if (result.getStatus() == MasterChangeRequest.Status.applied) {
			return ResponseEntity.ok(result);
		}
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
	}

	@ExceptionHandler(NumberFormatNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(NumberFormatNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
	}

	public record NumberFormatCreateRequest(
			String codeType,
			String companyId,
			String branchId,
			String formatTemplate,
			ResetPeriod resetPeriod,
			String sequenceName,
			LocalDate effectiveFrom,
			LocalDate effectiveTo) {
	}
}
// SDD-PROVENANCE: U-012 | vault: .mega-sdd/vaults/acquisition-master-data | NumberFormatController /number-formats E38 GET/POST/PATCH; CREDIT_ID maker-checker BR-BE07-05; NumberFormatCreateRequest DTO
