package com.marklogic.mgmt;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.DigestAuthenticator;
import com.fasterxml.jackson.databind.JsonNode;
import com.marklogic.client.ext.helper.LoggingObject;
import com.marklogic.mgmt.util.ObjectMapperFactory;
import com.marklogic.rest.util.Fragment;
import com.marklogic.rest.util.RestConfig;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.jdom2.Namespace;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps a RestTemplate with methods that should simplify accessing the Manage API with RestTemplate. Each NounManager
 * should depend on an instance of ManageClient for accessing the Manage API.
 */
public class ManageClient extends LoggingObject {

	private final static String JSON_MEDIA_TYPE = "application/json";
	private final static String XML_MEDIA_TYPE = "application/xml";

	private ManageConfig manageConfig;
	private PayloadParser payloadParser;

	private OkHttpClient okHttpClient;
	private OkHttpClient securityUserOkHttpClient;

	/**
     * Can use this constructor when the default values in ManageConfig will work.
     */
    public ManageClient() {
        this(new ManageConfig());
    }

    public ManageClient(ManageConfig config) {
        setManageConfig(config);
    }

	/**
	 * Use setManageConfig instead.
	 *
	 * @param config
	 */
	@Deprecated
	public void initialize(ManageConfig config) {
    	setManageConfig(config);
	}

	/**
	 * Uses the given ManageConfig instance to construct a Spring RestTemplate for communicating with the Manage API.
	 * In addition, if adminUsername on the ManageConfig instance differs from username, then a separate RestTemplate is
	 * constructed for making calls to the Manage API that need user with the manage-admin and security roles, which is
	 * often an admin user.
	 *
	 * @param config
	 */
	public void setManageConfig(ManageConfig config) {
	    this.manageConfig = config;
	    if (logger.isInfoEnabled()) {
		    logger.info("Initializing ManageClient with manage config of: " + config);
	    }
	    //this.restTemplate = RestTemplateUtil.newRestTemplate(config);

		final DigestAuthenticator authenticator = new DigestAuthenticator(new com.burgstaller.okhttp.digest.Credentials(
			manageConfig.getUsername(), manageConfig.getPassword()
		));
		final Map<String, CachingAuthenticator> authCache = new ConcurrentHashMap<>();

		// TODO Add SSL support
		okHttpClient = new OkHttpClient.Builder()
			.authenticator(new CachingAuthenticatorDecorator(authenticator, authCache))
			.addInterceptor(new AuthenticationCacheInterceptor(authCache))
			.build();

		String securityUsername = config.getSecurityUsername();
	    if (securityUsername != null && securityUsername.trim().length() > 0 && !securityUsername.equals(config.getUsername())) {
		    if (logger.isInfoEnabled()) {
			    logger.info(format("Initializing separate connection to Manage API with user '%s' that must have both manage-admin and security roles", securityUsername));
		    }

		    RestConfig rc = new RestConfig(config.getHost(), config.getPort(), securityUsername, config.getSecurityPassword());
		    rc.setScheme(config.getScheme());
		    rc.setConfigureSimpleSsl(config.isConfigureSimpleSsl());
		    rc.setHostnameVerifier(config.getHostnameVerifier());
		    rc.setSslContext(config.getSslContext());

		    final DigestAuthenticator securityAuthenticator = new DigestAuthenticator(new com.burgstaller.okhttp.digest.Credentials(
			    manageConfig.getUsername(), manageConfig.getPassword()
		    ));
		    final Map<String, CachingAuthenticator> securityAuthCache = new ConcurrentHashMap<>();
		    securityUserOkHttpClient = new OkHttpClient.Builder()
			    .authenticator(new CachingAuthenticatorDecorator(securityAuthenticator, securityAuthCache))
			    .addInterceptor(new AuthenticationCacheInterceptor(securityAuthCache))
			    .build();
	    } else {
		    this.securityUserOkHttpClient = this.okHttpClient;
	    }
    }

