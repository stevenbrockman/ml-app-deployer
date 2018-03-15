package com.marklogic.mgmt;

public class SaveReceipt {

    private String resourceId;
    private String payload;
    private ManageResponse response;
    private String path;

    public SaveReceipt(String resourceId, String payload, String path, ManageResponse response) {
        super();
        this.resourceId = resourceId;
        this.payload = payload;
        this.path = path;
        this.response = response;
    }

    public boolean hasLocationHeader() {
        return response != null && response.getLocationHeader() != null;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getPath() {
        return path;
    }

    public ManageResponse getResponse() {
        return response;
    }

    public String getPayload() {
        return payload;
    }
}
