package com.marklogic.mgmt;

import java.net.URI;

public interface ManageResponse {

	Object getResponseObject();

	URI getLocationHeader();

	String getBody();

	int getStatusCode();
}
