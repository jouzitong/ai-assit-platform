package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.VirtualCatalogGateway;
import ai.platform.aiassit.data.virtualization.api.VirtualQueryGateway;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeResponse;
import ai.platform.aiassit.db.engine.core.service.DbQueryService;
import org.springframework.stereotype.Service;

/**
 * DbQueryApi 的兼容应用门面。
 *
 * <p>这里只负责旧协议翻译和响应整形；查询、目录、路由与物理执行全部进入虚拟表内核。</p>
 */
@Service
public class DbQueryCompatibilityFacade implements DbQueryService {

    private final VirtualQueryGateway queryGateway;
    private final LegacyRequestTranslator translator;
    private final LegacyResponseAssembler responseAssembler;
    private final TreeAssembler treeAssembler;
    private final PivotAssembler pivotAssembler;

    public DbQueryCompatibilityFacade(
            VirtualQueryGateway queryGateway,
            VirtualCatalogGateway catalogGateway
    ) {
        this.queryGateway = queryGateway;
        this.translator = new LegacyRequestTranslator(catalogGateway);
        this.responseAssembler = new LegacyResponseAssembler();
        this.treeAssembler = new TreeAssembler();
        this.pivotAssembler = new PivotAssembler();
    }

    @Override
    public DbQueryGetResponse queryGet(DbQueryGetRequest request) {
        LegacyRequestTranslator.Translation translated = translator.translateGet(request);
        VirtualQueryResponse result = queryGateway.query(translated.request());
        return responseAssembler.assembleGet(result, translated.outputFields());
    }

    @Override
    public DbQueryListResponse queryList(DbQueryListRequest request) {
        LegacyRequestTranslator.Translation translated = translator.translateList(request);
        VirtualQueryResponse result = queryGateway.query(translated.request());
        return responseAssembler.assembleList(
                result,
                translated.page(),
                translated.pageSize(),
                translated.outputFields()
        );
    }

    @Override
    public DbQueryCountResponse queryCount(DbQueryCountRequest request) {
        LegacyRequestTranslator.Translation translated = translator.translateCount(request);
        VirtualQueryResponse result = queryGateway.query(translated.request());
        return responseAssembler.assembleCount(
                result,
                translated.page(),
                translated.pageSize(),
                translated.plainCount()
        );
    }

    @Override
    public DbQueryAggregateResponse queryAggregate(DbQueryAggregateRequest request) {
        LegacyRequestTranslator.Translation translated = translator.translateAggregate(request);
        VirtualQueryResponse result = queryGateway.query(translated.request());
        return responseAssembler.assembleAggregate(result, translated.page(), translated.pageSize());
    }

    @Override
    public DbQueryTreeResponse queryTree(DbQueryTreeRequest request) {
        LegacyRequestTranslator.Translation translated = translator.translateTree(request);
        VirtualQueryResponse result = queryGateway.query(translated.request());
        requireFullyMaterialized(result, "query.tree");
        return treeAssembler.assemble(result, request);
    }

    @Override
    public DbQueryPivotResponse queryPivot(DbQueryPivotRequest request) {
        LegacyRequestTranslator.Translation translated = translator.translatePivot(request);
        VirtualQueryResponse result = queryGateway.query(translated.request());
        requireFullyMaterialized(result, "query.pivot");
        return pivotAssembler.assemble(result, request);
    }

    private void requireFullyMaterialized(VirtualQueryResponse response, String operation) {
        long total = response == null || response.getTotal() == null ? 0L : response.getTotal();
        int materialized = response == null || response.getRecords() == null ? 0 : response.getRecords().size();
        if (total != materialized) {
            throw new LegacyQueryCompatibilityException(
                    "PLAN_EXACTNESS_UNPROVABLE",
                    operation + " 需要完整结果集，当前预算仅物化 " + materialized + "/" + total + " 条"
            );
        }
    }
}
