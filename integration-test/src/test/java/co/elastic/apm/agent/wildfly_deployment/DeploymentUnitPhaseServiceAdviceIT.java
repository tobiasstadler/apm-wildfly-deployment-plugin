/*
   Copyright 2021 Tobias Stadler

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package co.elastic.apm.agent.wildfly_deployment;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.ClearType;
import org.mockserver.model.Format;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

@Testcontainers
class DeploymentUnitPhaseServiceAdviceIT {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    private static final GenericContainer<?> MOCK_SERVER = new GenericContainer<>(DockerImageName.parse("mockserver/mockserver:mockserver-5.11.2"))
            .withNetwork(NETWORK)
            .withNetworkAliases("apm-server")
            .withExposedPorts(1080)
            .waitingFor(Wait.forHttp("/mockserver/status").withMethod("PUT").forStatusCode(200));

    @Container
    private static final GenericContainer<?> WILDFLY = new GenericContainer<>(DockerImageName.parse("quay.io/wildfly/wildfly:26.0.0.Final"))
            .withNetwork(NETWORK)
            .withExposedPorts(8080)
            .withFileSystemBind("target/apm-wildfly-deployment-plugin-it.war", "/opt/jboss/wildfly/standalone/deployments/apm-wildfly-deployment-plugin-it.war")
            .withFileSystemBind("target/elastic-apm-agent.jar", "/tmp/elastic-apm-agent.jar")
            .withFileSystemBind("target/test-classes/elasticapm.properties", "/tmp/elasticapm.properties")
            .withFileSystemBind("target/apm-plugins/apm-wildfly-deployment-plugin.jar", "/tmp/apm-plugins/apm-wildfly-deployment-plugin.jar")
            .withEnv("JAVA_OPTS", "-javaagent:/tmp/elastic-apm-agent.jar")
            .waitingFor(Wait.forLogMessage(".*WFLYSRV0025.*", 1))
            .dependsOn(MOCK_SERVER);

    private static MockServerClient MOCK_SERVER_CLIENT;

    @BeforeAll
    static void setUp() {
        MOCK_SERVER_CLIENT = new MockServerClient(MOCK_SERVER.getContainerIpAddress(), MOCK_SERVER.getMappedPort(1080));
        MOCK_SERVER_CLIENT.when(request("/")).respond(response().withStatusCode(200).withBody(json("{\"version\": \"7.13.0\"}")));
        MOCK_SERVER_CLIENT.when(request("/config/v1/agents")).respond(response().withStatusCode(403));
        MOCK_SERVER_CLIENT.when(request("/intake/v2/events")).respond(response().withStatusCode(200));
    }

    @BeforeEach
    void clear() {
        MOCK_SERVER_CLIENT.clear(request("/intake/v2/events"), ClearType.LOG);
    }

    @Test
    void testServiceName() throws IOException {
        URL url = new URL("http://" + WILDFLY.getContainerIpAddress() + ":" + WILDFLY.getMappedPort(8080) + "/apm-wildfly-deployment-plugin-it/greeting");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openConnection().getInputStream()))) {
            assertEquals("Hello World!", reader.readLine());
        }

        assertEquals("My Service Name", JsonPath.read(getTransaction(), "$.context.service.name"));
    }

    private static Map<String, Object> getTransaction() {
        return ((List<String>) JsonPath.read(MOCK_SERVER_CLIENT.retrieveRecordedRequests(request("/intake/v2/events"), Format.JAVA), "$..body.rawBytes"))
                .stream()
                .map(Base64.getDecoder()::decode)
                .map(String::new)
                .flatMap(s -> Arrays.stream(s.split("\r?\n")))
                .map(JsonPath::parse)
                .flatMap(dc -> ((List<Map<String, Object>>) dc.read("$[?(@.transaction)].transaction")).stream())
                .findAny()
                .get();
    }
}
