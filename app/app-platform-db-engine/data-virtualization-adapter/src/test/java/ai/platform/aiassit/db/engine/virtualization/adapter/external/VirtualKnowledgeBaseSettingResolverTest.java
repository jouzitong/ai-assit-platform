package ai.platform.aiassit.db.engine.virtualization.adapter.external;

import ai.platform.aiassit.db.engine.api.constant.DbEngineSystemSettingKeys;
import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.web.vo.R;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VirtualKnowledgeBaseSettingResolverTest {

    private final SystemSettingInternalApi systemSettingInternalApi = mock(SystemSettingInternalApi.class);
    private final VirtualKnowledgeBaseSettingResolver resolver =
            new VirtualKnowledgeBaseSettingResolver(systemSettingInternalApi);

    @Test
    void resolvesConfiguredKnowledgeBaseCode() {
        when(systemSettingInternalApi.queryValueByKey(DbEngineSystemSettingKeys.KNOWLEDGE_BASE_CODE))
                .thenReturn(R.ok(" data-semantic-catalog "));

        assertThat(resolver.resolve()).isEqualTo("data-semantic-catalog");
    }

    @Test
    void rejectsMissingKnowledgeBaseSetting() {
        when(systemSettingInternalApi.queryValueByKey(DbEngineSystemSettingKeys.KNOWLEDGE_BASE_CODE))
                .thenReturn(R.ok(null));

        assertThatThrownBy(resolver::resolve).isInstanceOf(BizException.class);
    }
}
