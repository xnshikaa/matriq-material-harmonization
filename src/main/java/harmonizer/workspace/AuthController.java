package harmonizer.workspace;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    static final String EMAIL = "accountEmail";
    static final String STARTED = "authenticatedAt";
    static final String CSRF = "csrfToken";
    static final String USER = "matriqUser";

    record Credentials(String name, String email, String password) {}
    record Session(Accounts.User user, String csrfToken) {}
    record ErrorMessage(String error) {}

    private final Accounts accounts;

    public AuthController(Accounts accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/session")
    Session session(HttpServletRequest request) {
        var session = request.getSession();
        synchronized (session) {
            if (session.getAttribute(CSRF) == null) session.setAttribute(CSRF, Accounts.token());
            return new Session((Accounts.User) request.getAttribute(USER), (String) session.getAttribute(CSRF));
        }
    }

    @PostMapping("/signup")
    Session signup(@RequestBody Credentials input, HttpServletRequest request) throws IOException {
        accounts.throttle("signup", request.getRemoteAddr(), input.email());
        return authenticate(request, accounts.register(input.name(), input.email(), input.password()));
    }

    @PostMapping("/login")
    Session login(@RequestBody Credentials input, HttpServletRequest request) {
        accounts.throttle("login", request.getRemoteAddr(), input.email());
        return authenticate(request, accounts.login(input.email(), input.password()));
    }

    @PostMapping("/reviewer-login")
    Session reviewerLogin(@RequestBody Credentials input, HttpServletRequest request) {
        accounts.throttle("reviewer-login", request.getRemoteAddr(), input.email());
        return authenticate(request, accounts.loginReviewer(input.email(), input.password()));
    }

    @PostMapping("/logout")
    Session logout(HttpServletRequest request) {
        Accounts.User user = (Accounts.User) request.getAttribute(USER);
        request.getSession(false).invalidate();
        request.removeAttribute(USER);
        Accounts.audit("session-ended", user.id());
        return session(request);
    }

    private Session authenticate(HttpServletRequest request, Accounts.User user) {
        request.getSession(false).invalidate();
        var session = request.getSession();
        session.setAttribute(EMAIL, user.email());
        session.setAttribute(STARTED, Instant.now());
        request.setAttribute(USER, user);
        return session(request);
    }
}
