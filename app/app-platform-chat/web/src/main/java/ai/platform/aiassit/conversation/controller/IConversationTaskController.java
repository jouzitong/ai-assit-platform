package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.task.ConversationTaskQueryRequest;
import ai.platform.aiassit.conversation.dto.task.ConversationTaskStatusResponse;
import ai.platform.aiassit.conversation.dto.task.ConversationTaskStopRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 聊天异步运行任务协议契约。
 *
 * <p>用于查询或停止当前登录用户的聊天运行，任务可通过运行标识、会话编码或轮次编码定位。</p>
 */
@RequestMapping("/api/v1/task/chat")
public interface IConversationTaskController {

    /**
     * 查询指定聊天运行的状态。
     *
     * @param request 任务查询请求体，提供运行、会话或轮次定位信息
     * @return 任务生命周期、执行节点、关联轮次和失败状态
     */
    @PostMapping("/status")
    ConversationTaskStatusResponse status(@RequestBody ConversationTaskQueryRequest request);

    /**
     * 请求停止指定聊天运行。
     *
     * @param request 停止请求体，提供运行、会话或轮次定位信息
     * @return 是否成功提交或确认停止请求
     */
    @PostMapping("/stop")
    Boolean stop(@RequestBody ConversationTaskStopRequest request);
}
