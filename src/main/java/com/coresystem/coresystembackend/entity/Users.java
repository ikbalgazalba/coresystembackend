package com.coresystem.coresystembackend.entity;
// SDD-PROVENANCE: U-002 | vault: .mega-sdd/vaults/jwt-login | replicated from newmojf mojf_users_Model (jakarta.persistence, table=mojf_users — OQ-DM-1 RESOLVED: newmojf existing DB uses mojf_users)

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mojf_users")
public class Users {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "uid_")
	private Long id;
	@Column(name = "uname")
	private String uname;
	@Column(name = "pass")
	private String pass;
	@Column(name = "nama_lengkap")
	private String namaLengkap;
	@Column(name = "kode_unit_kerja")
	private String kodeUnitKerja;
	@Column(name = "cakupan")
	private Long cakupan;
	@Column(name = "urole")
	private Long urole;
	@Column(name = "created_date")
	private Date createdDate;
	@Column(name = "created_by")
	private String createdBy;
	@Column(name = "last_modified")
	private Date lastModified;
	@Column(name = "modified_by")
	private String modifiedBy;
	@Column(name = "last_login")
	private Date lastLogin;
	@Column(name = "kode_mitra")
	private String kodeMitra;
	@Column(name = "active")
	private Long active;
	@Column(name = "expired_days")
	private Long expiredDays;
	@Column(name = "kode_kelas_user")
	private String kodeKelasUser;
	@Column(name = "status_user")
	private Long statusUser;
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getUname() {
		return uname;
	}
	public void setUname(String uname) {
		this.uname = uname;
	}
	public String getPass() {
		return pass;
	}
	public void setPass(String pass) {
		this.pass = pass;
	}
	public String getNamaLengkap() {
		return namaLengkap;
	}
	public void setNamaLengkap(String namaLengkap) {
		this.namaLengkap = namaLengkap;
	}
	public String getKodeUnitKerja() {
		return kodeUnitKerja;
	}
	public void setKodeUnitKerja(String kodeUnitKerja) {
		this.kodeUnitKerja = kodeUnitKerja;
	}
	public Long getCakupan() {
		return cakupan;
	}
	public void setCakupan(Long cakupan) {
		this.cakupan = cakupan;
	}
	public Long getUrole() {
		return urole;
	}
	public void setUrole(Long urole) {
		this.urole = urole;
	}
	public Date getCreatedDate() {
		return createdDate;
	}
	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}
	public String getCreatedBy() {
		return createdBy;
	}
	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}
	public Date getLastModified() {
		return lastModified;
	}
	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}
	public String getModifiedBy() {
		return modifiedBy;
	}
	public void setModifiedBy(String modifiedBy) {
		this.modifiedBy = modifiedBy;
	}
	public Date getLastLogin() {
		return lastLogin;
	}
	public void setLastLogin(Date lastLogin) {
		this.lastLogin = lastLogin;
	}
	public String getKodeMitra() {
		return kodeMitra;
	}
	public void setKodeMitra(String kodeMitra) {
		this.kodeMitra = kodeMitra;
	}
	public Long getActive() {
		return active;
	}
	public void setActive(Long active) {
		this.active = active;
	}
	public Long getExpiredDays() {
		return expiredDays;
	}
	public void setExpiredDays(Long expiredDays) {
		this.expiredDays = expiredDays;
	}
	public String getKodeKelasUser() {
		return kodeKelasUser;
	}
	public void setKodeKelasUser(String kodeKelasUser) {
		this.kodeKelasUser = kodeKelasUser;
	}
	public Long getStatusUser() {
		return statusUser;
	}
	public void setStatusUser(Long statusUser) {
		this.statusUser = statusUser;
	}


}
