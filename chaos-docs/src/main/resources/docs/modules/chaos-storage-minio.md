# chaos-storage-minio

## 职责

MinIO 实现的 `ObjectStorageClient`，源码目录为 `chaos-storage/chaos-storage-minio`，对外 artifactId 为 `chaos-storage-minio`。

## 依赖方式

通过 `chaos-storage-starter` 引入，并配置 `chaos.storage.provider=minio`。

## 配置

```yaml
chaos:
  storage:
    provider: minio
    minio:
      endpoint: http://localhost:9000
      access-key: ${MINIO_ACCESS_KEY}
      secret-key: ${MINIO_SECRET_KEY}
```

## 行为

- 调用 SDK 前按 [chaos-storage](chaos-storage.md) 的规则校验 bucket、objectKey、TTL。
- `size < 0`（大小未知）时使用 10 MiB 分块上传。MinIO 要求未知大小时必须指定 5 MiB ~ 5 GiB 的 partSize，旧版本固定传 `-1` 会直接报错。
- 预签名 URL 有效期上限 7 天。
- 额外提供 `stat(bucket, objectKey)` 查询对象元数据。

## 注意事项

- 密钥请通过环境变量或配置中心注入，不要写入仓库。
