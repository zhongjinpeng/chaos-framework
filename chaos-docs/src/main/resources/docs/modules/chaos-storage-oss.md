# chaos-storage-oss

## 职责

阿里云 OSS 实现的 `ObjectStorageClient`，源码目录为 `chaos-storage/chaos-storage-oss`，对外 artifactId 为 `chaos-storage-oss`。

## 依赖方式

通过 `chaos-storage-starter` 引入，并配置 `chaos.storage.provider=oss`。

## 配置

```yaml
chaos:
  storage:
    provider: oss
    oss:
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      access-key-id: ${OSS_ACCESS_KEY_ID}
      access-key-secret: ${OSS_ACCESS_KEY_SECRET}
```

## 行为

- 调用 SDK 前按 [chaos-storage](chaos-storage.md) 的规则校验 bucket、objectKey、TTL。
- `size < 0` 时不设置 `Content-Length`，由 SDK 使用 chunked 上传；contentType 为空时不写入元数据。
- 预签名 URL 有效期限制在 1 秒 ~ 7 天。

## 注意事项

- 生产环境建议使用 RAM 子账号或 STS 临时凭证，最小化 bucket 权限。
