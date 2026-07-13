# 通用 JDBC 执行器运行配置

本模块为 PostgreSQL、Oracle、DM8、KingbaseES、GaussDB、OceanBase、TDSQL、GoldenDB、GBase 和神通数据库提供通用 JDBC 访问，包括连接测试、元数据、查询、执行和基础 DDL。MySQL 由同级 `mysql` 模块处理，MongoDB 由 `mongodb` 模块处理。

## 默认连接参数

`HOST_PORT` 模式按下表生成 `jdbcUrl`；集群、服务名、读写分离或特殊认证场景应使用 `JDBC_URL` 模式传入厂商完整连接串。

| `dbType` | 默认 URL | 端口 | 预期 `driverClass` | 默认 DDL 族 |
| --- | --- | ---: | --- | --- |
| `POSTGRESQL` | `jdbc:postgresql://host:port/database` | 5432 | `org.postgresql.Driver` | `POSTGRESQL` |
| `ORACLE` | `jdbc:oracle:thin:@//host:port/service` | 1521 | `oracle.jdbc.OracleDriver` | `ORACLE` |
| `DM8` | `jdbc:dm://host:port` | 5236 | `dm.jdbc.driver.DmDriver` | `ORACLE` |
| `KINGBASE_ES` | `jdbc:kingbase8://host:port/database` | 54321 | `com.kingbase8.Driver` | `POSTGRESQL` |
| `GAUSSDB` | `jdbc:gaussdb://host:port/database` | 8000 | `com.huawei.gaussdb.jdbc.Driver` | `POSTGRESQL` |
| `OCEANBASE` | `jdbc:oceanbase://host:port/database` | 2881 | `com.oceanbase.jdbc.Driver` | `MYSQL` |
| `TDSQL` | `jdbc:mysql://host:port/database` | 3306 | `com.mysql.cj.jdbc.Driver` | `MYSQL` |
| `GOLDENDB` | `jdbc:mysql://host:port/database` | 3306 | `com.mysql.cj.jdbc.Driver` | `MYSQL` |
| `GBASE` | `jdbc:gbase://host:port/database` | 5258 | `com.gbase.jdbc.Driver` | `MYSQL` |
| `SHENTONG` | `jdbc:oscar://host:port/database` | 2003 | `com.oscar.Driver` | `ORACLE` |

## 已内置的官方驱动

下列版本由当前有效 Maven 配置解析；模块已为每类产品配置默认驱动类，通常不必填写 `driverClass`。

| 用途 | Maven 坐标 | 版本 |
| --- | --- | --- |
| PostgreSQL | `org.postgresql:postgresql` | `42.6.0` |
| Oracle | `com.oracle.database.jdbc:ojdbc11` | `21.9.0.0` |
| DM8 | `com.dameng:DmJdbcDriver8` | `8.1.4.125` |
| KingbaseES | `cn.com.kingbase:kingbase8` | `9.0.1` |
| GaussDB | `com.huaweicloud.gaussdb:gaussdbjdbc` | `506.0.0.b058` |
| OceanBase | `com.oceanbase:oceanbase-client` | `2.4.14` |
| TDSQL/GoldenDB MySQL 模式 | `mysql:mysql-connector-java` | `8.0.17`，由标准 boot 装配中的 MySQL 执行器提供 |

驱动版本应与目标数据库服务端的厂商兼容矩阵一致。GaussDB 必须使用华为 GaussDB 驱动，不能用上游 PostgreSQL 驱动替代。

## 配置字段

- `connection.mode`：`HOST_PORT` 自动生成 URL；`JDBC_URL` 直接使用 `connection.jdbcUrl`。
- `driverProperties.driverClass`：显式加载运行时驱动，企业私服驱动建议必填。
- `driverProperties.compatibilityMode`：选择 SQL、DDL 和超时参数族，优先级高于 `ddlDialect`。
- `driverProperties.ddlDialect`：`compatibilityMode` 的兼容别名。
- 可选值：`MYSQL`、`POSTGRESQL`、`ORACLE`、`ANSI`；`PG`、`POSTGRES` 会归一化为 `POSTGRESQL`。
- 其余 `driverProperties` 会原样传给 JDBC 驱动。
- `network.connectTimeoutMs`、`readTimeoutMs` 会按 DDL 族转换成对应驱动参数；当前通用 JDBC 层不映射 `writeTimeoutMs`。

