-- 检查是否存在数据库crackProject，存在则使用，不存在则创建并使用（幂等性：重复执行仅切换数据库）
CREATE DATABASE IF NOT EXISTS crackProject DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crackProject;

-- 1. 角色表（存储用户角色，如管理员/普通用户）
-- 幂等性：IF NOT EXISTS 确保表已存在时不重复创建
CREATE TABLE IF NOT EXISTS `roles` (
                                       `id` varchar(10) NOT NULL COMMENT '角色ID',
    `roleName` varchar(50) NOT NULL COMMENT '角色名称（如管理员/普通用户）',
    `createdAt` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色表';

-- 初始化默认角色（1=普通用户，2=管理员）
-- 幂等性：ON DUPLICATE KEY UPDATE 确保重复执行仅更新角色名称，不插入重复数据
INSERT INTO `roles` (`id`, `roleName`) VALUES
                                           ('1', '普通用户'),
                                           ('2', '管理员')
    ON DUPLICATE KEY UPDATE `roleName` = VALUES(`roleName`);

-- 2. 用户表（核心登录注册表）
-- 幂等性：IF NOT EXISTS 确保表已存在时不重复创建
CREATE TABLE IF NOT EXISTS `users` (
                                       `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户主键ID',
    `email` varchar(100) NOT NULL COMMENT '用户邮箱（登录账号）',
    `password` varchar(255) NOT NULL COMMENT '加密后的密码（建议BCrypt）',
    `name` varchar(50) NOT NULL COMMENT '用户姓名',
    `roleId` varchar(10) NOT NULL DEFAULT '1' COMMENT '关联角色表ID',
    `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '账号状态：1=正常，0=禁用',
    `lastLoginAt` datetime DEFAULT NULL COMMENT '最后登录时间',
    `createdAt` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email` (`email`) COMMENT '邮箱唯一索引（防止重复注册）',
    KEY `idx_roleId` (`roleId`) COMMENT '角色ID索引',
    CONSTRAINT `fk_user_role` FOREIGN KEY (`roleId`) REFERENCES `roles` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- 3. 验证码表（存储发送的邮箱验证码）
-- 幂等性：IF NOT EXISTS 确保表已存在时不重复创建
CREATE TABLE IF NOT EXISTS `verificationCodes` (
                                                   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `email` varchar(100) NOT NULL COMMENT '接收验证码的邮箱',
    `code` varchar(10) NOT NULL COMMENT '验证码（如6位数字）',
    `type` tinyint(1) NOT NULL COMMENT '验证码类型：1=注册，2=找回密码',
    `expiredAt` datetime NOT NULL COMMENT '验证码过期时间（如10分钟）',
    `isUsed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已使用：0=未使用，1=已使用',
    `createdAt` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_email_type` (`email`, `type`) COMMENT '邮箱+类型索引（查询验证码用）',
    KEY `idx_expiredAt` (`expiredAt`) COMMENT '过期时间索引（清理过期验证码）'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱验证码表';

-- 4. Token存储表（关联用户与token，控制有效期）
-- 幂等性：IF NOT EXISTS 确保表已存在时不重复创建
CREATE TABLE IF NOT EXISTS `userTokens` (
                                            `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `token` varchar(255) NOT NULL COMMENT '用户登录生成的唯一token',
    `userId` bigint(20) NOT NULL COMMENT '关联用户表主键ID',
    `expiredAt` datetime NOT NULL COMMENT 'token过期时间（如2小时/7天）',
    `isValid` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'token是否有效：1=有效，0=失效（登出/过期）',
    `createdAt` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'token生成时间',
    `updatedAt` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'token状态更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token` (`token`) COMMENT 'token唯一索引，加速查询',
    KEY `idx_userId` (`userId`) COMMENT '用户ID索引，关联用户信息',
    KEY `idx_expiredAt` (`expiredAt`) COMMENT '过期时间索引，清理过期token',
    KEY `idx_isValid` (`isValid`) COMMENT '有效性索引，快速筛选有效token',
    CONSTRAINT `fk_token_user` FOREIGN KEY (`userId`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户Token存储表';