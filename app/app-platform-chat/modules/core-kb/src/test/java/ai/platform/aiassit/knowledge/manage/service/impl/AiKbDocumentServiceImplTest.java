package ai.platform.aiassit.knowledge.manage.service.impl;

import ai.platform.aiassit.knowledge.manage.entity.document.AiKbDocumentEntity;
import ai.platform.aiassit.knowledge.manage.entity.document.req.AiKbDocumentQueryRequest;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiKbDocumentServiceImplTest {

    @Test
    void exactDocumentLookupMatchesOnlyRequestedDocumentInSharedKnowledgeBase() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AiKbDocumentEntity.class);
        AiKbDocumentServiceImpl service = new AiKbDocumentServiceImpl(null);
        AiKbDocumentQueryRequest request = new AiKbDocumentQueryRequest();
        request.setKbCode("shared-kb");
        request.setDocumentCode("target-document");

        QueryWrapper<?> wrapper = service.buildQuery(request);

        assertThat(wrapper.getSqlSegment())
                .contains("kb_code =", "document_code =");
        assertThat(wrapper.getParamNameValuePairs())
                .containsValue("shared-kb")
                .containsValue("target-document");

        List<AiKbDocumentEntity> documents = List.of(
                document("shared-kb", "sibling-document"),
                document("shared-kb", "target-document"));
        List<AiKbDocumentEntity> matchedDocuments = documents.stream()
                .filter(document -> wrapper.getParamNameValuePairs().containsValue(document.getKbCode()))
                .filter(document -> wrapper.getParamNameValuePairs().containsValue(document.getDocumentCode()))
                .toList();

        assertThat(matchedDocuments)
                .extracting(AiKbDocumentEntity::getDocumentCode)
                .containsExactly("target-document");
    }

    private static AiKbDocumentEntity document(String kbCode, String documentCode) {
        AiKbDocumentEntity document = new AiKbDocumentEntity();
        document.setKbCode(kbCode);
        document.setDocumentCode(documentCode);
        return document;
    }
}
