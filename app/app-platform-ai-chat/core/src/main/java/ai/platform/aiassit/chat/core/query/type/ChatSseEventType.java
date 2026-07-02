package ai.platform.aiassit.chat.core.query.type;

/**
 *
 * @author zhouzhitong
 * @since 2026/7/3
 */
public interface ChatSseEventType {

    /**
     * 会话初始化完成。
     * <p>
     * 一般用于返回新建会话的基础信息，例如 sessionId。
     */
    String SESSION_INIT = "session_init";

    /**
     * 会话名称更新。
     * <p>
     * 一般用于 AI 自动生成标题后，通知前端刷新会话名称。
     */
    String SESSION_RENAME = "session_rename";

    /**
     * AI 执行进度初始化。
     * <p>
     * 一般用于返回本次执行的步骤列表、节点信息或初始进度状态。
     */
    String PROGRESS_INIT = "progress_init";

    /**
     * AI 执行进度更新。
     * <p>
     * 一般用于通知某个步骤开始执行、执行中、执行失败、执行成功等状态变化。
     */
    String PROGRESS_UPDATE = "progress_update";

    /**
     * AI 执行进度完成。
     * <p>
     * 表示 AI 主流程已经执行完成，但不一定代表前端页面已经完成渲染。
     */
    String PROGRESS_COMPLETE = "progress_complete";

    /**
     * AI 思考过程摘要。
     * <p>
     * 一般用于返回阶段性思考、分析过程、工具调用摘要等可展示内容。
     */
    String THINKING_SUMMARY = "thinking_summary";

    /**
     * 页面渲染完成。
     * <p>
     * 一般用于返回最终生成的 render page id，前端据此加载或展示页面。
     */
    String RENDER_COMPLETE = "render_complete";

    /**
     * 全局执行完成。
     * <p>
     * 表示本次 SSE 推送流程已经全部结束，前端可以关闭连接或标记任务完成。
     */
    String GLOBAL_COMPLETE = "global_complete";

    /**
     * 全局错误提示。
     * <p>
     * 表示本次执行过程中出现异常或业务错误，前端可以展示错误信息并结束当前流程。
     */
    String GLOBAL_ERROR = "global_error";

}
