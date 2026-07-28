package com.coresystem.coresystembackend.masterdata.menu;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Menu tree entity (BE-07 §3.4, table {@code cfg_menu}).
 *
 * <p>Maps the acquisition-module menu tree — the navigation structure that drives the E6
 * menu-efektif endpoint. Each row represents a menu node; the tree is formed via the
 * self-referential {@code parent_id} FK (nullable for root nodes).
 *
 * <h2>Key design rules</h2>
 * <ul>
 *   <li><strong>{@code transTypeIdPrefix}</strong> — {@code @Column(name = "trans_type_id_prefix")}
 *       [LOCKED] verbatim per BR-PRODASSET-14. This is an external-FK that must match char-for-char;
 *       mutations to this field require maker-checker approval (BR-BE07-14, constitution §I-007).
 *       The prefix is the link between a menu node and the transaction-type routing system; changing
 *       it impacts all transaction-types that reference this prefix.</li>
 *   <li><strong>No DELETE</strong> (BR-BE07-03 [LOCKED] — deactivate-only). The service layer never
 *       calls {@code delete}; lifecycle is managed via the {@code isActive} toggle.</li>
 *   <li><strong>Tree structure</strong> — {@code parentId} is a self-FK to {@code cfg_menu.id},
 *       nullable for root nodes. The tree is assembled in-memory by the service layer from the flat
 *       list (not via a JPA recursive fetch).</li>
 * </ul>
 *
 * <p>Extends {@link VersionedEntity} — audit columns + {@code @Version} optimistic lock
 * (DB-CONVENTIONS §4 — {@code cfg_} tables participate in concurrent edits).
 *
 * <p>Per constitution §C-002, controllers return DTOs, never the raw entity.
 */
@Entity
@Table(name = "cfg_menu")
public class Menu extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/**
	 * Self-FK to {@code cfg_menu.id} — the parent menu node. {@code null} for root nodes.
	 * The tree is assembled in-memory from the flat list, not via JPA recursive fetching.
	 */
	@Column(name = "parent_id")
	private Long parentId;

	/** Module grouping (e.g. {@code "ACQ"}, {@code "MASTER"}). Used for organizational display. */
	@Column(name = "module", length = 32)
	private String module;

	/** Display name of the menu node (human-readable label). */
	@Column(name = "name", nullable = false, length = 128)
	private String name;

	/** Frontend route path for the menu node (e.g. {@code "/acq/application"}). */
	@Column(name = "route", length = 256)
	private String route;

	/**
	 * [LOCKED] external-FK — must match char-for-char (BR-PRODASSET-14). This prefix links the
	 * menu node to the transaction-type routing system. Mutations require maker-checker approval
	 * (BR-BE07-14, constitution §I-007). The {@code @Column} name {@code trans_type_id_prefix}
	 * must not change to satisfy the zero-diff migration checksum constraint.
	 *
	 * <p>NOT exposed in the E6 menu-efektif response (E6 is a consumer-facing endpoint; the prefix
	 * is an internal admin field exposed only via E9 admin CRUD).
	 */
	@Column(name = "trans_type_id_prefix", length = 64)
	private String transTypeIdPrefix;

	/** Display order within the same parent (ascending). Controls menu sort order. */
	@Column(name = "display_order")
	private int displayOrder;

	/** Whether the menu node is active. Lifecycle toggle (BR-BE07-03 deactivate-only). */
	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	// --- getters/setters ---

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getParentId() {
		return parentId;
	}

	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}

	public String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = module;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRoute() {
		return route;
	}

	public void setRoute(String route) {
		this.route = route;
	}

	public String getTransTypeIdPrefix() {
		return transTypeIdPrefix;
	}

	public void setTransTypeIdPrefix(String transTypeIdPrefix) {
		this.transTypeIdPrefix = transTypeIdPrefix;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public void setDisplayOrder(int displayOrder) {
		this.displayOrder = displayOrder;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

}
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/acquisition-master-data | Menu @Entity cfg_menu extends VersionedEntity; id, parentId (self-FK nullable tree), module, name, route, transTypeIdPrefix (@Column(name="trans_type_id_prefix") [LOCKED] verbatim BR-PRODASSET-14), displayOrder (int), isActive; No DELETE (BR-BE07-03 LOCKED deactivate-only)
