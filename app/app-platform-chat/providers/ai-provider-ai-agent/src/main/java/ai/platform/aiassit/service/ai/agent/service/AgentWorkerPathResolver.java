package ai.platform.aiassit.service.ai.agent.service;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves bundled or source-tree workers without assuming a fixed JVM working directory. */
final class AgentWorkerPathResolver {

    private AgentWorkerPathResolver() {
    }

    static Path findFromCurrentOrAncestor(Path startDirectory, Path relativePath) {
        if (relativePath == null) {
            return null;
        }
        Path current = (startDirectory == null ? Path.of("") : startDirectory)
                .toAbsolutePath()
                .normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }
}