`compatibilityMode` 只决定本模块生成的 SQL/DDL 和超时参数，不会改变 JDBC 协议或自动替换驱动。

## JSON 示例

下面示例配置 OceanBase Oracle 租户。`dbType` 和 `authType` 同时接受枚举名或数字 code。

```json
{
  "configVersion": 2,
  "configType": "DATABASE",
  "dbType": "OCEANBASE",
  "connection": {
    "mode": "JDBC_URL",
    "jdbcUrl": "jdbc:oceanbase://10.0.0.10:2881/appdb",
    "schemaName": "APP"
  },
  "credential": {
    "authType": "BASIC",
    "username": "app_user",
    "passwordCiphertext": "<由凭证组件提供的运行时密码>"
  },
  "network": {
    "connectTimeoutMs": 5000,
    "readTimeoutMs": 30000
  },
  "driverProperties": {
    "compatibilityMode": "ORACLE",
    "useSSL": false
  }
}
```

当前连接层读取 `username` 和 `passwordCiphertext` 建立连接；若配置只保存 `credentialRef`，部署侧必须在进入执行器前完成凭证解析。

## GBase 与神通企业私服驱动

GBase 和神通 JDBC 驱动未随本模块发布。取得与服务端、JDK 17 匹配且许可允许使用的厂商 JAR 后，将其发布到企业 Nexus/Artifactory，不要提交 JAR，也不要使用 `systemPath`。内部坐标由企业自行定义，例如：

```bash
mvn deploy:deploy-file \
  -Dfile=/secure/vendor/gbase-connector-java.jar \
  -DgroupId=com.company.jdbc \
  -DartifactId=gbase-connector-java \
  -Dversion=<vendor-version> \
  -Dpackaging=jar \
  -DrepositoryId=company-releases \
  -Durl=https://nexus.example.com/repository/maven-releases/
```

在实际 boot 发行模块中以 `runtime` scope 引入内部坐标，并在数据源配置中显式指定驱动：

```json
{
  "dbType": "GBASE",
  "connection": {
    "mode": "JDBC_URL",
    "jdbcUrl": "jdbc:gbase://10.0.0.20:5258/appdb"
  },
  "driverProperties": {
    "driverClass": "com.gbase.jdbc.Driver",
    "compatibilityMode": "MYSQL"
  }
}
```

神通对应使用厂商 `oscarJDBC*.jar`、`com.oscar.Driver` 和 `jdbc:oscar://...`。

## 产品变体限制

- `TDSQL` 和 `GOLDENDB` 的默认配置仅表示 MySQL 模式，复用 MySQL Connector/J；不要把 Oracle/MySQL 的“语法兼容”理解为可复用 Oracle 驱动。
- TDSQL PG 必须使用 `JDBC_URL`、腾讯厂商驱动和 `POSTGRESQL` 模式；当前未内置该驱动。
- GoldenDB Oracle 模式必须使用厂商认证的连接串和驱动，当前未内置，也不能直接使用 `ojdbc11`。
- `OCEANBASE` 默认是 MySQL 模式；Oracle 租户应显式配置 `compatibilityMode=ORACLE`，仍使用 OceanBase Connector/J。
- `GBASE` 的 `HOST_PORT` 默认只对应 GBase 8a。GBase 8s/8c 必须使用完整 URL、匹配的厂商驱动和明确的 DDL 族；8s 的 Informix 特有 DDL 不在四种通用 DDL 族覆盖范围内。
- KingbaseES、GaussDB 等 PostgreSQL 兼容产品仍使用各自厂商驱动。多版本共存或 HA 参数应使用厂商完整 URL，并在目标环境做连接、元数据和 DDL 验证。
