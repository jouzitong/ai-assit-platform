package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import ai.platform.aiassist.service.ai.api.enums.MessageRole;

import java.io.Serializable;



@Data
public class ChatMessage implements Serializable {

    /** 消息角色（system/user/assistant/tool） */
    private MessageRole role;
    /** 消息内容 */
    private String content;
    /** 可选名称（tool 名称或消息别名） */
    private String name;
}
