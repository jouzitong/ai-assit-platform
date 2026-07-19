package ai.platform.aiassit.db.engine.api.constant;

/** DB Engine 运行时系统参数 Key。 */
public final class DbEngineSystemSettingKeys {

    /**
     * DB Engine 默认知识库编码。
     *
     * <p>用途：指定物理表与虚拟表知识文档保存、预览及同步所使用的知识库。</p>
     * <p>类型：STRING；值必须是 {@code ai_kb_store.kb_code} 中的有效编码。</p>
     * <p>要求：必填；缺失、停用或空值时拒绝知识文档相关操作。</p>
     * <p>敏感性：不包含敏感信息，可以在管理页面显示。</p>
     * <p>生效：每次加载知识库配置或执行初始化时实时读取，无需重启。</p>
     */
    public static final String KNOWLEDGE_BASE_CODE = "dbEngine.kb.kbId";

    private DbEngineSystemSettingKeys() {
    }
}
