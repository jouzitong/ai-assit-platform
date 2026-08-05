package ai.platform.aiassit.chat.agent.control.data.controller;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.SkillControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.service.control.AiSkillControlService;
import jakarta.validation.Valid;
import org.athena.framework.web.vo.R;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Agent Skill 包的定义、版本和发布管理接口。
 *
 * <p>支持表单化维护与 ZIP 包导入两种方式，并在发布前校验 Skill 清单、文件结构和运行所需能力。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/skills")
public class AiSkillManageController {

    private final AiSkillControlService service;

    public AiSkillManageController(AiSkillControlService service) {
        this.service = service;
    }

    /**
     * 查询全部 Skill 目录。
     *
     * @return Skill 基础信息和当前版本摘要
     */
    @GetMapping
    public R<List<SkillControlDTOs.Catalog>> catalogs() {
        return R.ok(service.listCatalogs());
    }

    /**
     * 查询指定 Skill 的当前定义。
     *
     * @param skillCode Skill 业务编码
     * @return Skill 当前版本的完整配置与文件摘要
     */
    @GetMapping("/{skillCode}")
    public R<SkillControlDTOs.Version> skill(@PathVariable String skillCode) {
        return R.ok(service.getSkill(skillCode));
    }

    /**
     * 使用表单数据创建 Skill 草稿。
     *
     * @param request 表单草稿请求体，包含 Skill 元信息、指令内容和文件定义
     * @return 创建后的 Skill 版本详情
     */
    @PostMapping("/form")
    public R<SkillControlDTOs.Version> createForm(
            @Valid @RequestBody SkillControlDTOs.FormDraftRequest request) {
        return R.ok(service.createFormDraft(request));
    }

    /**
     * 修改指定 Skill 的可编辑配置。
     *
     * @param skillCode Skill 业务编码
     * @param request   更新请求体，包含元信息、指令或文件变更
     * @return 更新后的 Skill 版本详情
     */
    @PutMapping("/{skillCode}")
    public R<SkillControlDTOs.Version> update(@PathVariable String skillCode,
                                              @Valid @RequestBody SkillControlDTOs.UpdateRequest request) {
        return R.ok(service.updateSkill(skillCode, request));
    }

    /**
     * 删除指定 Skill 及可删除的版本记录。
     *
     * @param skillCode Skill 业务编码
     * @return 是否成功删除
     */
    @DeleteMapping("/{skillCode}")
    public R<Boolean> delete(@PathVariable String skillCode) {
        return R.ok(service.deleteSkill(skillCode));
    }

    /**
     * 检查上传的 Skill ZIP 包，未持久化导入结果。
     *
     * @param file 待检查的 ZIP 包，服务会解析其清单、目录和文件内容
     * @return 包检查结果，包含识别出的 Skill 信息与校验问题
     */
    @PostMapping(value = "/packages/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<SkillControlDTOs.Inspection> inspect(@RequestParam("file") MultipartFile file) {
        return R.ok(service.inspect(file));
    }

    /**
     * 将已检查的 ZIP 草稿导入为一个 Skill 版本。
     *
     * @param draftId 已检查并暂存的 Skill 包草稿标识
     * @param request 可选导入请求体，用于补充版本说明或导入选项
     * @return 导入后创建的 Skill 版本详情
     */
    @PostMapping("/packages/{draftId}/import")
    public R<SkillControlDTOs.Version> importZip(@PathVariable String draftId,
                                                 @RequestBody(required = false) SkillControlDTOs.ImportRequest request) {
        return R.ok(service.importDraft(draftId, request == null ? new SkillControlDTOs.ImportRequest() : request));
    }

    /**
     * 查询指定 Skill 的全部版本。
     *
     * @param skillCode Skill 业务编码
     * @return Skill 版本列表及发布状态
     */
    @GetMapping("/{skillCode}/versions")
    public R<List<SkillControlDTOs.Version>> versions(@PathVariable String skillCode) {
        return R.ok(service.listVersions(skillCode));
    }

    /**
     * 查询 Skill 的指定版本配置。
     *
     * @param skillCode Skill 业务编码
     * @param version   版本号
     * @return 该版本的完整定义和文件摘要
     */
    @GetMapping("/{skillCode}/versions/{version}")
    public R<SkillControlDTOs.Version> version(@PathVariable String skillCode,
                                               @PathVariable Integer version) {
        return R.ok(service.getVersion(skillCode, version));
    }

    /**
     * 查询 Skill 指定版本包含的文件清单。
     *
     * @param skillCode Skill 业务编码
     * @param version   版本号
     * @return 文件路径、类型和校验信息列表，不返回文件二进制内容
     */
    @GetMapping("/{skillCode}/versions/{version}/files")
    public R<List<SkillControlDTOs.FileItem>> files(@PathVariable String skillCode,
                                                    @PathVariable Integer version) {
        return R.ok(service.listVersionFiles(skillCode, version));
    }

    /**
     * 下载 Skill 指定版本的原始 ZIP 包。
     *
     * @param skillCode Skill 业务编码
     * @param version   版本号
     * @return ZIP 二进制响应，响应头携带文件名和 SHA-256 校验值
     */
    @GetMapping("/{skillCode}/versions/{version}/package")
    public ResponseEntity<byte[]> downloadPackage(@PathVariable String skillCode,
                                                   @PathVariable Integer version) {
        AiSkillControlService.PackageDownload value = service.getVersionPackage(skillCode, version);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(value.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Package-SHA256", value.checksum())
                .body(value.content());
    }

    /**
     * 校验 Skill 版本的清单、目录结构、文件引用和平台兼容性。
     *
     * @param skillCode Skill 业务编码
     * @param version   待校验版本号
     * @return 校验报告，包含阻塞问题与修复提示
     */
    @PostMapping("/{skillCode}/versions/{version}/validate")
    public R<ValidationReportDTO> validate(@PathVariable String skillCode,
                                           @PathVariable Integer version) {
        return R.ok(service.validateVersion(skillCode, version));
    }

    /**
     * 发布校验通过的 Skill 版本，供 Agent 运行时加载。
     *
     * @param skillCode Skill 业务编码
     * @param version   要发布的版本号
     * @return 发布后的 Skill 版本详情
     */
    @PostMapping("/{skillCode}/versions/{version}/publish")
    public R<SkillControlDTOs.Version> publish(@PathVariable String skillCode,
                                               @PathVariable Integer version) {
        return R.ok(service.publishVersion(skillCode, version));
    }
}
