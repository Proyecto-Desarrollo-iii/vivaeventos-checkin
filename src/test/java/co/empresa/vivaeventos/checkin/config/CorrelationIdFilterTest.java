package co.empresa.vivaeventos.checkin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void usesCorrelationIdFromHeader_whenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "existing-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals("existing-id", request.getAttribute(CorrelationIdFilter.REQUEST_ATTR));
        assertEquals("existing-id", response.getHeader(CorrelationIdFilter.HEADER));
    }

    @Test
    void generatesCorrelationId_whenNotPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        String correlationId = (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
        assertNotNull(correlationId);
        assertTrue(correlationId.length() > 0);
        assertEquals(correlationId, response.getHeader(CorrelationIdFilter.HEADER));
    }
}
