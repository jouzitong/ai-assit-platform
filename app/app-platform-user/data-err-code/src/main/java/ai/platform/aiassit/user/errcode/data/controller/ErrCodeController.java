package ai.platform.aiassit.user.errcode.data.controller;

import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeDTO;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeResolveDTO;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeUpsertRequest;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeUpsertResultDTO;
import ai.platform.aiassit.user.errcode.data.entity.req.ErrCodeQueryRequest;
import ai.platform.aiassit.user.errcode.data.service.ErrCodeService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 平台错误码目录、解析与 JSON 迁移接口。
 *
 * <p>复用 {@link BaseController} 维护错误码基础定义，并提供按语言解析、批量导入和导出能力，使服务端异常能映射为稳定的用户可读信息。</p>
 */
@RestController
@RequestMapping("/api/v1/err-code")
public class ErrCodeController extends BaseController<ErrCodeDTO, ErrCodeQueryRequest, ErrCodeService> {

    private final ErrCodeService service;

    public ErrCodeController(ErrCodeService service) {
        this.service = service;
    }

    @Override
    protected ErrCodeService service() {
        return service;
    }

    /**
     * 按错误码和可选语言解析用户可见错误信息。
     *
     * @param code   平台错误码
     * @param locale 可选语言标识；未传时使用服务端默认语言或错误码默认文案
     * @return 包装后的错误码解析结果，包含 HTTP 状态、消息模板和描述
     */
    @GetMapping("/resolve")
    public R<ErrCodeResolveDTO> resolve(@RequestParam Integer code,
                                        @RequestParam(required = false) String locale) {
        return R.ok(service.resolve(code, locale));
    }

    /**
     * 从 JSON 文件批量导入或更新错误码定义。
     *
     * @param file 包含错误码及多语言文案的 JSON 文件
     * @return 包装后的导入结果，包含新增、更新和失败明细
     * @throws IOException 读取上传文件失败时抛出
     */
    @PostMapping("/import-json")
    public R<ErrCodeUpsertResultDTO> importJson(@RequestParam("file") MultipartFile file) throws IOException {
        return R.ok(service.importJsonFile(file));
    }

    /**
     * 导出全部错误码定义为可迁移的 JSON 文档集合。
     *
     * @return 包装后的错误码导出文档，包含基础定义和可持久化字段
     */
    @GetMapping("/export-json")
    public R<List<ErrCodeUpsertRequest>> exportJson() {
        return R.ok(service.exportJson());
    }
}
