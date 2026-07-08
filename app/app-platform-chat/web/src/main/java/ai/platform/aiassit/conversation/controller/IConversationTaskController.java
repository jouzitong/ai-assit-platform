package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.task.ConversationTaskQueryRequest;
import ai.platform.aiassit.conversation.dto.task.ConversationTaskStatusResponse;
import ai.platform.aiassit.conversation.dto.task.ConversationTaskStopRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/task/chat")
public interface IConversationTaskController {

    @PostMapping("/status")
    ConversationTaskStatusResponse status(@RequestBody ConversationTaskQueryRequest request);

    @PostMapping("/stop")
    Boolean stop(@RequestBody ConversationTaskStopRequest request);
}
