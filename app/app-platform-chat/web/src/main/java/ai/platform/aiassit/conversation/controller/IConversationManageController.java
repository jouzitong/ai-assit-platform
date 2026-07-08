package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.conversation.ConversationPinRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationRenameRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationSearchRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationSessionVO;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDeleteRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * AI 会话管理接口。
 */
@RequestMapping("/api/v1/chat/conversation")
public interface IConversationManageController {

    /**
     * 查询会话列表。
     */
    @PostMapping("/list")
    List<ConversationSessionVO> list(@RequestBody(required = false) ConversationQueryRequest request);

    /**
     * 搜索会话。
     */
    @PostMapping("/search")
    List<ConversationSessionVO> search(@RequestBody ConversationSearchRequest request);

    /**
     * 重命名会话。
     */
    @PostMapping("/rename")
    ConversationSessionVO rename(@RequestBody ConversationRenameRequest request);

    /**
     * 置顶或取消置顶会话。
     */
    @PostMapping("/pin")
    ConversationSessionVO pin(@RequestBody ConversationPinRequest request);

    /**
     * 删除会话。
     */
    @PostMapping("/delete")
    Boolean delete(@RequestBody ConversationDeleteRequest request);
}
