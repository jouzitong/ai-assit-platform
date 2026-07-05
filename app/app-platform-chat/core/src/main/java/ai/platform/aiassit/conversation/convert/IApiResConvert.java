package ai.platform.aiassit.conversation.convert;

import ai.platform.aiassit.conversation.dto.conversation.AiChatSessionVO;
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
