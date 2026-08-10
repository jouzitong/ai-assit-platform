package ai.platform.aiassit.conversation.support;

import org.arthena.framework.common.context.SystemContext;
import org.athena.framework.security.api.model.MutableUserContext;
import org.athena.framework.security.api.model.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRequestContextResolverTest {

    private final ConversationRequestContextResolver resolver = new ConversationRequestContextResolver();

    @AfterEach
    void clearContext() {
        SystemContext.clearUserContext();
    }

    @Test
    void usesServerOwnedScopeWhenTenantClaimIsUnavailable() {
        MutableUserContext userContext = new MutableUserContext();
        userContext.setSubject(new Subject(7L, "memory-test-user", null, "USER"));
        SystemContext.setUserContext(userContext);

        assertThat(resolver.currentTenantId())
                .isEqualTo(ConversationRequestContextResolver.SINGLE_TENANT_SCOPE);
    }
}
