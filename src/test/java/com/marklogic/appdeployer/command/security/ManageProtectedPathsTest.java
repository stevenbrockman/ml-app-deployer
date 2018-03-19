package com.marklogic.appdeployer.command.security;

import com.marklogic.appdeployer.command.AbstractManageResourceTest;
import com.marklogic.appdeployer.command.Command;
import com.marklogic.mgmt.resource.ResourceManager;
import com.marklogic.mgmt.resource.security.ProtectedPathManager;

/**
 * TODO This isn't working with OkHttp3 which throws an error if a 204 returns content. Created bug 49407 with ML
 * for this.
 *
 */
public class ManageProtectedPathsTest extends AbstractManageResourceTest {
	@Override
	protected ResourceManager newResourceManager() {
        return new ProtectedPathManager(manageClient);
	}

	@Override
	protected Command newCommand() {
		return new DeployProtectedPathsCommand();
	}

	@Override
	protected String[] getResourceNames() {
		return new String[] { "/test:element" };
	}

}
