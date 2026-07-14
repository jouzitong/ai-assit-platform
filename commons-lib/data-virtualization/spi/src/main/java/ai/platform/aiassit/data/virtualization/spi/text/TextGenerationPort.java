package ai.platform.aiassit.data.virtualization.spi.text;

public interface TextGenerationPort {

    TextGenerationResult generate(TextGenerationCommand command);
}
