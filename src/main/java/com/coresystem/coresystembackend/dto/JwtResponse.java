package com.coresystem.coresystembackend.dto;
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/jwt-login | replicated from newmojf model/mojf/{request,response}

public class JwtResponse {

	private String token;
	private String type = "Bearer ";
	private Long id;
	private String uname;
	private String mitKode;
	private String urole;

	public JwtResponse(String token, Long id, String uname, String mitKode, String urole) {
		super();
		this.token = token;
		this.id = id;
		this.uname = uname;
		this.mitKode = mitKode;
		this.urole = urole;

	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

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

	public String getUrole() {
		return urole;
	}

	public void setUrole(String urole) {
		this.urole = urole;
	}

	public String getMitKode() {
		return mitKode;
	}

	public void setMitKode(String mitKode) {
		this.mitKode = mitKode;
	}

}
