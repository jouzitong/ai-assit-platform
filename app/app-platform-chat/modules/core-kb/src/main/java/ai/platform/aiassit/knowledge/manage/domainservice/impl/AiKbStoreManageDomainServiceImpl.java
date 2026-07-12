package ai.platform.aiassit.knowledge.manage.domainservice.impl;

import ai.platform.aiassit.execution.service.KnowledgeClientConfigService;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKbStoreManageDomainService;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.entity.req.AiKbStoreQueryRequest;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
import ai.platform.aiassit.knowledge.manage.vo.AiKbAuthVO;
import ai.platform.aiassit.knowledge.manage.vo.AiKbStoreVO;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.AiKbAuthConfig;
import ai.platform.aiassit.service.ai.api.enums.AiKbAuthType;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link AiKbStoreManageDomainService} 默认实现。 */
@Service
public class AiKbStoreManageDomainServiceImpl implements AiKbStoreManageDomainService {

    private final AiKbStoreService storeService;
    private final KnowledgeClientConfigService knowledgeClientConfigService;

    public AiKbStoreManageDomainServiceImpl(AiKbStoreService storeService,
                                            KnowledgeClientConfigService knowledgeClientConfigService) {
        this.storeService = storeService;
        this.knowledgeClientConfigService = knowledgeClientConfigService;
    }

    @Override
    public PageResultVO<AiKbStoreVO> page(AiKbStoreQueryRequest request) {
        PageResultVO<AiKbStoreDTO> result = storeService.page(request == null ? new AiKbStoreQueryRequest() : request);
        List<AiKbStoreVO> list = result.getList().stream().map(this::toVO).toList();
        return PageResultVO.of(list, result.getPageInfo());
    }

