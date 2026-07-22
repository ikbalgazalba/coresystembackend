package com.coresystem.coresystembackend.dto;
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/jwt-login | replicated from newmojf model/mojf/{request,response}

public class LoginRequest {

	private String uname;
	private String pass;

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

}
