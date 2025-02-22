package pl.allegro.tech.hermes.integration;

import com.google.common.collect.ImmutableMap;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import pl.allegro.tech.hermes.integration.env.HermesIntegrationEnvironment;
import pl.allegro.tech.hermes.integration.env.SharedServices;
import pl.allegro.tech.hermes.integration.helper.AuditEventEndpoint;
import pl.allegro.tech.hermes.integration.helper.Waiter;
import pl.allegro.tech.hermes.test.helper.endpoint.BrokerOperations;
import pl.allegro.tech.hermes.test.helper.endpoint.HermesAPIOperations;
import pl.allegro.tech.hermes.test.helper.endpoint.HermesEndpoints;
import pl.allegro.tech.hermes.test.helper.endpoint.HermesPublisher;

import static jakarta.ws.rs.core.Response.Status.OK;
import static pl.allegro.tech.hermes.integration.env.SharedServices.services;

public class IntegrationTest extends HermesIntegrationEnvironment {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationTest.class);

    protected HermesEndpoints management;

    protected HermesPublisher publisher;

    protected HermesAPIOperations operations;

    protected Waiter wait;

    protected BrokerOperations brokerOperations;

    protected AuditEventEndpoint auditEvents;

    @BeforeClass
    public void initializeIntegrationTest() {
        this.management = new HermesEndpoints(MANAGEMENT_ENDPOINT_URL, CONSUMER_ENDPOINT_URL);
        this.publisher = new HermesPublisher(FRONTEND_URL);
        this.brokerOperations = new BrokerOperations(
                ImmutableMap.of(
                        PRIMARY_KAFKA_CLUSTER_NAME, kafkaClusterOne.getBootstrapServersForExternalClients(),
                        SECONDARY_KAFKA_CLUSTER_NAME, kafkaClusterTwo.getBootstrapServersForExternalClients()
                ), KAFKA_NAMESPACE);
        this.wait = new Waiter(management, services().zookeeper(), brokerOperations, PRIMARY_KAFKA_CLUSTER_NAME, KAFKA_NAMESPACE);
        this.operations = new HermesAPIOperations(management, wait);
        this.auditEvents = new AuditEventEndpoint(SharedServices.services().auditEventMock());
    }

    @AfterMethod
    public void after() {
        auditEvents.reset();
        try {
            removeSubscriptions(management, wait);
            removeTopics(management, wait);
        } catch (RuntimeException e) {
            logger.error("Error while removing topics and subscriptions", e);
        }
    }

    @AfterSuite
    public void afterSuite() {
        auditEvents.stop();
    }

    protected void removeSubscriptions(HermesEndpoints management, Waiter wait) {
        management.query().querySubscriptions("{\"query\": {}}").forEach(sub -> {
            Response response = management.subscription().remove(sub.getQualifiedTopicName(), sub.getName());
            if (response.getStatus() == OK.getStatusCode()) {
                wait.untilSubscriptionRemoved(sub);
            } else {
                logger.warn("Could not remove subscription {}. Received {} http status. Reason {}",
                        sub.getQualifiedName().getQualifiedName(), response.getStatus(), response.readEntity(String.class));
            }
        });
    }

    protected void removeTopics(HermesEndpoints management, Waiter wait) {
        management.query().queryTopics("{\"query\": {}}").forEach(topic -> {
            Response response = management.topic().remove(topic.getQualifiedName());
            if (response.getStatus() == OK.getStatusCode()) {
                wait.untilTopicRemoved(topic);
            } else {
                logger.warn("Could not remove topic {}. Received {} http status. Reason {}",
                        topic.getQualifiedName(), response.getStatus(), response.readEntity(String.class));
            }
        });
    }
}
