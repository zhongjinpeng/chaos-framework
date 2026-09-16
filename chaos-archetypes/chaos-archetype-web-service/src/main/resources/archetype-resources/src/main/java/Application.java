package ${package};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 服务启动入口。
 *
 * <p>分层约定（依赖只能自上而下）：</p>
 * <ul>
 *     <li>{@code interfaces}：HTTP 等协议适配，只做参数校验与 DTO 转换；</li>
 *     <li>{@code application}：用例编排、事务边界、权限声明；</li>
 *     <li>{@code domain}：聚合、领域事件、仓储接口，不依赖 Spring；</li>
 *     <li>{@code infrastructure}：仓储实现、外部系统适配。</li>
 * </ul>
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
