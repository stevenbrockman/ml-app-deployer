package com.marklogic.mgmt;

import java.net.URI;

public interface ManageResponse {

	Object getResponseObject();

	String getLocationHeader();

	String getBody();

	int getStatusCode();
}
