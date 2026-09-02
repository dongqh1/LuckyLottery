# HMall Lottery 融合工程

本目录是黑马商城与 LuckyHub 营销核心的隔离融合工作区，原始四个来源目录保持不变。

## 当前目录

- `mall-backend/`：黑马商城 Java 后端原始副本（`hm-common` + `hm-service`）。
- `marketing-service/`：LuckyHub Java 17 后端源码快照，后续只保留并适配营销核心。
- `mysql/`：黑马商城原始 MySQL 配置和初始化数据。
- `nginx/`：黑马商城 Portal/Admin 静态前端和 Nginx 配置。
- `tests/`：融合工作区结构与集成契约测试。

## 来源

- LuckyHub：`E:\ANotes\software\luckyhub`
- 黑马商城：`E:\ANotes\后端\后端开发\SpringCloud微服务—资料\day02-Docker\资料\hmall - 副本`
- MySQL：`E:\ANotes\后端\后端开发\SpringCloud微服务—资料\day02-Docker\资料\mysql - 副本`
- Nginx：`E:\ANotes\后端\后端开发\SpringCloud微服务—资料\day02-Docker\资料\nginx - 副本`

> LuckyHub 来源分支为 `feature/frontend-contract-remediation`，复制时来源工作树已有修改和未跟踪文件。本仓库提交保存的是实际工作树快照，不等同于来源仓库 HEAD。
