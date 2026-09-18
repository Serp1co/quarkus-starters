package it.bancaditalia.quarkus.poc.remote.provider.api;

import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import it.bancaditalia.quarkus.poc.remote.provider.platform.PlatformConfigResource;

@QuarkusIntegrationTest
@WithTestResource(value = PlatformConfigResource.class, scope = TestResourceScope.RESTRICTED_TO_CLASS)
class CounterpartyResourceIT extends CounterpartyResourceTest {
}
