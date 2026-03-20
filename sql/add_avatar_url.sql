-- 9. Add avatarUrl to users table
ALTER TABLE `users` ADD COLUMN `avatarUrl` varchar(500) DEFAULT NULL COMMENT '用户头像地址' AFTER `name`;
