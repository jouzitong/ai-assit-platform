package ai.platform.aiassit.user.system.settings.data.controller;

import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingImportResultDTO;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingTransferDocument;
import ai.platform.aiassit.user.system.settings.data.entity.req.SystemSettingExportRequest;
import ai.platform.aiassit.user.system.settings.data.entity.req.SystemSettingQueryRequest;
import ai.platform.aiassit.user.system.settings.data.service.SystemSettingService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 系统运行参数的通用 CRUD 与 JSON 迁移接口。
 *
 * <p>复用 {@link BaseController} 维护配置键、值和描述；导入导出接口用于跨环境迁移配置，敏感值的展示和持久化策略由服务层统一控制。</p>
 */
@RestController
@RequestMapping("/api/v1/system-settings")
public class SystemSettingController
        extends BaseController<SystemSettingDTO, SystemSettingQueryRequest, SystemSettingService> {

    private final SystemSettingService service;

    public SystemSettingController(SystemSettingService service) {
        this.service = service;
    }

    @Override
    protected SystemSettingService service() {
        return service;
    }

    /**
     * 从 JSON 文件批量导入或更新系统参数。
     *
     * @param file 包含系统参数键值和元信息的 JSON 文件
     * @return 包装后的导入结果，包含新增、更新和失败明细
     * @throws IOException 读取上传文件失败时抛出
     */
    @PostMapping("/import-json")
    public R<SystemSettingImportResultDTO> importJson(@RequestParam("file") MultipartFile file) throws IOException {
        return R.ok(service.importJsonFile(file));
    }

    /**
     * 按可选条件导出可迁移的系统参数文档。
     *
     * @param request 可选导出请求体，包含配置键、分组或环境筛选条件
     * @return 包装后的配置迁移文档列表
     */
    @PostMapping("/export-json")
    public R<List<SystemSettingTransferDocument>> exportJson(
            @RequestBody(required = false) SystemSettingExportRequest request) {
        return R.ok(service.exportJson(request));
    }
}
