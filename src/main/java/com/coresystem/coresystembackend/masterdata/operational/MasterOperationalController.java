package com.coresystem.coresystembackend.masterdata.operational;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.ApprovalReason;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.BlacklistOverride;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.BranchCreditSource;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.CreditSource;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.GeneralParameter;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.PublicHoliday;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.PromotionLineText;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalService.MissingJustificationException;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalService.NotFoundException;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalService.NotUpdateableException;

@RestController
@RequestMapping("/master-data")
public class MasterOperationalController {
	private final MasterOperationalService service;
	public MasterOperationalController(MasterOperationalService service) { this.service = service; }

	@GetMapping("/approval-reasons") public PageResponse<ApprovalReason> listApprovalReasons(@RequestParam(name = "type", required = false) String type, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) { Pageable pageable = PageRequest.of(page, size); return PageResponse.of(service.listApprovalReasons(type, pageable), page, size); }
	@GetMapping("/approval-reasons/{id}") public ResponseEntity<ApprovalReason> getApprovalReason(@PathVariable Long id) { return service.getApprovalReason(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()); }
	@PostMapping("/approval-reasons") public ResponseEntity<ApprovalReason> createApprovalReason(@RequestBody ApprovalReason reason, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createApprovalReason(reason, actorNik)); }
	@PatchMapping("/approval-reasons/{id}") public ResponseEntity<ApprovalReason> updateApprovalReason(@PathVariable Long id, @RequestBody ApprovalReason update, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.ok(service.updateApprovalReason(id, update, actorNik)); }
	@PatchMapping("/approval-reasons/{id}/deactivate") public ResponseEntity<ApprovalReason> deactivateApprovalReason(@PathVariable Long id, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.ok(service.deactivateApprovalReason(id, actorNik)); }
	@DeleteMapping("/approval-reasons/{id}") public ResponseEntity<Map<String, String>> deleteApprovalReason(@PathVariable Long id) { return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "DELETE_NOT_ALLOWED", "message", "approval-reason is deactivate-only (BR-BE07-03)")); }

	@GetMapping("/credit-sources") public PageResponse<CreditSource> listCreditSources(@RequestParam(name = "branch_id", required = false) String branchId, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) { Pageable pageable = PageRequest.of(page, size); return PageResponse.of(service.listCreditSources(branchId, pageable), page, size); }
	@GetMapping("/credit-sources/{id}") public ResponseEntity<CreditSource> getCreditSource(@PathVariable Long id) { return service.getCreditSource(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()); }
	@PostMapping("/credit-sources") public ResponseEntity<CreditSource> createCreditSource(@RequestBody CreditSource source, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createCreditSource(source, actorNik)); }
	@PatchMapping("/credit-sources/{id}") public ResponseEntity<CreditSource> updateCreditSource(@PathVariable Long id, @RequestBody CreditSource update, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.ok(service.updateCreditSource(id, update, actorNik)); }
	@PatchMapping("/credit-sources/{id}/deactivate") public ResponseEntity<CreditSource> deactivateCreditSource(@PathVariable Long id, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.ok(service.deactivateCreditSource(id, actorNik)); }
	@DeleteMapping("/credit-sources/{id}") public ResponseEntity<Map<String, String>> deleteCreditSource(@PathVariable Long id) { return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "DELETE_NOT_ALLOWED", "message", "credit-source is deactivate-only (BR-BE07-03)")); }
	@GetMapping("/branch-credit-sources") public PageResponse<BranchCreditSource> listBranchCreditSources(@RequestParam(name = "branch_id", required = false) String branchId, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) { Pageable pageable = PageRequest.of(page, size); return PageResponse.of(service.listBranchCreditSources(branchId, pageable), page, size); }
	@PostMapping("/branch-credit-sources") public ResponseEntity<BranchCreditSource> createBranchCreditSource(@RequestBody BranchCreditSource mapping, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createBranchCreditSource(mapping, actorNik)); }
	@PatchMapping("/branch-credit-sources/{id}/deactivate") public ResponseEntity<BranchCreditSource> deactivateBranchCreditSource(@PathVariable Long id, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.ok(service.deactivateBranchCreditSource(id, actorNik)); }
	@DeleteMapping("/branch-credit-sources/{id}") public ResponseEntity<Map<String, String>> deleteBranchCreditSource(@PathVariable Long id) { return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "DELETE_NOT_ALLOWED", "message", "branch-credit-source is deactivate-only (BR-BE07-03)")); }

	@GetMapping("/blacklist-overrides") public PageResponse<BlacklistOverride> listBlacklistOverrides(@RequestParam(name = "national_id", required = false) String nationalId, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) { Pageable pageable = PageRequest.of(page, size); return PageResponse.of(service.listBlacklistOverrides(nationalId, pageable), page, size); }
	@GetMapping("/blacklist-overrides/{id}") public ResponseEntity<BlacklistOverride> getBlacklistOverride(@PathVariable Long id) { return service.getBlacklistOverride(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()); }
	@PostMapping("/blacklist-overrides") public ResponseEntity<?> createBlacklistOverride(@RequestBody BlacklistOverride override, @RequestParam(name = "maker_nik", defaultValue = "SYSTEM") String makerNik) { try { MasterChangeRequest request = service.createBlacklistOverride(override, makerNik); return ResponseEntity.status(HttpStatus.ACCEPTED).body(request); } catch (MissingJustificationException e) { return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "JUSTIFICATION_REQUIRED", "message", e.getMessage())); } }
	@PatchMapping("/blacklist-overrides/{id}") public ResponseEntity<?> updateBlacklistOverride(@PathVariable Long id, @RequestBody BlacklistOverride update, @RequestParam(name = "maker_nik", defaultValue = "SYSTEM") String makerNik) { try { MasterChangeRequest request = service.updateBlacklistOverride(id, update, makerNik); return ResponseEntity.status(HttpStatus.ACCEPTED).body(request); } catch (MissingJustificationException e) { return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "JUSTIFICATION_REQUIRED", "message", e.getMessage())); } }
	@DeleteMapping("/blacklist-overrides/{id}") public ResponseEntity<Map<String, String>> deleteBlacklistOverride(@PathVariable Long id) { return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "DELETE_NOT_ALLOWED", "message", "blacklist-override is deactivate-only (BR-BE07-03)")); }

	@GetMapping("/public-holidays") public PageResponse<PublicHoliday> listPublicHolidays(@RequestParam(name = "year", required = false) Integer year, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) { Pageable pageable = PageRequest.of(page, size); return PageResponse.of(service.listPublicHolidays(year, pageable), page, size); }
	@GetMapping("/public-holidays/{id}") public ResponseEntity<PublicHoliday> getPublicHoliday(@PathVariable Long id) { return service.getPublicHoliday(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()); }
	@PostMapping("/public-holidays") public ResponseEntity<PublicHoliday> createPublicHoliday(@RequestBody PublicHoliday holiday, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createPublicHoliday(holiday, actorNik)); }
	@PatchMapping("/public-holidays/{id}") public ResponseEntity<PublicHoliday> updatePublicHoliday(@PathVariable Long id, @RequestBody PublicHoliday update, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.ok(service.updatePublicHoliday(id, update, actorNik)); }
	@PatchMapping("/public-holidays/{id}/deactivate") public ResponseEntity<PublicHoliday> deactivatePublicHoliday(@PathVariable Long id, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.ok(service.deactivatePublicHoliday(id, actorNik)); }
	@DeleteMapping("/public-holidays/{id}") public ResponseEntity<Map<String, String>> deletePublicHoliday(@PathVariable Long id) { return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "DELETE_NOT_ALLOWED", "message", "public-holiday is deactivate-only (BR-BE07-03, OQ-BE07-06 resolved)")); }

	@GetMapping("/general-parameters") public PageResponse<GeneralParameter> listGeneralParameters(@RequestParam(name = "include_hidden", defaultValue = "false") boolean includeHidden, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) { Pageable pageable = PageRequest.of(page, size); return PageResponse.of(service.listGeneralParameters(includeHidden, pageable), page, size); }
	@GetMapping("/general-parameters/{parameter}") public ResponseEntity<GeneralParameter> getGeneralParameter(@PathVariable String parameter) { return service.getGeneralParameter(parameter).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()); }
	@PatchMapping("/general-parameters/{parameter}") public ResponseEntity<?> updateGeneralParameter(@PathVariable String parameter, @RequestBody GeneralParameterUpdateRequest body, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { try { GeneralParameter param = service.getGeneralParameter(parameter).orElseThrow(() -> new NotFoundException("general-parameter", 0L)); GeneralParameter updated = service.updateGeneralParameter(param.getId(), body.value(), actorNik); return ResponseEntity.ok(updated); } catch (NotUpdateableException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "NOT_UPDATEABLE", "message", e.getMessage(), "parameter", e.getParameterName())); } catch (NotFoundException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND", "message", e.getMessage())); } }
	@PostMapping("/general-parameters") public ResponseEntity<Map<String, String>> createGeneralParameter(@RequestBody GeneralParameter param) { return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "CREATE_NOT_ALLOWED", "message", "general-parameter: no create via API (E34, BR-BE07-23)")); }
	@DeleteMapping("/general-parameters/{parameter}") public ResponseEntity<Map<String, String>> deleteGeneralParameter(@PathVariable String parameter) { return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "DELETE_NOT_ALLOWED", "message", "general-parameter: no delete via API (E34, BR-BE07-23)")); }

	@GetMapping("/promotion-line-texts") public PageResponse<PromotionLineText> listPromotionLineTexts(@RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) { Pageable pageable = PageRequest.of(page, size); return PageResponse.of(service.listPromotionLineTexts(pageable), page, size); }
	@GetMapping("/promotion-line-texts/{id}") public ResponseEntity<PromotionLineText> getPromotionLineText(@PathVariable Long id) { return service.getPromotionLineText(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()); }
	@PostMapping("/promotion-line-texts") public ResponseEntity<PromotionLineText> createPromotionLineText(@RequestBody PromotionLineText text, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createPromotionLineText(text, actorNik)); }
	@PatchMapping("/promotion-line-texts/{id}") public ResponseEntity<PromotionLineText> updatePromotionLineText(@PathVariable Long id, @RequestBody PromotionLineText update, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.ok(service.updatePromotionLineText(id, update, actorNik)); }
	@PatchMapping("/promotion-line-texts/{id}/deactivate") public ResponseEntity<PromotionLineText> deactivatePromotionLineText(@PathVariable Long id, @RequestParam(name = "actor_nik", defaultValue = "SYSTEM") String actorNik) { return ResponseEntity.ok(service.deactivatePromotionLineText(id, actorNik)); }
	@DeleteMapping("/promotion-line-texts/{id}") public ResponseEntity<Map<String, String>> deletePromotionLineText(@PathVariable Long id) { return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "DELETE_NOT_ALLOWED", "message", "promotion-line-text is deactivate-only (BR-BE07-03)")); }

	public record GeneralParameterUpdateRequest(String value) {}
}
// SDD-PROVENANCE: U-010 | vault: .mega-sdd/vaults/acquisition-master-data | MasterOperationalController E29-E35