    @Override
    public AiKbStoreVO get(Long id) {
        return toVO(requireStore(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbStoreVO add(AiKbStoreDTO dto) {
        AiKbStoreDTO target = merge(null, dto, true, true);
        validate(target);
        return toVO(storeService.add(target));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbStoreVO update(Long id, AiKbStoreDTO dto) {
        AiKbStoreDTO target = merge(requireStore(id), dto, true, true);
        validate(target);
        return toVO(storeService.update(id, target));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbStoreVO edit(Long id, AiKbStoreDTO dto) {
        AiKbStoreDTO current = requireStore(id);
        boolean clientChanged = hasClientKey(dto == null ? null : dto.getExtJson());
        AiKbStoreDTO target = merge(current, dto, false, clientChanged);
        if (target.getAuth() != null) {
            validateAuth(target.getAuth(), target.getClientType());
        }
        return toVO(storeService.edit(id, target));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        return storeService.delete(id);
    }

    private AiKbStoreDTO merge(AiKbStoreDTO current, AiKbStoreDTO incoming,
                                boolean replaceNulls, boolean hydrateAuthFromClient) {
        AiKbStoreDTO source = incoming == null ? new AiKbStoreDTO() : incoming;
        AiKbStoreDTO target = new AiKbStoreDTO();
        target.setId(current == null ? source.getId() : current.getId());
        target.setKbCode(choose(trimToNull(source.getKbCode()), current == null ? null : current.getKbCode(), replaceNulls));
        target.setKbName(choose(trimToNull(source.getKbName()), current == null ? null : current.getKbName(), replaceNulls));
        target.setClientType(choose(source.getClientType(), current == null ? null : current.getClientType(), replaceNulls));
        target.setProviderKbId(choose(trimToNull(source.getProviderKbId()), current == null ? null : current.getProviderKbId(), replaceNulls));
        target.setEnabled(choose(source.getEnabled(), current == null ? null : current.getEnabled(), replaceNulls));
        target.setTags(choose(copyList(source.getTags()), current == null ? null : copyList(current.getTags()), replaceNulls));
        target.setUrl(choose(trimToNull(source.getUrl()), current == null ? null : current.getUrl(), replaceNulls));
        target.setExtJson(choose(copyMap(source.getExtJson()), current == null ? null : copyMap(current.getExtJson()), replaceNulls));

        AiKbAuthConfig configuredAuth = hydrateAuthFromClient ? resolveClientAuth(target.getExtJson()) : null;
        target.setAuth(configuredAuth != null
                ? configuredAuth
                : mergeAuth(source.getAuth(), current == null ? null : current.getAuth()));
        return target;
    }

    private AiKbAuthConfig resolveClientAuth(Map<String, Object> extJson) {
        if (!hasClientKey(extJson)) {
            return null;
        }
        Object value = extJson.get(KnowledgeClientConfigService.CLIENT_KEY_EXT);
        return knowledgeClientConfigService.resolveAuth(String.valueOf(value));
    }

    private boolean hasClientKey(Map<String, Object> extJson) {
        Object value = extJson == null ? null : extJson.get(KnowledgeClientConfigService.CLIENT_KEY_EXT);
        return value instanceof String key && StringUtils.hasText(key);
    }

    private AiKbAuthConfig mergeAuth(AiKbAuthConfig incoming, AiKbAuthConfig current) {
        if (incoming == null) {
            return copyAuth(current);
        }
        AiKbAuthConfig target = new AiKbAuthConfig();
        target.setType(incoming.getType() == null ? current == null ? null : current.getType() : incoming.getType());
        target.setApiKey(choose(trimToNull(incoming.getApiKey()), current == null ? null : current.getApiKey(), false));
        target.setAccessKeyId(choose(trimToNull(incoming.getAccessKeyId()), current == null ? null : current.getAccessKeyId(), false));
        target.setAccessKeySecret(choose(trimToNull(incoming.getAccessKeySecret()), current == null ? null : current.getAccessKeySecret(), false));
        return target;
    }

    private AiKbAuthConfig copyAuth(AiKbAuthConfig source) {
        if (source == null) {
            return null;
        }
        AiKbAuthConfig target = new AiKbAuthConfig();
        target.setType(source.getType());
        target.setApiKey(source.getApiKey());
        target.setAccessKeyId(source.getAccessKeyId());
        target.setAccessKeySecret(source.getAccessKeySecret());
        return target;
    }

    private void validate(AiKbStoreDTO dto) {
        if (!StringUtils.hasText(dto.getKbCode())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_ID);
        }
        if (!StringUtils.hasText(dto.getKbName())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }
        if (dto.getClientType() == null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KNOWLEDGE_CLIENT_TYPE);
        }
        validateAuth(dto.getAuth(), dto.getClientType());
    }

    private void validateAuth(AiKbAuthConfig auth, AiKnowledgeClientType clientType) {
        if (auth == null || auth.getType() == null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_AUTH);
        }
        if (auth.getType() == AiKbAuthType.BEARER && !StringUtils.hasText(auth.getApiKey())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_API_KEY);
        }
        if (auth.getType() == AiKbAuthType.ALIYUN_AKSK
                && (!StringUtils.hasText(auth.getAccessKeyId()) || !StringUtils.hasText(auth.getAccessKeySecret()))) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_AUTH);
        }
        if (clientType == AiKnowledgeClientType.RAGFLOW && auth.getType() != AiKbAuthType.BEARER) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_AUTH);
        }
        if (clientType == AiKnowledgeClientType.BAILIAN && auth.getType() != AiKbAuthType.ALIYUN_AKSK) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_AUTH);
        }
    }

    private AiKbStoreDTO requireStore(Long id) {
        AiKbStoreDTO store = storeService.get(id);
        if (store == null) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, id);
        }
        return store;
    }

    private AiKbStoreVO toVO(AiKbStoreDTO source) {
        AiKbStoreVO target = new AiKbStoreVO();
        BeanUtils.copyProperties(source, target, "auth");
        target.setAuth(toAuthVO(source.getAuth()));
        return target;
    }

    private AiKbAuthVO toAuthVO(AiKbAuthConfig source) {
        if (source == null) {
            return null;
        }
        AiKbAuthVO target = new AiKbAuthVO();
        target.setType(source.getType());
        target.setApiKeyMasked(mask(source.getApiKey()));
        target.setAccessKeyIdMasked(mask(source.getAccessKeyId()));
        target.setAccessKeySecretMasked(mask(source.getAccessKeySecret()));
        return target;
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value) || value.length() <= 12) {
            return StringUtils.hasText(value) ? "****" : null;
        }
        return value.substring(0, 8) + "****" + value.substring(value.length() - 4);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    private List<String> copyList(List<String> source) {
        return source == null ? null : List.copyOf(source);
    }

    private <T> T choose(T incoming, T current, boolean replaceNulls) {
        return replaceNulls ? incoming : incoming != null ? incoming : current;
    }
}
