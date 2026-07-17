package ai.platform.aiassit.conversation.convert;

import ai.platform.aiassit.conversation.dto.conversation.ConversationSessionVO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import org.mapstruct.Mapper;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/7
 */
@Mapper(componentModel = "spring")
public interface IApiResConvert {

    ConversationSessionVO toVO(ConversationSessionDTO dto);

}
