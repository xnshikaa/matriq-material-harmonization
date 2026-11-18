package harmonizer.workspace;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Accounts {
    private static final org.slf4j.Logger SECURITY = org.slf4j.LoggerFactory.getLogger("matriq.security");
    private static final int ITERATIONS = 600_000;
    private static final int WINDOW_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:[.][a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$");

    public record User(String id, String name, String email, boolean reviewer) {}
    record Account(String id, String name, String email, String salt, String hash) {}
    record AccountState(Map<String, Account> accounts) {}
    private record Limit(long until, int count) {}

    private final WorkspaceStorage storage;
    private final Map<String, Limit> attempts = new HashMap<>();
    private final String dummySalt = token();
    private final String dummyHash = hash("unused", dummySalt);
    private Map<String, Account> accounts;
    private final Set<String> reviewerIds = new HashSet<>();

    public Accounts(WorkspaceStorage storage,
                    @Value("${matriq.reviewer-ids:}") String reviewerIds) throws IOException {
        this.storage = storage;
        for (String id : reviewerIds.split(",")) {
            if (!id.isBlank()) this.reviewerIds.add(UUID.fromString(id.strip()).toString());
        }
        accounts = storage.read("accounts.json", AccountState.class, new AccountState(Map.of())).accounts();
    }

    synchronized User register(String name, String email, String password) throws IOException {
        String key = normalize(email);
        if (key.length() > 254 || !EMAIL.matcher(key).matches() || key.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        }
        if (name == null || name.isBlank() || name.strip().length() > 100
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a full name of up to 100 characters.");
        }
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a password between 12 and 128 characters.");
        }
        if (accounts.containsKey(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account cannot be created with these details.");
        }
        String salt = token();
        Account account = new Account(UUID.randomUUID().toString(), name.strip(), key, salt, hash(password, salt));
        Map<String, Account> updated = new HashMap<>(accounts);
        updated.put(key, account);
        storage.write("accounts.json", new AccountState(updated));
        accounts = updated;
        audit("account-created", account.id());
        return view(account);
    }

    synchronized User login(String email, String password) {
        Account account = accounts.get(normalize(email));
        if (password == null || password.length() > 128) password = "";
        String actual = hash(password, account == null ? dummySalt : account.salt());
        String expected = account == null ? dummyHash : account.hash();
        if (!MessageDigest.isEqual(Base64.getDecoder().decode(actual), Base64.getDecoder().decode(expected))
                || account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        audit("session-started", account.id());
        return view(account);
    }

    public synchronized User lookup(String email) {
        Account account = accounts.get(email);
        return account == null ? null : view(account);
    }

    synchronized void throttle(String operation, String address, String email) {
        long now = Instant.now().getEpochSecond();
        attempts.values().removeIf(limit -> limit.until() <= now);
        limit(operation + ":ip:" + address, now, 30);
        String key = normalize(email);
        if (!key.isEmpty() && key.length() <= 254) limit(operation + ":email:" + key, now, 10);
    }

    private void limit(String key, long now, int max) {
        Limit previous = attempts.get(key);
        if ((previous == null && attempts.size() >= 20_000) || (previous != null && previous.count() >= max)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again later.");
        }
        attempts.put(key, previous == null ? new Limit(now + WINDOW_SECONDS, 1)
                : new Limit(previous.until(), previous.count() + 1));
    }

    private User view(Account account) {
        return new User(account.id(), account.name(), account.email(), reviewerIds.contains(account.id()));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    static void audit(String action, String accountId) {
        SECURITY.info("action={} accountId={}", action, accountId);
    }

    static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String hash(String password, String salt) {
        PBEKeySpec key = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256);
        try {
            return Base64.getEncoder().encodeToString(
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable.", error);
        } finally {
            key.clearPassword();
        }
    }
}
