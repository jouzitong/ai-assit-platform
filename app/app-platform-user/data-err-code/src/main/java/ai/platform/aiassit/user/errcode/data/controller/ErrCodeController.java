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

    @GetMapping("/resolve")
    public R<ErrCodeResolveDTO> resolve(@RequestParam Integer code,
                                        @RequestParam(required = false) String locale) {
        return R.ok(service.resolve(code, locale));
    }

    @PostMapping("/import-json")
    public R<ErrCodeUpsertResultDTO> importJson(@RequestParam("file") MultipartFile file) throws IOException {
        return R.ok(service.importJsonFile(file));
    }

    @GetMapping("/export-json")
    public R<List<ErrCodeUpsertRequest>> exportJson() {
        return R.ok(service.exportJson());
    }
}
