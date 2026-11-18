package harmonizer.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceStorage {
    private final Path directory;
    private final FileChannel lockChannel;
    private final ObjectMapper mapper;
    private final PostgresState postgres;

    public WorkspaceStorage(String directory, ObjectMapper mapper) throws IOException {
        this(directory, mapper, "", "", "", false);
    }

    @Autowired
    public WorkspaceStorage(@Value("${matriq.state-directory:.matriq-state}") String directory,
                            ObjectMapper mapper,
                            @Value("${MATRIQ_JDBC_URL:}") String jdbcUrl,
                            @Value("${MATRIQ_DB_USER:}") String dbUser,
                            @Value("${MATRIQ_DB_PASSWORD:}") String dbPassword,
                            @Value("${MATRIQ_REQUIRE_DATABASE:false}") boolean requireDatabase) throws IOException {
        this.mapper = mapper;
        if (!jdbcUrl.isBlank()) {
            if (!jdbcUrl.startsWith("jdbc:postgresql://") || dbUser.isBlank() || dbPassword.isBlank()) {
                throw new IllegalStateException("Set the MATRIQ PostgreSQL JDBC URL, user and password.");
            }
            postgres = new PostgresState(jdbcUrl, dbUser, dbPassword);
            this.directory = null;
            lockChannel = null;
            return;
        }
        if (requireDatabase) throw new IllegalStateException("Set MATRIQ_JDBC_URL for hosted workspace persistence.");
        postgres = null;
        this.directory = Files.createDirectories(Path.of(directory).toAbsolutePath());
        lockChannel = FileChannel.open(this.directory.resolve("owner.lock"),
                StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        if (lockChannel.tryLock() == null) {
            lockChannel.close();
            throw new IllegalStateException("MATRIQ state directory is already in use.");
        }
    }

    <T> T read(String file, Class<T> type, T initial) throws IOException {
        if (postgres != null) {
            byte[] payload = postgres.read(file);
            return payload == null ? initial : mapper.readValue(payload, type);
        }
        Path path = directory.resolve(file);
        return Files.exists(path) ? mapper.readValue(path.toFile(), type) : initial;
    }

    void write(String file, Object state) throws IOException {
        if (postgres != null) postgres.write(file, mapper.writeValueAsBytes(state));
        else StateFile.write(mapper, directory.resolve(file), state);
    }

    @PreDestroy
    void close() throws IOException {
        if (lockChannel != null) lockChannel.close();
    }
}