	/**
	 * Use this when you want to provide your own OkHttpClient as opposed to using the one that's constructed via a
	 * ManageConfig instance.
	 *
	 * @param okHttpClient
	 */
	public ManageClient(OkHttpClient okHttpClient) {
    	this(okHttpClient, okHttpClient);
    }

	/**
	 * Use this when you want to provide your own OkHttpClient as opposed to using the one that's constructed via a
	 * ManageConfig instance.
	 *
	 * @param okHttpClient
	 * @param securityUserOkHttpClient
	 */
	public ManageClient(OkHttpClient okHttpClient, OkHttpClient securityUserOkHttpClient) {
    	this.okHttpClient = okHttpClient;
    	this.securityUserOkHttpClient = securityUserOkHttpClient;
    }

    public ManageResponse putJson(String path, String json) {
        logRequest(path, "JSON", "PUT");
        return executeRequest(request(path).put(jsonBody(json)).build());
    }

	public ManageResponse putJsonAsSecurityUser(String path, String json) {
		logSecurityUserRequest(path, "JSON", "PUT");
		return executeRequest(request(path).put(jsonBody(json)).build());
	}

    public ManageResponse putXml(String path, String xml) {
        logRequest(path, "XML", "PUT");
	    return executeRequest(request(path).put(xmlBody(xml)).build());
    }

	public ManageResponse putXmlAsSecurityUser(String path, String xml) {
		logSecurityUserRequest(path, "XML", "PUT");
		return executeSecurityUserRequest(request(path).put(xmlBody(xml)).build());
	}

    public ManageResponse postJson(String path, String json) {
        logRequest(path, "JSON", "POST");
        return executeRequest(request(path).post(jsonBody(json)).build());
    }

	public ManageResponse postJsonAsSecurityUser(String path, String json) {
		logSecurityUserRequest(path, "JSON", "POST");
		return executeSecurityUserRequest(request(path).post(jsonBody(json)).build());
	}

    public ManageResponse postXml(String path, String xml) {
        logRequest(path, "XML", "POST");
	    return executeRequest(request(path).post(xmlBody(xml)).build());
    }

	public ManageResponse postXmlAsSecurityUser(String path, String xml) {
		logSecurityUserRequest(path, "XML", "POST");
		return executeSecurityUserRequest(request(path).post(xmlBody(xml)).build());
	}

    public String getXmlString(String path) {
        logRequest(path, "XML", "GET");
        return executeRequest(request(path).get().build()).getBody();
    }

    public Fragment getXml(String path, String... namespacePrefixesAndUris) {
        String xml = getXmlString(path);
        List<Namespace> list = new ArrayList<Namespace>();
        for (int i = 0; i < namespacePrefixesAndUris.length; i += 2) {
            list.add(Namespace.getNamespace(namespacePrefixesAndUris[i], namespacePrefixesAndUris[i + 1]));
        }
        return new Fragment(xml, list.toArray(new Namespace[] {}));
    }

	public String getXmlStringAsSecurityUser(String path) {
		logSecurityUserRequest(path, "XML", "GET");
		return executeSecurityUserRequest(request(path).build()).getBody();
	}

	public Fragment getXmlAsSecurityUser(String path, String... namespacePrefixesAndUris) {
		String xml = getXmlStringAsSecurityUser(path);
		List<Namespace> list = new ArrayList<Namespace>();
		for (int i = 0; i < namespacePrefixesAndUris.length; i += 2) {
			list.add(Namespace.getNamespace(namespacePrefixesAndUris[i], namespacePrefixesAndUris[i + 1]));
		}
		return new Fragment(xml, list.toArray(new Namespace[] {}));
	}

    public String getJson(String path) {
        logRequest(path, "JSON", "GET");
        return executeRequest(request(path).header("Accept", JSON_MEDIA_TYPE).build()).getBody();
    }

