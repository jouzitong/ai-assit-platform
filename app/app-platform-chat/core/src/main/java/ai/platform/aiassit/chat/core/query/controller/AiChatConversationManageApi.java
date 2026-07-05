package ai.platform.aiassit.chat.core.query.controller;

import ai.platform.aiassit.chat.core.query.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationSearchRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatSessionVO;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDeleteRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * AI 会话管理接口。
 */
@RequestMapping("/api/v1/chat/conversation")
public interface AiChatConversationManageApi {

    /**
     * 查询会话列表。
     */
    @PostMapping("/list")
    List<AiChatSessionVO> list(@RequestBody(required = false) AiChatConversationQueryRequest request);

    /**
     * 搜索会话。
     */
    @PostMapping("/search")
    List<AiChatSessionVO> search(@RequestBody AiChatConversationSearchRequest request);

    /**
     * 重命名会话。
     */
    @PostMapping("/rename")
    AiChatSessionVO rename(@RequestBody AiChatConversationRenameRequest request);

    /**
     * 置顶或取消置顶会话。
     */
    @PostMapping("/pin")
    AiChatSessionVO pin(@RequestBody AiChatConversationPinRequest request);

    /**
     * 删除会话。
     */
    @PostMapping("/delete")
    Boolean delete(@RequestBody AiChatConversationDeleteRequest request);
}
