package it.bancaditalia.quarkus.poc.ejb.api;

import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import it.bancaditalia.quarkus.poc.ejb.platform.PlatformConfigResource;

@QuarkusIntegrationTest
@WithTestResource(value = PlatformConfigResource.class, scope = TestResourceScope.RESTRICTED_TO_CLASS)
class InstructionResourceIT extends InstructionResourceTest {
}
