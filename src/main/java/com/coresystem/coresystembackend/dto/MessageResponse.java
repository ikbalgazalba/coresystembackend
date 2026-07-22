package com.coresystem.coresystembackend.dto;
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/jwt-login | replicated from newmojf model/mojf/{request,response}

public class MessageResponse {

	private String message;

	public MessageResponse(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
