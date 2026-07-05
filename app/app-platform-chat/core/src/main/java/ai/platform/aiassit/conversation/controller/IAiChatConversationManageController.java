package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationPinRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationRenameRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationSearchRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatSessionVO;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationDeleteRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * AI 会话管理接口。
 */
@RequestMapping("/api/v1/chat/conversation")
public interface IAiChatConversationManageController {

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
