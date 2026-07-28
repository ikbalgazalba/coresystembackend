package com.coresystem.coresystembackend.masterdata.operational;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;

@RestController
@RequestMapping("/gl-transaction-type-links")
public class GlLinkController {

	private final GlLinkService glLinkService;

	public GlLinkController(GlLinkService glLinkService) {
		this.glLinkService = glLinkService;
	}

	@GetMapping
	public PageResponse<GlTransactionTypeLink> listGlLinks(
			@RequestParam(name = "trx_id", required = false) String trxId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		return glLinkService.listGlLinks(trxId, PageRequest.of(page, size), page, size);
	}

	@GetMapping("/{id}")
	public ResponseEntity<GlTransactionTypeLink> getGlLink(@PathVariable Long id) {
		return glLinkService.getGlLink(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PatchMapping("/{id}")
	public ResponseEntity<MasterChangeRequest> updateGlLink(
			@PathVariable Long id,
			@RequestBody GlTransactionTypeLink update,
			@RequestParam(name = "maker_nik", defaultValue = "SYSTEM") String makerNik) {
		MasterChangeRequest request = glLinkService.updateGlLink(id, update, makerNik);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, String>> deleteGlLink(@PathVariable Long id) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.body(Map.of("error", "DELETE_NOT_ALLOWED",
						"message", "gl-transaction-type-link: no delete (BR-BE07-24)"));
	}

	@ExceptionHandler(GlLinkService.GlLinkNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(GlLinkService.GlLinkNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
	}

	@Service
	public static class GlLinkService {

		private final GlTransactionTypeLinkRepository glLinkRepository;
		private final MakerCheckerService makerCheckerService;

		public GlLinkService(
				GlTransactionTypeLinkRepository glLinkRepository,
				MakerCheckerService makerCheckerService) {
			this.glLinkRepository = glLinkRepository;
			this.makerCheckerService = makerCheckerService;
		}

		@Transactional(readOnly = true)
		public PageResponse<GlTransactionTypeLink> listGlLinks(
				String trxId, Pageable pageable, int requestedPage, int requestedSize) {
			Page<GlTransactionTypeLink> result;
			if (trxId != null && !trxId.isBlank()) {
				result = glLinkRepository.findByTrxIdAndIsActiveTrue(trxId, pageable);
			} else {
				result = glLinkRepository.findByIsActiveTrue(pageable);
			}
			return PageResponse.of(result, requestedPage, requestedSize);
		}

		@Transactional(readOnly = true)
		public Optional<GlTransactionTypeLink> getGlLink(Long id) {
			return glLinkRepository.findById(id);
		}

		@Transactional
		public MasterChangeRequest updateGlLink(
				Long id, GlTransactionTypeLink update, String makerNik) {
			GlTransactionTypeLink existing = glLinkRepository.findById(id)
					.orElseThrow(() -> new GlLinkNotFoundException(id));
			String payload = buildUpdatePayload(existing, update);
			return makerCheckerService.submit(
					"GL_TRANSACTION_TYPE_LINK", Action.update, payload, makerNik);
		}

		private String buildUpdatePayload(GlTransactionTypeLink existing,
				GlTransactionTypeLink update) {
			String newGlAccountNo = update.getGlAccountNo() != null
					? update.getGlAccountNo() : existing.getGlAccountNo();
			return "{\"id\":" + existing.getId() + ","
					+ "\"trx_id\":\"" + escape(existing.getTrxId()) + "\","
					+ "\"class_id\":\"" + escape(existing.getClassId()) + "\","
					+ "\"gl_account_no\":\"" + escape(newGlAccountNo) + "\"}";
		}

		private static String escape(String value) {
			return value != null ? value.replace("\\\\", "\\\\\\\\").replace("\"", "\\\\\"") : "";
		}

		public static class GlLinkNotFoundException extends RuntimeException {
			public GlLinkNotFoundException(Long id) {
				super("GL transaction-type link " + id + " not found");
			}
		}
	}
}

interface GlTransactionTypeLinkRepository extends JpaRepository<GlTransactionTypeLink, Long> {
	Page<GlTransactionTypeLink> findByIsActiveTrue(Pageable pageable);
	Page<GlTransactionTypeLink> findByTrxIdAndIsActiveTrue(String trxId, Pageable pageable);
}
// SDD-PROVENANCE: U-012 | vault: .mega-sdd/vaults/acquisition-master-data | GlLinkController /gl-transaction-type-links E36 GET+PATCH maker-checker BR-BE07-05 NO DELETE 405 BR-BE07-24; nested GlLinkService; bundled GlTransactionTypeLinkRepository
