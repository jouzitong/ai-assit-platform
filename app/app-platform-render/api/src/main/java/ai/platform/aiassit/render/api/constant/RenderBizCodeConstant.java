package ai.platform.aiassit.render.api.constant;

/**
 * 渲染页面业务异常子码。
 *
 * <p>编码规范：YY_XX_####
 * <ul>
 *     <li>YY: 大方向（52=渲染页面域）</li>
 *     <li>XX: 小方向（01=必填缺失，03=资源不存在）</li>
 *     <li>####: 具体业务编号</li>
 * </ul>
 */
public interface RenderBizCodeConstant {

    // 52_01_xxxx 必填缺失
    Integer REQUIRED_RENDER_PAGE_CODE = 52_01_0001;
    Integer REQUIRED_RENDER_PAGE_NAME = 52_01_0002;
    Integer REQUIRED_COMPONENT_KEY = 52_01_0003;
    Integer REQUIRED_COMPONENT_NAME = 52_01_0004;
    Integer REQUIRED_COMPONENT_STATUS = 52_01_0005;

    // 52_03_xxxx 资源不存在
    Integer RENDER_PAGE_NOT_FOUND = 52_03_0001;
    Integer COMPONENT_NOT_FOUND = 52_03_0002;
}
