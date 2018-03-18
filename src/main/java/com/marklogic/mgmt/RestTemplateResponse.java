package com.marklogic.mgmt;

import org.springframework.http.ResponseEntity;

import java.net.URI;

public class RestTemplateResponse implements ManageResponse {

	private ResponseEntity<String> responseEntity;

	public RestTemplateResponse(ResponseEntity<String> responseEntity) {
		this.responseEntity = responseEntity;
	}

	@Override
	public Object getResponseObject() {
		return responseEntity;
	}

	@Override
	public String getLocationHeader() {
		return responseEntity.getHeaders().getLocation().getPath();
	}

	@Override
	public String getBody() {
		return responseEntity.getBody();
	}

	@Override
	public int getStatusCode() {
		return responseEntity.getStatusCode().value();
	}
}
