package ai.platform.aiassit.data.virtualization.spi.text;

public record TextGenerationCommand(
        String systemPrompt,
        String userPrompt,
        String scene,
        int maxTokens,
        double temperature
) {
}
