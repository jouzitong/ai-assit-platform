package ai.platform.aiassit.chat.core.query.convert;

import ai.platform.aiassit.chat.core.query.dto.AiChatSessionVO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import org.mapstruct.Mapper;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/7
 */
@Mapper(componentModel = "spring")
public interface IApiResConvert {

    AiChatSessionVO toVO(AiChatSessionDTO dto);

}
