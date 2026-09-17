package it.bancaditalia.quarkus.poc.jakarta.api;

import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import it.bancaditalia.quarkus.poc.jakarta.platform.PlatformConfigResource;

/** Same tests against the packaged fast-jar (./mvnw verify -DskipITs=false). */
@QuarkusIntegrationTest
@WithTestResource(value = PlatformConfigResource.class, scope = TestResourceScope.RESTRICTED_TO_CLASS)
class AccountResourceIT extends AccountResourceTest {
}
