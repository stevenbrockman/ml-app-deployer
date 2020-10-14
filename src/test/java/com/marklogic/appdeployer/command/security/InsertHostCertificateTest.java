package com.marklogic.appdeployer.command.security;

import com.marklogic.appdeployer.AbstractAppDeployerTest;
import com.marklogic.appdeployer.ConfigDir;
import com.marklogic.mgmt.resource.security.CertificateTemplateManager;
import com.marklogic.rest.util.Fragment;
import org.junit.Test;

import java.io.File;
import java.util.List;


public class InsertHostCertificateTest extends AbstractAppDeployerTest {

	private final static String TEMPLATE_NAME = "sample-app-certificate-template";
	private final static String CERTIFICATE_HOSTNAME = "host1.marklogic.com";

	@Test
	public void getCertificateHostName() {
		InsertCertificateHostsTemplateCommand command = new InsertCertificateHostsTemplateCommand();

		assertEquals(
			"When the filename ends in .crt, the rest of the filename is assumed to be the hostname",
			"example", command.getCertificateHostName(new File("example.crt")));

		assertNull(
			"If the filename does not end in .crt, then null should be returned, as it's thus not known what the hostname should be",
			command.getCertificateHostName(new File("example.pem")));
	}

	@Test
	public void insertCertificateAndVerify() {
		appConfig.setConfigDir(new ConfigDir(new File("src/test/resources/sample-app/host-certificates")));

		initializeAppDeployer(
			new DeployCertificateAuthoritiesCommand(),
			new DeployCertificateTemplatesCommand(),
			new InsertCertificateHostsTemplateCommand());

		CertificateTemplateManager mgr = new CertificateTemplateManager(manageClient);

		try {
			deploySampleApp();
			verifyHostCertificateWasInserted(mgr);

			// Make sure nothing breaks by deploying it again
			deploySampleApp();
			verifyHostCertificateWasInserted(mgr);
		} finally {
			/**
			 * TODO Deleting certificate authorities in ML 9.0-5 via the Manage API doesn't appear to be working, so
			 * the certificate authority that's created by this class is left over.
			 */
			undeploySampleApp();

			List<String> templateNames = mgr.getAsXml().getListItemNameRefs();
			assertFalse(templateNames.contains(TEMPLATE_NAME));
		}
	}

	private void verifyHostCertificateWasInserted(CertificateTemplateManager mgr) {
		List<String> templateNames = mgr.getAsXml().getListItemNameRefs();
		assertTrue(templateNames.contains(TEMPLATE_NAME));

		Fragment xml = mgr.getCertificatesForTemplate(TEMPLATE_NAME);
		assertEquals(CERTIFICATE_HOSTNAME, xml.getElementValue("/msec:certificate-list/msec:certificate/msec:host-name"));
		assertEquals("MarkLogicBogusCA", xml.getElementValue("/msec:certificate-list/msec:certificate/cert:cert/cert:issuer/cert:commonName"));

		verifyCommandThinksTheCertificateExists(mgr);
	}

	private void verifyCommandThinksTheCertificateExists(CertificateTemplateManager mgr) {
		InsertCertificateHostsTemplateCommand command = new InsertCertificateHostsTemplateCommand();

		assertTrue(
			"Since matching on hostname is disabled by default, then the command should return true regardless of what the filename is",
			command.certificateExists(TEMPLATE_NAME, new File("anything.crt"), mgr));

		// Now turn on matching and verify
		command.setMatchCertificateOnHostName(true);
		assertTrue(command.certificateExists(TEMPLATE_NAME, new File(CERTIFICATE_HOSTNAME + ".crt"), mgr));
		assertFalse(
			"A match won't be found here because the filename doesn't end with .crt",
			command.certificateExists(TEMPLATE_NAME, new File(CERTIFICATE_HOSTNAME + ".pem"), mgr));
		assertFalse(
			"A match won't be found here because the filename doesn't start with the hostname",
			command.certificateExists(TEMPLATE_NAME, new File("something.crt"), mgr));
	}
}
