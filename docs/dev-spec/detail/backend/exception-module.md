# 后端异常处理规范

## 1. 目标

- 统一后端 Web 场景下的异常返回方式，避免接口层各自随意处理。
- 统一“该抛什么异常”的判断标准，减少 `IllegalArgumentException`、`IllegalStateException` 滥用。
- 明确 `BizException`、错误码 `code`、HTTP 状态码的边界。
- 优先复用 `athena-framework` 已有异常体系和全局异常处理能力，不再重复造一套异常框架。

## 2. 基本原则

- 默认使用 `BizException` 表达业务失败。
- 异常优先交给全局异常处理，不要在 controller 中手工 `try/catch` 后拼错误响应。
- 业务可预期错误，使用带业务码的异常；系统不可预期错误，保留运行时异常并记录日志。
- 异常对应的日志级别遵循《[后端日志规范](./logging-module.md)》：可预期业务失败优先 `warn`，未知系统错误优先 `error`。
- 需要表达 HTTP 状态语义时，通过 `BizException.status` 设置 HTTP status，不要再新增独立 HTTP 异常基类。
- `BizException` 的 `code` 不是随便填的数字，而是全局唯一的业务语义标识。

## 3. Web 异常处理约束

### 3.0 异常包结构

- Athena 公共异常对象统一收敛到 `org.arthena.framework.common.exception.BizException`。
- `org.arthena.framework.common.exception.base` 不再作为异常基类包使用，不要新增或引用该包下的异常对象。
- 如果需要保留轻量错误码定义对象，放在 `org.arthena.framework.common.exception.Code`，不要继续放在 `exception.base` 包下。

### 3.1 全局处理

- Web 接口异常统一交给 Athena 的 `BaseControllerAdvice` 处理。
- 新模块默认不要重复定义一套通用 `@RestControllerAdvice`。
- 只有在模块确实需要补充特殊异常映射时，才新增局部 advice；新增前先确认 Athena 默认处理是否已经覆盖。

### 3.2 Athena 已覆盖的常见异常

- `MethodArgumentNotValidException`
- `BindException`
- `MissingServletRequestParameterException`
- `MethodArgumentTypeMismatchException`
- `HttpMessageNotReadableException`
- `HttpRequestMethodNotSupportedException`
- `HttpMediaTypeNotSupportedException`
- `BizException`

这些异常 Athena 已有统一返回处理。新代码优先复用，不要再在 controller 里重复转换。

## 4. 默认异常选择

### 4.1 默认规则

- 如果没有特殊语义，统一使用 `BizException`。
- 这里的“没有特殊语义”包括：
  - 普通业务校验失败
  - 普通参数缺失或参数非法
  - 普通对象不存在，但本质只是业务流程中的校验失败
  - 普通状态冲突、重复创建、重复绑定、非法流转
- 不要因为 message 不同，就机械拆出很多异常类。

### 4.2 参数错误

- 请求参数缺失、格式错误、字段非法、前置条件不满足，优先使用：
  - Bean Validation（`@Valid`、`@NotNull`、`@NotBlank` 等）
  - `BizException.illegalParam(...)`
- 如果是应用服务内部的主动参数校验，也优先抛 `BizException.illegalParam(...)`。
- `IllegalArgumentException` 仅用于极底层通用工具、框架保护性校验，不作为业务接口参数错误的默认异常。

### 4.3 资源不存在

- 默认情况下，“对象不存在”也优先使用 `BizException` 表达。
- 如果该场景明确要表达 REST 资源语义、并且希望返回稳定 404 含义，使用带 `404` status 的 `BizException`。
- 换句话说：
  - 普通业务流程里的“当前对象不存在” -> `BizException`
  - 明确的资源访问语义缺失 -> `BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND, 404, args...)`
- 不要把“对象不存在”默认写成 `IllegalArgumentException("xxx 不存在")` 或 `IllegalStateException("xxx 不存在")`。

### 4.4 HTTP 状态异常

- 只有在接口确实需要明确区分 HTTP 状态时，才设置 `BizException.status`。
- 典型场景：
  - 未登录、认证失败、token 无效，需要明确返回 401
  - 已登录但无权限，需要明确返回 403
  - 某些协议型接口必须通过 HTTP status 表达失败类别
- 推荐写法：
  - `throw BizException.of(ErrCodeConstant.UNAUTHORIZED, 401);`
  - `throw BizException.of(ErrCodeConstant.FORBIDDEN, 403);`
- 如果只是普通业务失败，不要为了“看起来更专业”把它包装成 HTTP 状态异常。

### 4.5 系统错误

