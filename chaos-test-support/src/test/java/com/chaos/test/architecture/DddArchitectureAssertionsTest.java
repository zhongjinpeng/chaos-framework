package com.chaos.test.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

class DddArchitectureAssertionsTest {

    @TempDir
    private Path sourceRoot;

    @Test
    void acceptsSeparatedBoundaryObjects() throws IOException {
        write("com/example/order/domain/Order.java", "package com.example.order.domain; public class Order {}");
        write("com/example/order/application/command/CreateOrderCommand.java",
                "package com.example.order.application.command; public record CreateOrderCommand(String id) {}");
        write("com/example/order/application/query/OrderQuery.java",
                "package com.example.order.application.query; public record OrderQuery(String id) {}");
        write("com/example/order/application/result/OrderResult.java",
                "package com.example.order.application.result; public record OrderResult(String id) {}");
        write("com/example/order/interfaces/rest/request/OrderRequest.java",
                "package com.example.order.interfaces.rest.request; public record OrderRequest(String id) {}");
        write("com/example/order/interfaces/rest/response/OrderResponse.java",
                "package com.example.order.interfaces.rest.response; public record OrderResponse(String id) {}");

        assertDoesNotThrow(() -> DddArchitectureAssertions.assertLayeredArchitecture(
                sourceRoot, "com.example.order"));
    }

    @Test
    void rejectsPersistenceTypeInDomain() throws IOException {
        write("com/example/order/domain/OrderPO.java",
                "package com.example.order.domain; import com.baomidou.mybatisplus.annotation.TableName; "
                        + "public class OrderPO {}");

        assertThrows(AssertionFailedError.class, () -> DddArchitectureAssertions.assertLayeredArchitecture(
                sourceRoot, "com.example.order"));
    }

    private void write(String relativePath, String source) throws IOException {
        Path file = sourceRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
