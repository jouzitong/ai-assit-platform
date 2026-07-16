package ai.platform.aiassit.chat.workflow.data.importer;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillPackageInspectorTest {

    private final SkillPackageInspector inspector = new SkillPackageInspector();

    @Test
    void acceptsPortableSkillPackageAndStripsPackageRoot() throws Exception {
        byte[] archive = zip(Map.of(
                "analysis/SKILL.md", "---\nname: analysis\ndescription: Analyze a request safely\n---\n\n# Analysis\n",
                "analysis/templates/report.md", "# Report\n"));

        InspectedSkillPackage result = inspector.inspect("analysis.zip", archive);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getPackageRoot()).isEqualTo("analysis");
        assertThat(result.getEntrypoint()).isEqualTo("SKILL.md");
        assertThat(result.getSkillName()).isEqualTo("analysis");
        assertThat(result.getFiles()).extracting(InspectedSkillPackage.File::getPath)
                .containsExactlyInAnyOrder("SKILL.md", "templates/report.md");
        assertThat(result.getOriginalPackage()).isEqualTo(archive);
    }

    @Test
    void rejectsRootlessPackageAndEmbeddedCredential() throws Exception {
        InspectedSkillPackage rootless = inspector.inspect("rootless.zip", zip(Map.of(
                "SKILL.md", "---\nname: rootless\ndescription: Rootless package\n---\n")));
        InspectedSkillPackage secret = inspector.inspect("unsafe.zip", zip(Map.of(
                "unsafe/SKILL.md", "---\nname: unsafe\ndescription: Unsafe package\n---\napi_key: abcdefghijklmnop")));

        assertThat(rootless.isValid()).isFalse();
        assertThat(rootless.getErrors()).anyMatch(message -> message.contains("top-level directory")
                || message.contains("inside the single top-level directory"));
        assertThat(secret.isValid()).isFalse();
        assertThat(secret.getErrors()).anyMatch(message -> message.contains("credential"));
    }

    @Test
    void rejectsUnixSymlinkEntry() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
            add(zip, "unsafe/SKILL.md",
                    "---\nname: unsafe\ndescription: Unsafe package\n---\n");
            ZipArchiveEntry link = new ZipArchiveEntry("unsafe/link");
            link.setUnixMode(0120000 | 0777);
            zip.putArchiveEntry(link);
            zip.write("SKILL.md".getBytes(StandardCharsets.UTF_8));
            zip.closeArchiveEntry();
        }

        InspectedSkillPackage result = inspector.inspect("unsafe.zip", bytes.toByteArray());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(message -> message.contains("symlink"));
    }

    @Test
    void exposesDocumentedSafetyLimits() {
        assertThat(SkillPackageInspector.MAX_COMPRESSED_BYTES).isEqualTo(50L * 1024 * 1024);
        assertThat(SkillPackageInspector.MAX_FILES).isEqualTo(500);
        assertThat(SkillPackageInspector.MAX_FILE_BYTES).isEqualTo(25L * 1024 * 1024);
        assertThat(SkillPackageInspector.MAX_UNCOMPRESSED_BYTES).isEqualTo(100L * 1024 * 1024);
    }

    private byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
            Map<String, String> ordered = new LinkedHashMap<>(files);
            for (Map.Entry<String, String> file : ordered.entrySet()) add(zip, file.getKey(), file.getValue());
        }
        return bytes.toByteArray();
    }

    private void add(ZipArchiveOutputStream zip, String path, String value) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(path);
        zip.putArchiveEntry(entry);
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeArchiveEntry();
    }
}