- 外部系统调用失败、序列化失败、底层组件异常、数据库连接异常等不可预期问题，允许抛运行时异常或领域专用系统异常。
- 这类异常重点是保留 cause 和上下文，不要为了“统一”吞掉原始异常栈。
- 对外暴露时由全局异常处理兜底，不要在 controller 手工拼接 `"操作失败"`。

## 5. 值得保留的少数特殊异常场景

- `BizException`
  - 作为业务失败和 HTTP status 语义的统一异常对象。
  - 默认 status 为 `200`；需要 401/403/404 等 HTTP 语义时显式设置 status。
- 领域专用异常
  - 当某一类异常在模块内会反复出现，且需要稳定表达固定领域语义时，可以保留专门异常类。
  - 领域专用异常必须继承 `BizException`，不要再继承 `exception.base` 下的旧基类。
  - 例如：
    - `ObjectStorageException`
    - `WsProtocolException`
    - `CommunicationException`
- Bean Validation 相关异常
  - 这类由 Spring/Athena 框架层自动抛出，直接复用默认处理，不需要业务方重复封装。

## 6. BizException 与错误码规范

### 6.1 基本语义

- `BizException` 用于表达“可预期的业务失败”。
- `BizException` 的核心不是 message，而是 `code`。
- 接口返回中的 `msg` 由 `ErrorCodeUtils` 基于 `code + args` 解析得到，因此代码里不应把 message 当成主语义。
- `code = 0` 表示成功；非 `0` 表示失败。

### 6.2 唯一性要求

- 每个业务错误码必须全局唯一。
- “全局唯一”不是指当前类里不重复，而是整个 `ai-assit-platform` + Athena 公共错误码体系中都不能重复占用同一语义编号。
- 不允许不同模块各自定义同一个数字但表达不同语义。
- 一旦错误码已经上线使用，不允许随意改号；只能新增，不能重排。

### 6.3 定义位置

- 框架通用错误码放在 Athena 公共常量中，例如：
  - `ErrCodeConstant`
  - `ParamBizCodeConstant`
- 业务模块自己的错误码，放在该模块对外契约层的 `constant` 包中。
- 如果多个模块要共享同一组业务错误码，常量必须放到对应 `api` 模块，而不是散落在 `core` 或 `boot` 中。
- 不要把业务错误码直接写死在 service / controller 中。

### 6.4 编码格式

- 业务错误码统一使用 `YY_XX_####` 风格定义整数常量。
- 含义约定：
  - `YY`：业务大类
  - `XX`：错误子类
  - `####`：顺序编号
- 示例：
  - `41_01_0001`
  - `51_03_0002`
- Java 中虽然写了下划线，但本质仍然是整数常量；这种写法只是为了让码段结构可读。

### 6.5 推荐码段约定

- 优先复用 Athena 已有公共码段，不要重复发明：
  - `41_xx_xxxx`：参数域
  - `40xxx`：认证/用户相关公共错误
  - `10xxx` / `1~99999` 中 Athena 已占用的公共保留码，不要重复定义业务语义
- 新业务模块新增错误码时，先为该模块分配一个稳定的 `YY` 段，再在段内细分 `XX`。
- `XX` 推荐语义：
  - `01`：必填缺失
  - `02`：取值非法
  - `03`：资源不存在
  - `04`：状态或约束冲突
  - `05`：外部依赖/同步失败
  - `06`：系统处理失败
- 如果某模块已有自己的稳定划分，后续继续沿用，不要中途切换另一套规则。

### 6.6 命名规范

- 错误码常量类命名统一使用 `*BizCodeConstant`。
- 例如：
  - `AiKbBizCodeConstant`
  - `RenderBizCodeConstant`
- 常量名直接表达业务语义，例如：
  - `REQUIRED_DTO`
  - `DOCUMENT_NOT_FOUND`
  - `STATUS_CONFLICT`
- 不要使用模糊命名，例如：
  - `ERROR_1`
  - `FAILED_CODE`
  - `UNKNOWN_BIZ_ERROR`

### 6.7 文案定义

- 每个错误码都必须有对应文案。
- 文案统一通过 `ErrorCode-*.properties` 维护，不要只定义常量不补文案。
- 业务模块如果定义了自己的 `code`，必须在对应应用的 `boot` 模块下定义文案文件：
  - `ErrorCode-zh.properties`
  - `ErrorCode-en.properties`
- 例如：
  - `app/.../boot/src/main/resources/ErrorCode-zh.properties`
  - `app/.../boot/src/main/resources/ErrorCode-en.properties`
- Athena 默认会加载公共错误码文件，并支持业务侧覆盖 / 扩展。
- 如果新增了错误码但没有补文案，接口虽然能返回 `code`，但 `msg` 会退化，属于不完整实现。

