package harmonizer.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceStorage {
    private final Path directory;
    private final FileChannel lockChannel;
    private final ObjectMapper mapper;

    public WorkspaceStorage(@Value("${matriq.state-directory:.matriq-state}") String directory,
                            ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        this.directory = Files.createDirectories(Path.of(directory).toAbsolutePath());
        lockChannel = FileChannel.open(this.directory.resolve("owner.lock"),
                StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        if (lockChannel.tryLock() == null) {
            lockChannel.close();
            throw new IllegalStateException("MATRIQ state directory is already in use.");
        }
    }

    <T> T read(String file, Class<T> type, T initial) throws IOException {
        Path path = directory.resolve(file);
        return Files.exists(path) ? mapper.readValue(path.toFile(), type) : initial;
    }

    void write(String file, Object state) throws IOException {
        StateFile.write(mapper, directory.resolve(file), state);
    }

    @PreDestroy
    void close() throws IOException {
        lockChannel.close();
    }
}
