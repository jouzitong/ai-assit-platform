package ai.platform.aiassit.conversation.workflow.capability;

import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeCapabilityConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;

/**
 * 节点 prompt 上下文增强能力。
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
public interface PromptContextCapability extends Capability {

    PromptContextResult load(ConversationRuntimeContext context,
                             WorkflowNodeConfig nodeConfig,
                             WorkflowNodeCapabilityConfig capabilityConfig);
}