### 6.8 使用方式

- 参数类错误优先：
  - `BizException.illegalParam(code, args...)`
- 通用业务错误优先：
  - `BizException.of(code, args...)`
- 需要指定 HTTP status 时：
  - `BizException.of(code, status, args...)`
- 参数缺失类错误如果只是同一语义下的简单字段替换，并且中英文文案都能自然表达，可以复用一个 code + 参数。
- 如果参数化后会导致多语言文案别扭、业务语义不够清晰，或前后端希望精确区分字段级错误，则应拆成多个独立 code，不要为了“少几个 code”牺牲语义质量。
- 不要在业务代码中直接写裸数字错误码。
- 不要优先写 `throw new BizException(123456)` 这种绕过常量定义的用法。

### 6.9 新增错误码流程

1. 先确认 Athena 公共错误码或当前模块错误码里是否已有可复用语义。
2. 如果没有，再在目标模块的 `*BizCodeConstant` 中新增常量。
3. 选择该模块已分配的 `YY` 段，并按 `XX` 子类归位。
4. 同步补充 `ErrorCode-zh.properties`，必要时补 `ErrorCode-en.properties`。
5. 在业务代码中统一引用该常量，不要散落裸数字。

## 7. 何时新建异常类

### 7.1 应该新建

- 某一类错误在模块中会被重复抛出，且语义稳定明确。
- 需要携带固定业务码或固定 HTTP 状态。
- 需要让调用方一眼看出错误类别，而不是依赖 message 文本判断。
- 该异常在日志、排障、调用链中需要被单独识别。
- 新建异常类必须继承 `BizException`。

### 7.2 不需要新建

- 只会出现一次的一次性错误。
- 只是换一个类名包裹同样的 `code/message`，没有新增语义。
- 单纯为了避免写 `BizException.of(...)` 而机械创建大量细碎异常类。
- 普通业务校验失败，本来用 `BizException` 就足够表达。
- 只是为了区分参数错误、资源不存在、未实现等通用场景而新建异常类。

## 8. 自定义异常类设计规范

- 优先基于 `BizException` 扩展，不要直接从 `RuntimeException` 裸继承。
- 如果没有特殊 HTTP 或协议语义，优先不要新建异常类，直接使用 `BizException`。
- 需要 HTTP 状态语义时，使用 `BizException.status`，不要再引入 `BaseHttpRuntimeException` 之类的基类。
- 自定义异常至少要满足以下之一：
  - 固定业务码
  - 固定 HTTP 状态
  - 固定领域语义
- 异常类命名要直接体现领域语义，例如：
  - `DbAccessException`
  - `RenderPublishException`
  - `WorkflowPermissionException`
- 不要使用模糊命名，如：
  - `CommonException`
  - `SystemException`
  - `ManagerException`

## 9. Controller / Service 分层约束

- Controller 不负责定义异常语义，只负责让异常自然抛出。
- 参数校验、业务校验、资源校验优先放在 service / domain service。
- Controller 中除非是文件下载、流式响应、鉴权协商等特殊场景，否则不要手工捕获再转换异常。
- 如果某个接口为了兼容协议必须返回特殊 HTTP 状态，再在 controller 或 facade 层抛带 status 的 `BizException`。

## 10. 常见反模式

- 在 controller 中 `try/catch` 后返回 `R.fail("操作失败")`。
- 参数错误大量直接抛 `IllegalArgumentException`。
- 资源不存在统一抛 `IllegalStateException`。
- 已知业务失败直接抛裸 `RuntimeException`。
- 普通业务错误大量拆成无差异的自定义异常类。
- 认证/权限错误只返回业务码，不返回对应 HTTP 状态。
- 业务代码直接写裸数字错误码。
- 新增了 `BizCodeConstant` 常量，但没有补 `ErrorCode-*.properties` 文案。
- 不同模块重复占用同一个错误码数字。
- 继续新增或引用 `exception.base` 下的异常基类。

## 11. 落地建议

- 新接口优先接入 Bean Validation 和 `BizException` 体系。
- 旧代码里已经存在的大量 `IllegalArgumentException` / `IllegalStateException`，按模块逐步收敛，不要求一次性全量替换。
- 旧代码里如果已经把大量“对象不存在”写成专门异常，先分辨它是否真的需要 404 语义，再决定是否保留。
- 如果一个模块已经形成稳定异常语义，再提炼专用异常类；不要在语义还不清晰时提前抽象。
- 新模块首次定义业务错误码时，先在规范或模块文档里记录该模块占用的 `YY` 段，避免后续冲突。
