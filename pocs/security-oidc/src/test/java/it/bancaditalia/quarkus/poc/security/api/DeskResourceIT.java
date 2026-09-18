package it.bancaditalia.quarkus.poc.security.api;

import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import it.bancaditalia.quarkus.poc.security.platform.PlatformConfigResource;

/** The same tests on the packaged artifact under the prod profile, the platform played by PlatformConfigResource. */
@QuarkusIntegrationTest
@WithTestResource(value = PlatformConfigResource.class, scope = TestResourceScope.RESTRICTED_TO_CLASS)
class DeskResourceIT extends DeskResourceTest {
}
