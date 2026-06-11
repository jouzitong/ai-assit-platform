package ai.platform.aiassist.service.ai.core.validator;

import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDocument;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import org.arthena.framework.common.constant.ParamBizCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * AI 请求参数基础校验。
 */
@Component
public class AiRequestValidator {

    public void validateChat(ChatRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getMessages())) {
            throw BizException.illegalParam(ParamBizCodeConstant.REQUIRED_MESSAGES);
        }
        for (ChatMessage message : request.getMessages()) {
            if (message == null || message.getRole() == null || !StringUtils.hasText(message.getContent())) {
                throw BizException.illegalParam(ParamBizCodeConstant.INVALID_MESSAGES_ITEM);
            }
        }
    }

    public void validateEmbed(EmbedRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getInputs())) {
            throw BizException.illegalParam(ParamBizCodeConstant.REQUIRED_INPUTS);
        }
        for (String input : request.getInputs()) {
            if (!StringUtils.hasText(input)) {
                throw BizException.illegalParam(ParamBizCodeConstant.INVALID_INPUTS_ITEM);
            }
        }
    }

    public void validateRerank(RerankRequest request) {
        if (request == null || !StringUtils.hasText(request.getQuery()) || CollectionUtils.isEmpty(request.getCandidates())) {
            throw BizException.illegalParam(ParamBizCodeConstant.REQUIRED_QUERY_OR_CANDIDATES);
        }
        for (String candidate : request.getCandidates()) {
            if (!StringUtils.hasText(candidate)) {
                throw BizException.illegalParam(ParamBizCodeConstant.INVALID_CANDIDATES_ITEM);
            }
        }
    }

    public void validateKbUpsert(KbUpsertRequest request) {
        if (request == null || !StringUtils.hasText(request.getKbId()) || CollectionUtils.isEmpty(request.getDocuments())) {
            throw BizException.illegalParam(ParamBizCodeConstant.REQUIRED_KB_ID_OR_DOCUMENTS);
        }
        for (KbDocument document : request.getDocuments()) {
            if (document == null || !StringUtils.hasText(document.getDocumentId()) || !StringUtils.hasText(document.getContent())) {
                throw BizException.illegalParam(ParamBizCodeConstant.INVALID_DOCUMENTS_ITEM);
            }
        }
    }

    public void validateKbDelete(KbDeleteRequest request) {
        if (request == null || !StringUtils.hasText(request.getKbId()) || CollectionUtils.isEmpty(request.getDocumentIds())) {
            throw BizException.illegalParam(ParamBizCodeConstant.REQUIRED_KB_ID_OR_DOCUMENT_IDS);
        }
        for (String documentId : request.getDocumentIds()) {
            if (!StringUtils.hasText(documentId)) {
                throw BizException.illegalParam(ParamBizCodeConstant.INVALID_DOCUMENT_IDS_ITEM);
            }
        }
    }

    public void validateKbSearch(KbSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.getKbId()) || !StringUtils.hasText(request.getQuery())) {
            throw BizException.illegalParam(ParamBizCodeConstant.REQUIRED_KB_ID_OR_QUERY);
        }
        if (request.getTopK() != null && request.getTopK() <= 0) {
            throw BizException.illegalParam(ParamBizCodeConstant.INVALID_TOP_K);
        }
    }
}
