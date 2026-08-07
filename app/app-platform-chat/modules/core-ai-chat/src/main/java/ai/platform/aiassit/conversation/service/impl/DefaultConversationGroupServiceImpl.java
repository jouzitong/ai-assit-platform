package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationGroupDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import ai.platform.aiassit.conversation.data.service.ConversationGroupDataService;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupAssignRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupCreateRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupDeleteRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupRenameRequest;
import ai.platform.aiassit.conversation.service.ConversationGroupService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 会话分组应用服务实现。 */
@Service
public class DefaultConversationGroupServiceImpl implements ConversationGroupService {

    private static final int MAX_GROUP_NAME_LENGTH = 128;

    private final ConversationGroupDataService groupDataService;
    private final ConversationSessionService sessionService;

    public DefaultConversationGroupServiceImpl(ConversationGroupDataService groupDataService,
                                              ConversationSessionService sessionService) {
        this.groupDataService = groupDataService;
        this.sessionService = sessionService;
    }

    @Override
    public List<ConversationGroupDTO> listGroups(Long userId) {
        return groupDataService.listByUserId(userId);
    }

    @Override
    public ConversationGroupDTO createGroup(Long userId, ConversationGroupCreateRequest request) {
        ConversationGroupDTO group = new ConversationGroupDTO();
        group.setGroupCode(generateGroupCode());
        group.setUserId(userId);
        group.setGroupName(validateGroupName(request == null ? null : request.getGroupName()));
        return groupDataService.add(group);
    }

    @Override
    public ConversationGroupDTO renameGroup(Long userId, ConversationGroupRenameRequest request) {
        String groupCode = requireGroupCode(request == null ? null : request.getGroupCode());
        ConversationGroupDTO group = loadGroup(userId, groupCode);
        ConversationGroupDTO update = new ConversationGroupDTO();
        update.setGroupName(validateGroupName(request.getGroupName()));
        return groupDataService.edit(group.getId(), update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteGroup(Long userId, ConversationGroupDeleteRequest request) {
        String groupCode = requireGroupCode(request == null ? null : request.getGroupCode());
        ConversationGroupDTO group = loadGroup(userId, groupCode);
        sessionService.clearGroupCodeByUserAndGroup(userId, groupCode);
        return groupDataService.delete(group.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationSessionDTO assignSession(Long userId, ConversationGroupAssignRequest request) {
        String sessionCode = requireSessionCode(request == null ? null : request.getSessionCode());
        String groupCode = normalizeOptionalCode(request == null ? null : request.getGroupCode());
        ConversationSessionDTO session = loadSession(userId, sessionCode);

        if (session.getBusinessType() == ConversationBusinessType.PAGE_ASSISTANT && groupCode != null) {
            throw BizException.of(AiChatBizCodeConstant.GROUP_ASSIGNMENT_NOT_ALLOWED, sessionCode);
        }
        if (groupCode != null) {
            loadGroup(userId, groupCode);
        }
        if (Objects.equals(normalizeOptionalCode(session.getGroupCode()), groupCode)) {
            return session;
        }
        if (sessionService.updateGroupCode(userId, sessionCode, groupCode) == 0) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND, sessionCode);
        }
        return loadSession(userId, sessionCode);
    }

    @Override
    public String validateNewSessionGroup(Long userId,
                                          String groupCode,
                                          ConversationBusinessType businessType) {
        String normalized = normalizeOptionalCode(groupCode);
        if (normalized == null) {
            return null;
        }
        if (businessType == ConversationBusinessType.PAGE_ASSISTANT) {
            throw BizException.of(AiChatBizCodeConstant.GROUP_ASSIGNMENT_NOT_ALLOWED, normalized);
        }
        loadGroup(userId, normalized);
        return normalized;
    }

    @Override
    public void validateExistingSessionGroup(Long userId,
                                             ConversationSessionDTO session,
                                             String requestedGroupCode,
                                             ConversationBusinessType businessType) {
        String requested = normalizeOptionalCode(requestedGroupCode);
        if (requested == null) {
            return;
        }
        if (businessType == ConversationBusinessType.PAGE_ASSISTANT) {
            throw BizException.of(AiChatBizCodeConstant.GROUP_ASSIGNMENT_NOT_ALLOWED,
                    session == null ? requested : session.getSessionCode());
        }
        loadGroup(userId, requested);
        if (!Objects.equals(normalizeOptionalCode(session == null ? null : session.getGroupCode()), requested)) {
            throw BizException.of(AiChatBizCodeConstant.GROUP_ASSIGNMENT_NOT_ALLOWED,
                    session == null ? requested : session.getSessionCode());
        }
    }

    private ConversationGroupDTO loadGroup(Long userId, String groupCode) {
        ConversationGroupDTO group = groupDataService.getByUserIdAndCode(userId, groupCode);
        if (group == null) {
            throw BizException.of(AiChatBizCodeConstant.GROUP_NOT_FOUND, groupCode);
        }
        return group;
    }

    private ConversationSessionDTO loadSession(Long userId, String sessionCode) {
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setUserId(userId);
        query.setSessionCode(sessionCode);
        ConversationSessionDTO session = sessionService.get(query);
        if (session == null) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND, sessionCode);
        }
        return session;
    }

    private String requireGroupCode(String groupCode) {
        String normalized = normalizeOptionalCode(groupCode);
        if (normalized == null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_GROUP_CODE);
        }
        return normalized;
    }

    private String requireSessionCode(String sessionCode) {
        if (!StringUtils.hasText(sessionCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_SESSION_CODE);
        }
        return sessionCode.trim();
    }

    private String validateGroupName(String groupName) {
        if (!StringUtils.hasText(groupName)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_GROUP_NAME);
        }
        String normalized = groupName.trim();
        if (normalized.length() > MAX_GROUP_NAME_LENGTH) {
            throw BizException.illegalParam(AiChatBizCodeConstant.INVALID_GROUP_NAME, MAX_GROUP_NAME_LENGTH);
        }
        return normalized;
    }

    private String normalizeOptionalCode(String code) {
        return StringUtils.hasText(code) ? code.trim() : null;
    }

    private String generateGroupCode() {
        return "group-" + UUID.randomUUID().toString().replace("-", "");
    }
}
