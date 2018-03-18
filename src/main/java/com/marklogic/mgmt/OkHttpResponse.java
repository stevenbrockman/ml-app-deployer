package com.marklogic.mgmt;

import okhttp3.Response;

import java.io.IOException;
import java.net.URI;

public class OkHttpResponse implements ManageResponse {

	private Response response;

	public OkHttpResponse(Response response) {
		this.response = response;
	}

	@Override
	public Object getResponseObject() {
		return response;
	}

	@Override
	public String getLocationHeader() {
		return response.header("Location");
	}

	@Override
	public String getBody() {
		try {
			return response.body().string();
		} catch (IOException e) {
			throw new RuntimeException("Unable to get response body: " + e.getMessage(), e);
		}
	}

	@Override
	public int getStatusCode() {
		return response.code();
	}
}
