package ai.platform.aiassit.chat.core.query.controller;

import ai.platform.aiassit.chat.core.query.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatSessionVO;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationCreateRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDeleteRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDetailRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RequestMapping("/api/v1/ai/chat")
public interface AiChatQueryApi {

    @PostMapping("/query")
    AiChatQueryResponse query(@RequestBody AiChatQueryRequest request);

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter queryStream(@RequestBody AiChatQueryRequest request);

    @PostMapping("/conversation/list")
    List<AiChatSessionVO> list(@RequestBody(required = false) AiChatConversationQueryRequest request);

    @PostMapping("/conversation/detail")
    AiChatConversationDetailResponse detail(@RequestBody AiChatConversationDetailRequest request);

    @PostMapping("/conversation/create")
    AiChatConversationDetailResponse create(@RequestBody(required = false) AiChatConversationCreateRequest request);

    @PostMapping("/conversation/rename")
    AiChatSessionVO renameConversation(@RequestBody AiChatConversationRenameRequest request);

    @PostMapping("/conversation/pin")
    AiChatSessionVO pinConversation(@RequestBody AiChatConversationPinRequest request);

    @PostMapping("/conversation/delete")
    Boolean deleteConversation(@RequestBody AiChatConversationDeleteRequest request);
}
