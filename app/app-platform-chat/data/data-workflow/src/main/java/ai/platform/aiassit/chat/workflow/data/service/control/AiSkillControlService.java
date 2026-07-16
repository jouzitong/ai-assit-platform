package ai.platform.aiassit.chat.workflow.data.service.control;

import ai.platform.aiassit.chat.workflow.data.entity.dto.control.SkillControlDTOs;
import ai.platform.aiassit.chat.workflow.data.entity.dto.control.ValidationReportDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AiSkillControlService {

    List<SkillControlDTOs.Catalog> listCatalogs();

    SkillControlDTOs.Version getSkill(String skillCode);

    SkillControlDTOs.Inspection inspect(MultipartFile file);

    SkillControlDTOs.Version createFormDraft(SkillControlDTOs.FormDraftRequest request);

    SkillControlDTOs.Version importDraft(String draftId, SkillControlDTOs.ImportRequest request);

    SkillControlDTOs.Version updateSkill(String skillCode, SkillControlDTOs.UpdateRequest request);

    boolean deleteSkill(String skillCode);

    List<SkillControlDTOs.Version> listVersions(String skillCode);

    SkillControlDTOs.Version getVersion(String skillCode, Integer version);

    List<SkillControlDTOs.FileItem> listVersionFiles(String skillCode, Integer version);

    PackageDownload getVersionPackage(String skillCode, Integer version);

    ValidationReportDTO validateVersion(String skillCode, Integer version);

    SkillControlDTOs.Version publishVersion(String skillCode, Integer version);

    record PackageDownload(String filename, String checksum, byte[] content) {
    }
}
