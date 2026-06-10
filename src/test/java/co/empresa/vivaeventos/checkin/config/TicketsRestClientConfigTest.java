package co.empresa.vivaeventos.checkin.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TicketsRestClientConfigTest {

    private final TicketsRestClientConfig config = new TicketsRestClientConfig();

    @Test
    void ticketsRestClient_createsBean() {
        RestClient client = config.ticketsRestClient("http://localhost:8080", 3000, 5000);

        assertNotNull(client);
    }
}
