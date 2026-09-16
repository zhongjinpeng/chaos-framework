# chaos-iam（设计方案，未在本仓库落地）

> **注意**：本仓库中不存在 `chaos-iam`、`chaos-iam-api`、`chaos-iam-service` 模块，下文“已落地”“当前实现”描述的是规划中的设计，
> 并非当前代码。BOM 中曾错误声明过这两个 artifact，已移除。实际可用的认证/授权能力见 [chaos-authorization](../modules/chaos-authorization.md) 和 [chaos-security](../modules/chaos-security.md)。

## 职责

IAM 是业务身份与权限中心，负责用户、租户、组织、角色、菜单和数据权限管理。授权服务器仍由 `chaos-authorization` 负责 OAuth2 password grant、Redis/reference token、refresh token 和互踢，IAM 只提供身份校验和权限数据。

## 模块边界

```text
chaos-iam
├── chaos-iam-api      对外 DTO、权限常量和远程契约
└── chaos-iam-service  IAM 业务服务和持久化实现
```

`chaos-iam-service` 可以独立部署，也可以作为 auth-server 的本地依赖。生产建议独立服务，auth-server 通过本地 bean、Feign 或内部 RPC 实现 `ChaosAuthorizationUserService`。

## 当前落地状态

已落地：

```text
chaos-iam-api
- IamAuthService
- TenantBootstrapService
- IamTokenInvalidationService
- IamPermissions
- IamDataScopes

chaos-iam-service
- sys_* 实体和 MyBatis Mapper
- tenant_code + username + password 认证
- IAM 到 ChaosAuthorizationUserService 的适配
- Spring Boot AutoConfiguration.imports 自动装配
- 租户初始化服务
- 用户、租户、角色、菜单、组织基础管理接口
- 用户角色授权、角色菜单授权
- 登录失败锁定和登录日志
- 权限变更 permission_version 递增和 token 失效
- MySQL schema.sql
```

`chaos-authorization` 的 password grant 已兼容可选 `tenant_code` 和 `tenantCode` 参数。旧业务只实现 `authenticateByUsername(username, password)` 时仍可继续运行。

## 登录链路

用户名密码登录必须带租户识别：

```text
tenant_code + username + password
```

OAuth2 password grant 参数：

```text
grant_type=password
client_id=iam-web
client_secret=***
tenant_code=demo
username=admin
password=123456
```

认证流程：

1. 根据 `tenant_code` 查询 `sys_tenant`。
2. 校验租户状态和过期时间。
3. 根据 `tenant_id + username` 查询 `sys_user`。
4. 校验用户状态、锁定状态、密码过期和强制改密标记。
5. 使用 BCrypt 校验密码。
6. 查询用户角色、菜单权限和数据权限范围。
7. 返回 `LoginUser(userId, username, tenantId, roles, permissions)`。
8. `chaos-authorization` 使用 Redis/reference token 保存授权对象。

登录安全配置：

```yaml
chaos:
  iam:
    login:
      max-fail-count: 5
      lock-duration: 15m
      log-enabled: true
```

当前登录失败处理：

```text
密码错误
- 递增 sys_user.login_fail_count
- 达到 chaos.iam.login.max-fail-count 后写入 sys_user.locked_until
- 写入 sys_login_log

登录成功
- 重置 sys_user.login_fail_count
- 清空 sys_user.locked_until
- 更新 sys_user.last_login_at
- 写入 sys_login_log
```

Redis token 配置：

```yaml
chaos:
  authorization:
    token:
      type: redis
      redis-key-prefix: chaos:authorization
    grant:
      default-password-enabled: true
```

## MyBatis 配置

IAM 表统一使用 `sys_` 前缀。当前框架默认忽略 `sys_` 表的租户和数据权限改写，IAM 服务必须覆盖：

```yaml
chaos:
  mybatis:
    tenant:
      ignore-table-prefixes: []
      ignore-tables:
        - flyway_schema_history
    data-scope:
      ignore-table-prefixes: []
      ignore-tables:
        - flyway_schema_history
```

## 表结构

所有核心表统一包含：

```text
tenant_id、version、created_by、created_at、updated_by、updated_at、deleted、deleted_at
```

软删除唯一索引必须带 `deleted_at`，避免删除后无法重建同名数据。

核心表：

