package ai.platform.aiassit.chat.agent.control.data.importer;

import org.apache.commons.compress.archivers.zip.AsiExtraField;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Security-bounded inspector for the portable Agent Skills ZIP profile. */
@Component
public class SkillPackageInspector {

    public static final long MAX_COMPRESSED_BYTES = 50L * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 25L * 1024 * 1024;
    public static final int MAX_FILES = 500;
    public static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    public static final int MAX_COMPRESSION_RATIO = 100;
    private static final int MAX_ENTRIES = 1_000;
    private static final int MAX_FRONTMATTER_BYTES = 64 * 1024;
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:.*");
    private static final Pattern SKILL_NAME = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern OPENAI_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{20,}\\b");
    private static final Pattern AWS_KEY = Pattern.compile("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b");
    private static final Pattern CREDENTIAL_ASSIGNMENT = Pattern.compile(
            "(?i)(?:api[_-]?key|access[_-]?token|client[_-]?secret|password)\\s*[:=]\\s*['\"]?[A-Za-z0-9_./+=-]{12,}");
    private static final Set<String> NESTED_ARCHIVE_EXTENSIONS = Set.of(
            ".zip", ".jar", ".tar", ".tgz", ".gz", ".7z", ".rar");

    public InspectedSkillPackage inspect(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            return rejected("ZIP file is required");
        }
        if (upload.getSize() > MAX_COMPRESSED_BYTES) {
            return rejected("compressed package exceeds " + MAX_COMPRESSED_BYTES + " bytes");
        }
        try {
            return inspect(upload.getOriginalFilename(), upload.getBytes());
        } catch (IOException ex) {
            return rejected("failed to read ZIP package: " + safeMessage(ex));
        }
    }

    /** Used by the FORM path so generated packages pass the exact same checks as uploaded ZIPs. */
    public InspectedSkillPackage inspect(String filename, byte[] packageBytes) {
        InspectedSkillPackage result = new InspectedSkillPackage();
        if (!StringUtils.hasText(filename) || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            result.getErrors().add("only .zip packages are supported");
            return result;
        }
        if (packageBytes == null || packageBytes.length == 0) {
            result.getErrors().add("ZIP file is required");
            return result;
        }
        if (packageBytes.length > MAX_COMPRESSED_BYTES) {
            result.getErrors().add("compressed package exceeds " + MAX_COMPRESSED_BYTES + " bytes");
            return result;
        }
        result.setOriginalPackage(packageBytes.clone());
        Set<String> seenPaths = new HashSet<>();
        Set<String> roots = new HashSet<>();
        List<InspectedSkillPackage.File> originalFiles = new ArrayList<>();
        int entries = 0;
        try (ZipFile zip = new ZipFile(new SeekableInMemoryByteChannel(packageBytes), StandardCharsets.UTF_8.name())) {
            Enumeration<ZipArchiveEntry> values = zip.getEntriesInPhysicalOrder();
            while (values.hasMoreElements()) {
                ZipArchiveEntry entry = values.nextElement();
                if (++entries > MAX_ENTRIES) {
                    result.getErrors().add("ZIP contains too many entries");
                    break;
                }
                String path = validateAndNormalizePath(entry.getName(), result);
                if (path == null) break;
                String duplicateKey = path.toLowerCase(Locale.ROOT);
                if (!seenPaths.add(duplicateKey)) {
                    result.getErrors().add("duplicate or case-conflicting ZIP path: " + path);
                    break;
                }
                String root = root(path);
                if (StringUtils.hasText(root)) roots.add(root);
                if (entry.isDirectory()) continue;
                if (entry.getGeneralPurposeBit().usesEncryption()) {
                    result.getErrors().add("encrypted ZIP entries are not supported: " + path);
                    break;
                }
                if (entry.isUnixSymlink() || isHardOrSymbolicLink(entry)) {
                    result.getErrors().add("symlink and hardlink entries are not allowed: " + path);
                    break;
                }
                if (result.getFiles().size() >= MAX_FILES) {
                    result.getErrors().add("ZIP contains more than " + MAX_FILES + " files");
                    break;
                }
                if (entry.getSize() > MAX_FILE_BYTES) {
                    result.getErrors().add("file exceeds per-file limit: " + path);
                    break;
                }
                byte[] content;
                try (InputStream input = zip.getInputStream(entry)) {
                    content = readBounded(input, path, result);
                }
                if (content == null) break;
                result.setTotalSize(result.getTotalSize() + content.length);
                if (result.getTotalSize() > MAX_UNCOMPRESSED_BYTES) {
                    result.getErrors().add("uncompressed package exceeds " + MAX_UNCOMPRESSED_BYTES + " bytes");
                    break;
                }
                long compressed = entry.getCompressedSize();
                if (compressed > 0 && content.length > compressed * (long) MAX_COMPRESSION_RATIO) {
                    result.getErrors().add("suspicious compression ratio: " + path);
                    break;
                }
                if (isExecutable(entry) || isExecutableBinary(content)) {
                    result.getErrors().add("executable files are not allowed in a portable Skill package: " + path);
                    break;
                }
                InspectedSkillPackage.File file = file(path, content);
                originalFiles.add(file);
                scanContent(file, result);
                if (!result.getErrors().isEmpty()) break;
            }
        } catch (IOException | RuntimeException ex) {
            result.getErrors().add("invalid or unsupported ZIP package: " + safeMessage(ex));
        }

        if (result.getErrors().isEmpty() && roots.size() != 1) {
            result.getErrors().add("Skill package must contain exactly one top-level directory");
        }
        if (result.getErrors().isEmpty()) {
            String packageRoot = roots.iterator().next();
            result.setPackageRoot(packageRoot);
            for (InspectedSkillPackage.File file : originalFiles) {
                if (!file.getPath().startsWith(packageRoot + "/")) {
                    result.getErrors().add("all files must be inside the single top-level directory");
                    break;
                }
                file.setPath(file.getPath().substring(packageRoot.length() + 1));
                result.getFiles().add(file);
            }
        }
        if (result.getErrors().isEmpty()) resolveEntrypointAndManifest(result);
        if (result.getErrors().isEmpty()
                && result.getTotalSize() > packageBytes.length * (long) MAX_COMPRESSION_RATIO) {
            result.getErrors().add("package compression ratio exceeds " + MAX_COMPRESSION_RATIO + ":1");
        }
        if (result.getErrors().isEmpty()) {
            result.setChecksum(sha256(packageBytes));
            result.setValid(true);
        }
        return result;
    }

    private void resolveEntrypointAndManifest(InspectedSkillPackage result) {
        List<InspectedSkillPackage.File> candidates = result.getFiles().stream()
                .filter(file -> "SKILL.md".equalsIgnoreCase(file.getPath()))
                .toList();
        long nestedSkillFiles = result.getFiles().stream()
                .filter(file -> file.getPath().toLowerCase(Locale.ROOT).endsWith("/skill.md"))
                .count();
        if (candidates.size() != 1 || nestedSkillFiles > 0) {
            result.getErrors().add("top-level package directory must contain exactly one case-insensitive SKILL.md");
            return;
        }
        InspectedSkillPackage.File entrypoint = candidates.get(0);
        result.setEntrypoint(entrypoint.getPath());
        String markdown = decodeUtf8(entrypoint.getContent(), "SKILL.md", result);
        if (markdown == null) return;
        Map<String, Object> frontmatter = frontmatter(markdown, result);
        if (frontmatter == null) return;
        String name = text(frontmatter.get("name"));
        String description = text(frontmatter.get("description"));
        if (!StringUtils.hasText(name) || name.length() > 64 || !SKILL_NAME.matcher(name).matches()) {
            result.getErrors().add("SKILL.md frontmatter name must be 1-64 lowercase letters, digits or hyphens");
        } else if (!name.equals(result.getPackageRoot())) {
            result.getErrors().add("SKILL.md frontmatter name must match the top-level directory");
        }
        if (!StringUtils.hasText(description) || description.length() > 1024) {
            result.getErrors().add("SKILL.md frontmatter description must be 1-1024 characters");
        }
        result.setSkillName(name);
        result.setDescription(description);
        result.setLicense(text(frontmatter.get("license")));
        result.setCompatibility(text(frontmatter.get("compatibility")));
    }

    private Map<String, Object> frontmatter(String markdown, InspectedSkillPackage result) {
        String normalized = markdown.startsWith("\uFEFF") ? markdown.substring(1) : markdown;
        if (!normalized.startsWith("---\n") && !normalized.startsWith("---\r\n")) {
            result.getErrors().add("SKILL.md must start with YAML frontmatter");
            return null;
        }
        int contentStart = normalized.indexOf('\n') + 1;
        int end = normalized.indexOf("\n---", contentStart);
        if (end < 0 || end - contentStart > MAX_FRONTMATTER_BYTES) {
            result.getErrors().add("SKILL.md YAML frontmatter is missing or too large");
            return null;
        }
        String yamlText = normalized.substring(contentStart, end);
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(10);
            options.setCodePointLimit(MAX_FRONTMATTER_BYTES);
            Object parsed = new Yaml(new SafeConstructor(options)).load(yamlText);
            if (!(parsed instanceof Map<?, ?> map)) {
                result.getErrors().add("SKILL.md YAML frontmatter must be an object");
                return null;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        } catch (RuntimeException ex) {
            result.getErrors().add("SKILL.md YAML frontmatter is invalid: " + safeMessage(ex));
            return null;
        }
    }

    private String validateAndNormalizePath(String rawPath, InspectedSkillPackage result) {
        if (!StringUtils.hasText(rawPath) || rawPath.indexOf('\0') >= 0 || rawPath.indexOf('\\') >= 0) {
            result.getErrors().add("ZIP contains an invalid path");
            return null;
        }
        String value = rawPath.trim();
        if (value.startsWith("/") || WINDOWS_ABSOLUTE.matcher(value).matches()) {
            result.getErrors().add("absolute ZIP path is not allowed: " + rawPath);
            return null;
        }
        for (String segment : value.split("/")) {
            if (segment.isEmpty() && !value.endsWith("/")) {
                result.getErrors().add("empty ZIP path segment is not allowed: " + rawPath);
                return null;
            }
            if ("..".equals(segment) || ".".equals(segment)) {
                result.getErrors().add("path traversal is not allowed: " + rawPath);
                return null;
            }
        }
        try {
            String normalized = Path.of(value).normalize().toString().replace('\\', '/');
            if (!StringUtils.hasText(normalized) || normalized.startsWith("../") || "..".equals(normalized)) {
                result.getErrors().add("path traversal is not allowed: " + rawPath);
                return null;
            }
            return rawPath.endsWith("/") ? normalized + "/" : normalized;
        } catch (InvalidPathException ex) {
            result.getErrors().add("ZIP contains an invalid path: " + rawPath);
            return null;
        }
    }

    private boolean isHardOrSymbolicLink(ZipArchiveEntry entry) {
        for (ZipExtraField field : entry.getExtraFields()) {
            if (field instanceof AsiExtraField asi && asi.isLink()) return true;
        }
        return false;
    }

    private boolean isExecutable(ZipArchiveEntry entry) {
        return (entry.getUnixMode() & 0111) != 0;
    }

    private boolean isExecutableBinary(byte[] content) {
        if (content.length < 4) return false;
        boolean elf = content[0] == 0x7f && content[1] == 'E' && content[2] == 'L' && content[3] == 'F';
        boolean pe = content[0] == 'M' && content[1] == 'Z';
        int magic = ((content[0] & 0xff) << 24) | ((content[1] & 0xff) << 16)
                | ((content[2] & 0xff) << 8) | (content[3] & 0xff);
        boolean macho = Set.of(0xfeedface, 0xfeedfacf, 0xcafebabe, 0xcefaedfe, 0xcffaedfe).contains(magic);
        return elf || pe || macho;
    }

    private void scanContent(InspectedSkillPackage.File file, InspectedSkillPackage result) {
        String lower = file.getPath().toLowerCase(Locale.ROOT);
        if (isNestedArchive(lower)) {
            result.getWarnings().add("nested archive is retained as opaque data and is never extracted: " + file.getPath());
            return;
        }
        if (!isTextMediaType(file.getMediaType())) return;
        String content = decodeUtf8(file.getContent(), file.getPath(), result);
        if (content == null) return;
        if (content.contains("-----BEGIN PRIVATE KEY-----")
                || content.contains("-----BEGIN RSA PRIVATE KEY-----")
                || content.contains("-----BEGIN OPENSSH PRIVATE KEY-----")
                || OPENAI_KEY.matcher(content).find() || AWS_KEY.matcher(content).find()
                || CREDENTIAL_ASSIGNMENT.matcher(content).find()) {
            result.getErrors().add("credential or private-key material detected: " + file.getPath());
        }
        if ((lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts"))
                && (content.contains("http://") || content.contains("https://"))) {
            result.getWarnings().add("script contains a network reference and will require sandbox allowlisting: "
                    + file.getPath());
        }
    }

    private String decodeUtf8(byte[] content, String path, InspectedSkillPackage result) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException ex) {
            result.getErrors().add("text file is not valid UTF-8: " + path);
            return null;
        }
    }

    private byte[] readBounded(InputStream input, String path, InspectedSkillPackage result) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if ((long) output.size() + read > MAX_FILE_BYTES) {
                result.getErrors().add("file exceeds per-file limit: " + path);
                return null;
            }
            if (result.getTotalSize() + output.size() + read > MAX_UNCOMPRESSED_BYTES) {
                result.getErrors().add("uncompressed package exceeds " + MAX_UNCOMPRESSED_BYTES + " bytes");
                return null;
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private InspectedSkillPackage.File file(String path, byte[] content) {
        InspectedSkillPackage.File file = new InspectedSkillPackage.File();
        file.setPath(path);
        file.setContent(content);
        file.setSize(content.length);
        file.setChecksum(sha256(content));
        file.setMediaType(mediaType(path));
        return file;
    }

    private InspectedSkillPackage rejected(String error) {
        InspectedSkillPackage value = new InspectedSkillPackage();
        value.getErrors().add(error);
        return value;
    }

    private String root(String path) {
        String value = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int separator = value.indexOf('/');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private boolean isNestedArchive(String path) {
        return NESTED_ARCHIVE_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private boolean isTextMediaType(String mediaType) {
        return mediaType != null && (mediaType.startsWith("text/")
                || Set.of("application/json", "application/yaml", "application/xml", "text/csv")
                .contains(mediaType));
    }

    private String mediaType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "application/yaml";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "text/javascript";
        if (lower.endsWith(".ts")) return "text/typescript";
        if (lower.endsWith(".html")) return "text/html";
        return "application/octet-stream";
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String text(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? null : String.valueOf(value).trim();
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message.substring(0, Math.min(512, message.length()))
                : ex.getClass().getSimpleName();
    }
}
