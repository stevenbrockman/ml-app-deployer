package com.marklogic.appdeployer.command.security;

import com.marklogic.appdeployer.command.AbstractIncrementalDeployTest;
import com.marklogic.appdeployer.command.ResourceFileManagerImpl;
import com.marklogic.mgmt.resource.security.RoleManager;
import com.marklogic.mgmt.resource.security.UserManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class IncrementallyDeployUsersTest extends AbstractIncrementalDeployTest {

	private UserManager userManager;
	private RoleManager roleManager;

	@Before
	public void setup() {
		userManager = new UserManager(manageClient);
		roleManager = new RoleManager(manageClient);
	}

	@After
	public void teardown() {
		this.manageClient = originalManageClient;
		initializeAppDeployer(new DeployUsersCommand(), new DeployRolesCommand());
		undeploySampleApp();
		assertUsersDontExist();
		assertRolesDontExist();
	}

	@Test
	public void test() {
		this.originalManageClient = this.manageClient;
		assertUsersDontExist();

		initializeAppDeployer(new DeployUsersCommand());
		deploySampleApp();
		assertUsersExist();

		// Ensure that no calls can be made to the Manage API. The deployment should succeed because neither of
		// the role files have been modified.
		this.manageClient = null;
		initializeAppDeployer(new DeployUsersCommand());
		deploySampleApp();
		assertUsersExist();
	}

	@Test
	public void usersAndRoles() throws IOException  {
		this.originalManageClient = this.manageClient;

		assertUsersDontExist();
		assertRolesDontExist();

		initializeAppDeployer(new DeployUsersCommand(), new DeployRolesCommand());
		deploySampleApp();
		assertUsersExist();
		assertRolesExist();

		this.manageClient = null;
		initializeAppDeployer(new DeployUsersCommand(), new DeployRolesCommand());
		deploySampleApp();
		assertUsersExist();
		assertRolesExist();

		Properties props = new Properties();
		FileReader reader = new FileReader(ResourceFileManagerImpl.DEFAULT_FILE_PATH);
		props.load(reader);
		reader.close();
		assertEquals("There should be 2 entries for users and 2 entries for roles", 4, props.size());
	}

	private void assertUsersExist() {
		assertTrue(userManager.exists("sample-app-jane"));
		assertTrue(userManager.exists("sample-app-john"));
	}

	private void assertUsersDontExist() {
		assertFalse(userManager.exists("sample-app-jane"));
		assertFalse(userManager.exists("sample-app-john"));
	}

	private void assertRolesExist() {
		assertTrue(roleManager.exists("sample-app-role1"));
		assertTrue(roleManager.exists("sample-app-role2"));
	}

	private void assertRolesDontExist() {
		assertFalse(roleManager.exists("sample-app-role1"));
		assertFalse(roleManager.exists("sample-app-role2"));
	}
}
