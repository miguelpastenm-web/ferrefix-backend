package cl.ferrefix.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestAccessDeniedHandlerTest {

    private final SecurityErrorWriter writer = new SecurityErrorWriter(JsonMapper.builder().build());

    @Test
    void responde403ConErrorResponseDto() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/api/v1/productos/1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        new RestAccessDeniedHandler(writer).handle(req, res, new AccessDeniedException("no"));

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentType().startsWith("application/json"));
        String body = res.getContentAsString();
        assertTrue(body.contains("\"status\":403"));
        assertTrue(body.contains("\"path\":\"/api/v1/productos/1\""));
    }

    @Test
    void entryPointResponde401ConErrorResponseDto() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse res = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(writer).commence(req, res,
                new org.springframework.security.authentication.BadCredentialsException("x"));

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("\"status\":401"));
    }
}