	public String getJsonAsSecurityUser(String path) {
		logSecurityUserRequest(path, "JSON", "GET");
		return executeSecurityUserRequest(request(path).header("Accept", JSON_MEDIA_TYPE).build()).getBody();
	}

	public void delete(String path) {
        logRequest(path, "", "DELETE");
        executeRequest(request(path).delete().build());
    }

    public void deleteAsSecurityUser(String path) {
	    logSecurityUserRequest(path, "", "DELETE");
	    executeSecurityUserRequest(request(path).delete().build());
    }

    protected void logRequest(String path, String contentType, String method) {
        if (logger.isInfoEnabled()) {
        	String username = manageConfig != null ? manageConfig.getUsername() : "(unknown)";
            logger.info(String.format("Sending %s %s request as user '%s' to path: %s", contentType, method, username, path));
        }
    }

    protected void logSecurityUserRequest(String path, String contentType, String method) {
        if (logger.isInfoEnabled()) {
	        String username = manageConfig != null ? manageConfig.getUsername() : "(unknown)";
            logger.info(String.format("Sending %s %s request as user '%s' (who must have the 'manage-admin' and 'security' roles) to path: %s",
	            contentType, method, username, path));
        }
    }

	/**
	 * Per #187, and version 3.1.0, this will also use Jackson to remove any comments in the JSON payload, as Jackson
	 * is now configured to ignore comments, but we still don't want to include them in the payload sent to MarkLogic.
	 * @param payload
	 * @return
	 */
	private String cleanJsonPayload(String payload) {
		if (payloadParser == null) {
			payloadParser = new PayloadParser();
		}
		JsonNode node = payloadParser.parseJson(payload);
		StringWriter sw = new StringWriter();
		try {
			ObjectMapperFactory.getObjectMapper().writer().writeValue(sw, node);
		} catch (IOException ex) {
			throw new RuntimeException("Unable to write JSON payload as JsonNode back out to a string, cause: " + ex.getMessage());
		}
		return sw.toString();
	}

	/**
	 * Per #187 and version 3.1.0, when an HttpEntity is constructed with a JSON payload, this method will check to see
	 * if it should "clean" the JSON via the Jackson library, which is primarily intended for removing comments from
	 * JSON (comments that Jackson allows, but aren't allowed by the JSON spec). This behavior is disabled by default.
	 *
	 * @param json
	 * @return
	 */
	protected RequestBody jsonBody(String json) {
		if (manageConfig != null && manageConfig.isCleanJsonPayloads()) {
			json = cleanJsonPayload(json);
		}
		return RequestBody.create(okhttp3.MediaType.parse(JSON_MEDIA_TYPE), json);
	}

	protected RequestBody xmlBody(String xml) {
		return RequestBody.create(okhttp3.MediaType.parse(XML_MEDIA_TYPE), xml);
	}

	protected Request.Builder request(String path) {
		return new Request.Builder().url(buildHttpUrl(path));
	}

	public OkHttpResponse executeRequest(Request request) {
		try {
			return new OkHttpResponse(okHttpClient.newCall(request).execute());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public OkHttpResponse executeSecurityUserRequest(Request request) {
		try {
			return new OkHttpResponse(securityUserOkHttpClient.newCall(request).execute());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public HttpUrl buildHttpUrl(String path) {
		return new HttpUrl.Builder()
			.scheme(manageConfig.getScheme())
			.host(manageConfig.getHost())
			.port(manageConfig.getPort())
			.encodedPath(path.replace(" ", "+"))
			.build();
	}

	public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    public ManageConfig getManageConfig() {
        return manageConfig;
    }

	public void setOkHttpClient(OkHttpClient okHttpClient) {
		this.okHttpClient = okHttpClient;
	}

	public OkHttpClient getSecurityUserOkHttpClient() {
		return securityUserOkHttpClient;
	}

	public void setSecurityUserOkHttpClient(OkHttpClient securityUserOkHttpClient) {
		this.securityUserOkHttpClient = securityUserOkHttpClient;
	}
}
