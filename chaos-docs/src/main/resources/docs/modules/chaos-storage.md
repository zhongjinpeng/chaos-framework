# chaos-storage

## 职责

文件与对象存储能力域。契约模块位于 `chaos-storage/chaos-storage`，对外 artifactId 为 `chaos-storage`；MinIO、OSS 适配器（`chaos-storage-minio`、`chaos-storage-oss`）位于同一能力域目录下。

> 对象存储端口模块（目录 `chaos-storage/chaos-storage`），
> 能力域、starter（`chaos-storage-starter`）与配置前缀（`chaos.storage.*`）统一使用 storage 命名。

| 类型 | 说明 |
| --- | --- |
| `ObjectStorageClient` | 对象存储端口：put / get / delete / presignedGetUrl |
| `FileObject` | 对象元数据 |
| `ObjectStorageArguments` | bucket、objectKey、预签名 TTL 统一校验 |
| `StoragePolicy` | 上传策略：最大大小、contentType 白名单（支持 `image/*`） |
| `PolicyEnforcingObjectStorageClient` | 按策略校验上传的装饰器 |

## 依赖方式

业务应用使用 `chaos-storage-starter`；application 层只需端口时依赖 `chaos-storage`。

## 配置

`chaos-storage-starter` 聚合 `chaos-autoconfigure`（storage）、`chaos-storage-minio` 与 `chaos-storage-oss`。
只需要端口抽象时依赖 `chaos-storage`；需要运行时自动装配 MinIO 或 OSS 时依赖 starter。

```yaml
chaos:
  storage:
    provider: minio        # 必须显式配置 oss|minio，否则不创建默认 ObjectStorageClient
    minio:
      endpoint: http://localhost:9000
      access-key: ${MINIO_ACCESS_KEY}
      secret-key: ${MINIO_SECRET_KEY}
```

OSS：

```yaml
chaos:
  storage:
    provider: oss
    oss:
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      access-key-id: ${OSS_ACCESS_KEY_ID}
      access-key-secret: ${OSS_ACCESS_KEY_SECRET}
```

上传限制对 OSS 与 MinIO 同时生效：

```yaml
chaos:
  storage:
    max-object-size: 20MB          # 为空不限制；大小未知的流式上传读取超限即中断
    allowed-content-types:         # contentType 白名单，支持通配；为空不限制
      - image/*
      - application/pdf
```

## 参数校验规则

所有适配器在调用 SDK 前执行，违反时抛出 `IllegalArgumentException`：

| 参数 | 规则 | 防止的问题 |
| --- | --- | --- |
| bucket | 非空 | — |
| objectKey | 非空；≤ 1024 字节；不以 `/` 开头；不含 `\` 和控制字符；不含 `.`、`..` 路径段 | 把用户文件名拼进 key 时通过 `../` 覆盖其他用户或租户的对象 |
| 预签名 TTL | 1 秒 ≤ ttl ≤ 7 天 | `null` 导致 NPE；超过 S3/MinIO 上限在运行期才报错 |

## 示例

```java
// 推荐：由服务端生成 key，不直接使用用户文件名
String key = tenantId + "/avatar/" + UUID.randomUUID() + extension;
objectStorageClient.put("user-files", key, inputStream, size, contentType);

URL url = objectStorageClient.presignedGetUrl("user-files", key, Duration.ofMinutes(10));
```

大小未知时 `size` 传 `-1`，适配器会使用分块/chunked 上传；配置了 `max-object-size` 时读取超限立即中断。

## 注意事项

- `put` 的输入流和 `get` 返回的输入流都由调用方关闭。
- 校验只保证 key 不会越出前缀，业务仍需自行保证租户前缀来自认证上下文。
- 自动注册的 `ObjectStorageClient` 是 `PolicyEnforcingObjectStorageClient` 包装后的实例；
  自定义 Bean 若需要上传限制，需自行包装。
- 同一应用需要多存储路由时，由业务注册自己的组合 `ObjectStorageClient` 实现。
- 新增存储产品时放到 `chaos-storage/{adapter}` 下，并补充对应的自动装配条件。
