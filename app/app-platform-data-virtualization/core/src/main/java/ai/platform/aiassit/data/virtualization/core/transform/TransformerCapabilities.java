package ai.platform.aiassit.data.virtualization.core.transform;

public record TransformerCapabilities(
        boolean readable,
        boolean writable,
        boolean partialWrite,
        boolean predicatePushdown,
        boolean sortPushdown,
        boolean aggregatePushdown,
        boolean applicationOnly
) {
    public static TransformerCapabilities identity() {
        return new TransformerCapabilities(true, true, true, true, true, true, false);
    }

    public static TransformerCapabilities readOnlyLocal() {
        return new TransformerCapabilities(true, false, false, false, false, false, true);
    }
}
