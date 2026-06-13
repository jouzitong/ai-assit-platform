package ai.platform.aiassit.db.engine.meta.entity.excel;

import lombok.Data;

import java.util.List;

@Data
public class DbMetaWorkbookTemplateConfig {

    private List<NamedRangeConfig> namedRanges;

    private List<SheetConfig> sheets;

    @Data
    public static class SheetConfig {
        private String key;
        private String name;
        private List<ColumnConfig> columns;
    }

    @Data
    public static class ColumnConfig {
        private String key;
        private String label;
        private String type;
        private Integer width;
        private String headerColor;
        private Boolean importable;
        private Boolean exportable;
        private List<String> masks;
        private String format;
        private String defaultValue;
        private String namedRange;
        private Boolean required;
        private String description;
    }

    @Data
    public static class NamedRangeConfig {
        private String name;
        private String sheetKey;
        private String columnKey;
        private Integer startRow;
        private Integer endRow;
    }
}
