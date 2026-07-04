package ai.platform.aiassist.service.ai.api.stream;

/**
 * 流式对话回调观察者。
 */
public interface ChatStreamObserver {

    /**
     * 接收一个流式增量分片。
     *
     * @param chunk 当前分片数据
     */
    void onChunk(ChatChunk chunk);

    /**
     * 流式输出正常结束回调。
     */
    void onComplete();

    /**
     * 流式输出异常回调。
     *
     * @param throwable 异常信息
     */
    void onError(Throwable throwable);
}
