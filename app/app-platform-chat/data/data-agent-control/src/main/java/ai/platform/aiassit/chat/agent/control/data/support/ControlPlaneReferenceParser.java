package ai.platform.aiassit.chat.agent.control.data.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser and canonicalizer for versioned control-plane references such as {@code skill://policy/v2}. */
@Component
public class ControlPlaneReferenceParser {

    private static final Pattern REFERENCE = Pattern.compile(
            "^([a-z][a-z0-9+.-]*)://([A-Za-z0-9._-]{1,255})(?:/v([1-9][0-9]*))?$");

    public ParsedReference parse(String reference, String expectedScheme) {
        if (!StringUtils.hasText(reference)) {
            throw new IllegalArgumentException("reference is required");
        }
        Matcher matcher = REFERENCE.matcher(reference.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("reference must use scheme://code[/vN]: " + reference);
        }
        String scheme = matcher.group(1).toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(expectedScheme) && !expectedScheme.equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("reference scheme must be " + expectedScheme + ": " + reference);
        }
        Integer version = matcher.group(3) == null ? null : Integer.valueOf(matcher.group(3));
        return new ParsedReference(scheme, matcher.group(2), version);
    }

    public String freeze(String reference, String expectedScheme, int version) {
        ParsedReference parsed = parse(reference, expectedScheme);
        return parsed.scheme() + "://" + parsed.code() + "/v" + version;
    }

    public record ParsedReference(String scheme, String code, Integer version) {
    }
}
