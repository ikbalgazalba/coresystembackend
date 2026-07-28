package com.coresystem.coresystembackend.masterdata.operational;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "map_transaction_type_gl", uniqueConstraints = @UniqueConstraint(
		name = "ux_map_transaction_type_gl_trx_id_class_id",
		columnNames = { "trx_id", "class_id" }))
public class GlTransactionTypeLink extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "trx_id", nullable = false)
	private String trxId;

	@Column(name = "class_id", nullable = false)
	private String classId;

	@Column(name = "gl_account_no")
	private String glAccountNo;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getTrxId() { return trxId; }
	public void setTrxId(String trxId) { this.trxId = trxId; }
	public String getClassId() { return classId; }
	public void setClassId(String classId) { this.classId = classId; }
	public String getGlAccountNo() { return glAccountNo; }
	public void setGlAccountNo(String glAccountNo) { this.glAccountNo = glAccountNo; }
	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }
}
// SDD-PROVENANCE: U-012 | vault: .mega-sdd/vaults/acquisition-master-data | GlTransactionTypeLink @Entity map_transaction_type_gl extends VersionedEntity; [LOCKED] CoA zero-diff C-16; unique ux_map_transaction_type_gl_trx_id_class_id (trx_id, class_id); no delete BR-BE07-24; maker-checker PATCH BR-BE07-05
