package ai.platform.aiassit.db.engine.virtualization.adapter.service;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;
import ai.platform.aiassit.data.virtualization.api.exception.VirtualDataRuntimeException;
import ai.platform.aiassit.db.engine.api.constant.DataPreviewErrorCode;
import org.athena.framework.security.api.model.MutableUserContext;
import org.athena.framework.security.api.model.Subject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedDataPreviewAccessPolicyTest {

    private final AuthenticatedDataPreviewAccessPolicy policy = new AuthenticatedDataPreviewAccessPolicy();

    @Test
    void rejectsMissingSystemUserContext() {
        DataPreviewAccessPolicy.AccessRequest request = new DataPreviewAccessPolicy.AccessRequest(
                null,
                catalog(),
                Set.of("id")
        );

        assertThatThrownBy(() -> policy.authorize(request))
                .isInstanceOfSatisfying(VirtualDataRuntimeException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(DataPreviewErrorCode.AUTH_REQUIRED));
    }

    @Test
    void allowsOnlyAlreadyValidatedRequestedFieldsForAuthenticatedContext() {
        MutableUserContext userContext = new MutableUserContext();
        userContext.setSubject(new Subject(7L, "preview-user", "default", "USER"));
        DataPreviewAccessPolicy.AccessDecision decision = policy.authorize(
                new DataPreviewAccessPolicy.AccessRequest(userContext, catalog(), Set.of("id", "name"))
        );

        assertThat(decision.allowedFields()).containsExactlyInAnyOrder("id", "name");
        assertThat(decision.enforcedRowFilter()).isNull();
    }

    @Test
    void rejectsContextWithoutSubjectOrUserId() {
        MutableUserContext withoutSubject = new MutableUserContext();
        assertThatThrownBy(() -> policy.authorize(
                new DataPreviewAccessPolicy.AccessRequest(withoutSubject, catalog(), Set.of("id"))
        )).isInstanceOfSatisfying(VirtualDataRuntimeException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(DataPreviewErrorCode.AUTH_REQUIRED));

        MutableUserContext withoutUserId = new MutableUserContext();
        withoutUserId.setSubject(new Subject(null, "preview-user", "default", "USER"));
        assertThatThrownBy(() -> policy.authorize(
                new DataPreviewAccessPolicy.AccessRequest(withoutUserId, catalog(), Set.of("id"))
        )).isInstanceOfSatisfying(VirtualDataRuntimeException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(DataPreviewErrorCode.AUTH_REQUIRED));
    }

    private VirtualCatalogDescriptor catalog() {
        return new VirtualCatalogDescriptor(
                "orders",
                17L,
                List.of(
                        new VirtualCatalogDescriptor.Field("id", true, true),
                        new VirtualCatalogDescriptor.Field("name", false, true)
                ),
                List.of()
        );
    }
}
