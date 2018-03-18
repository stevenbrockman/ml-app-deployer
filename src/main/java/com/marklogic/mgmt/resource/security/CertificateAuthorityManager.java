package com.marklogic.mgmt.resource.security;

import com.marklogic.mgmt.AbstractManager;
import com.marklogic.mgmt.ManageClient;
import com.marklogic.mgmt.ManageResponse;
import com.marklogic.rest.util.ResourcesFragment;
import okhttp3.Request;
import okhttp3.RequestBody;

public class CertificateAuthorityManager extends AbstractManager {

    private ManageClient manageClient;

    public CertificateAuthorityManager(ManageClient client) {
        this.manageClient = client;
    }

	@Override
	protected boolean useSecurityUser() {
		return true;
	}

    public ManageResponse create(String payload) {
    	Request request = new Request.Builder()
		    .url(manageClient.buildHttpUrl("/manage/v2/certificate-authorities"))
		    .post(RequestBody.create(okhttp3.MediaType.parse("text/plain"), payload))
		    .build();
    	return manageClient.executeRequest(request);
    }

    public ResourcesFragment getAsXml() {
        return new ResourcesFragment(manageClient.getXml("/manage/v2/certificate-authorities"));
    }

    public void delete(String resourceIdOrName) {
        manageClient.delete("/manage/v2/certificate-authorities/" + resourceIdOrName);
    }
}
