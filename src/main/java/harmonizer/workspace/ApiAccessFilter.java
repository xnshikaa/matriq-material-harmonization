package harmonizer.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiAccessFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC = Set.of("GET /api/auth/session", "POST /api/auth/login",
            "POST /api/auth/signup");
    private final Accounts accounts;
    private final ObjectMapper mapper;

    public ApiAccessFilter(Accounts accounts, ObjectMapper mapper) {
        this.accounts = accounts;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        var session = request.getSession(false);
        Accounts.User user = null;
        if (session != null && session.getAttribute(AuthController.STARTED) instanceof Instant start) {
            if (Duration.between(start, Instant.now()).compareTo(Duration.ofHours(8)) >= 0) {
                session.invalidate();
                session = null;
            } else {
                user = accounts.lookup((String) session.getAttribute(AuthController.EMAIL));
            }
        }
        if (user == null && !PUBLIC.contains(request.getMethod() + " " + path)) {
            deny(response, 401, "Sign in to continue.");
            return;
        }
        if (path.equals("/api/harmonize/reject") && (user == null || !user.reviewer())) {
            deny(response, 403, "Reviewer access is required.");
            return;
        }
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
            String actual = request.getHeader("X-CSRF-Token");
            String expected = session == null ? null : (String) session.getAttribute(AuthController.CSRF);
            if (actual == null || expected == null || !MessageDigest.isEqual(
                    actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
                deny(response, 403, "Session verification failed. Refresh and try again.");
                return;
            }
        }
        if (user != null) request.setAttribute(AuthController.USER, user);
        chain.doFilter(request, response);
    }

    private void deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), new AuthController.ErrorMessage(message));
    }
}
