package ai.platform.aiassit.data.virtualization.core.catalog;

/** 虚拟实体稳定命名规则，供初始化入口统一复用。 */
public final class VirtualEntityNaming {
    private static final int MAX_CODE_LENGTH = 64;

    private VirtualEntityNaming() {
    }

    public static String fromPhysicalTable(String sourceKey, String tableName) {
        String raw = text(sourceKey) + "_" + text(tableName);
        StringBuilder normalized = new StringBuilder(raw.length());
        boolean previousUnderscore = false;
        for (char character : raw.toCharArray()) {
            boolean accepted = Character.isLetterOrDigit(character) || character == '_';
            char value = accepted ? character : '_';
            if (value == '_' && previousUnderscore) {
                continue;
            }
            normalized.append(value);
            previousUnderscore = value == '_';
        }

        String code = normalized.toString();
        if (code.isBlank()) {
            code = "virtual_table";
        }
        if (!Character.isLetter(code.charAt(0))) {
            code = "v_" + code;
        }
        if (code.length() > MAX_CODE_LENGTH) {
            code = code.substring(0, MAX_CODE_LENGTH);
        }
        while (code.endsWith("_")) {
            code = code.substring(0, code.length() - 1);
        }
        return code.length() < 2 ? code + "_table" : code;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
