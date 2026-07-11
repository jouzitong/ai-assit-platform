package ai.platform.aiassit.db.engine.meta.entity.config;

import ai.platform.aiassit.db.engine.meta.enums.DataSourceConfigType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.Serializable;

/** 数据源协议专属配置的根类型。 */
@JsonDeserialize(using = DataSourceConfigDeserializer.class)
public interface DataSourceConfig extends Serializable {

    DataSourceConfigType getConfigType();

    Integer getConfigVersion();
}
