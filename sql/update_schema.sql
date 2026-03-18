
-- 7. 图片检测结果表（存储模型检测出的异常信息）
CREATE TABLE IF NOT EXISTS `image_results` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '结果主键ID',
    `imageId` bigint(20) NOT NULL COMMENT '关联图片表ID',
    `label` varchar(50) NOT NULL COMMENT '异常类别（如P0, P1）',
    `score` float NOT NULL COMMENT '置信度分数',
    `x` float NOT NULL COMMENT '目标框X坐标',
    `y` float NOT NULL COMMENT '目标框Y坐标',
    `width` float NOT NULL COMMENT '目标框宽度',
    `height` float NOT NULL COMMENT '目标框高度',
    `classId` int(11) NOT NULL COMMENT '类别索引',
    `createdAt` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间',
    PRIMARY KEY (`id`),
    KEY `idx_imageId` (`imageId`),
    CONSTRAINT `fk_result_image` FOREIGN KEY (`imageId`) REFERENCES `images` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片检测结果表';

mysql -u root -p -e "ALTER TABLE users ADD COLUMN avatar_url VARCHAR(255) COMMENT '用户头像URL';"