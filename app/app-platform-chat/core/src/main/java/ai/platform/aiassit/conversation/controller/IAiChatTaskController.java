package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.task.AiChatTaskQueryRequest;
import ai.platform.aiassit.conversation.dto.task.AiChatTaskStatusResponse;
import ai.platform.aiassit.conversation.dto.task.AiChatTaskStopRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/task/chat")
public interface IAiChatTaskController {

    @PostMapping("/status")
    AiChatTaskStatusResponse status(@RequestBody AiChatTaskQueryRequest request);

    @PostMapping("/stop")
    Boolean stop(@RequestBody AiChatTaskStopRequest request);
}