```text
sys_tenant
sys_org
sys_user
sys_role
sys_menu
sys_user_role
sys_role_menu
sys_role_org
sys_login_log
sys_user_password_history
```

完整 SQL 位于 `chaos-iam/chaos-iam-service/src/main/resources/schema.sql`。

## 权限模型

菜单类型：

```text
DIR     目录
MENU    前端路由
BUTTON  页面按钮
API     后端接口权限
```

`permission` 建议只配置在 `BUTTON` 和 `API` 上，最终注入 `LoginUser.permissions`。

权限编码统一使用 `sys:` 前缀：

```text
sys:user:list
sys:user:create
sys:user:update
sys:user:disable
sys:user:reset-password
sys:tenant:list
sys:tenant:create
sys:tenant:update
sys:tenant:disable
sys:role:list
sys:role:create
sys:role:update
sys:role:grant
sys:menu:list
sys:menu:create
sys:menu:update
sys:menu:delete
sys:org:list
sys:org:create
sys:org:update
sys:org:delete
```

## 数据权限

角色数据范围：

```text
ALL        全部数据
TENANT     本租户
ORG        本组织
ORG_CHILD  本组织及子组织
SELF       仅本人
CUSTOM     自定义组织
```

多个角色合并时取最大范围：

```text
ALL > TENANT > ORG_CHILD > ORG > CUSTOM > SELF
```

IAM 的 `DataScopeProvider` 根据 `DataScopeRequest` 生成 `DataScopeCondition`，与现有 ABAC/RBAC 通用授权模型兼容。

## Token 失效

权限变更后必须处理旧 token：

1. 用户、角色、菜单、用户角色、角色菜单变更后，定位受影响用户。
2. 递增 `sys_user.permission_version`。
3. 调用 `IamTokenInvalidationService.invalidateUsers` 撤销相关用户授权。

当前默认实现会在本地可获得授权服务 bean 时执行：

```text
Redis authorization store
- 通过 findIdsByPrincipal 定位用户授权
- 将 access token 写入 JwtRevocationService
- 删除授权对象和 token 索引

AuthorizationSessionRegistry
- 通过 findByPrincipal 定位会话索引
- 授权对象存在时删除授权对象并清理索引
- 授权对象不存在时按会话摘要写入 JWT 撤销黑名单并清理索引
```

如果 IAM 独立部署且无法直接访问授权服务，需要覆盖 `IamTokenInvalidationService`，通过内部 RPC 调用 auth-server 的撤销能力。

当前实现：

```text
grantUserRoles
- 重建 sys_user_role
- 递增受影响用户 permission_version
- 调用 IamTokenInvalidationService.invalidateUsers

grantRoleMenus
- 重建 sys_role_menu
- 反查拥有该角色的用户
- 递增受影响用户 permission_version
- 调用 IamTokenInvalidationService.invalidateUsers
```

## 租户初始化

创建租户必须由 `TenantBootstrapService` 在一个事务内完成：

1. 创建租户。
2. 创建默认根组织。
3. 创建租户管理员用户。
4. 创建管理员角色。
5. 分配默认菜单权限。
6. 绑定用户角色。
7. 记录审计日志。

当前代码已完成 1-4、6；第 5 步依赖系统菜单初始化数据，第 7 步后续接入审计事件。

## REST 接口

基础管理接口位于 `chaos-iam-service`：

```text
POST /api/iam/tenants
GET  /api/iam/tenants

POST /api/iam/users
GET  /api/iam/users
PUT  /api/iam/users/{userId}/roles

POST /api/iam/roles
GET  /api/iam/roles
PUT  /api/iam/roles/{roleId}/menus

POST /api/iam/menus
GET  /api/iam/menus

POST /api/iam/orgs
GET  /api/iam/orgs
```

接口均使用 `@Permission` 绑定 `sys:*` 权限编码。返回值由 `chaos-web` 的统一响应切面包装。

## 落地优先级

P0：

```text
sys_* 核心表
tenantCode + username 登录
BCrypt 密码校验
租户/用户状态校验
角色菜单权限加载
Redis token 登录适配
租户初始化骨架
```

P1：

```text
登录失败锁定（已落地）
登录日志（已落地）
密码历史
权限变更 token 失效（已落地）
数据权限合并
菜单树和组织树
```

P2：

```text
岗位管理
字典管理
用户会话管理
导入导出
组织 closure table
```
