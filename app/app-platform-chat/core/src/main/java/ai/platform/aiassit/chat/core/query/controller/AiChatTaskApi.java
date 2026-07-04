package ai.platform.aiassit.chat.core.query.controller;

import ai.platform.aiassit.chat.core.query.dto.AiChatTaskQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatTaskStatusResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatTaskStopRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/task/chat")
public interface AiChatTaskApi {

    @PostMapping("/status")
    AiChatTaskStatusResponse status(@RequestBody AiChatTaskQueryRequest request);

    @PostMapping("/stop")
    Boolean stop(@RequestBody AiChatTaskStopRequest request);
}
