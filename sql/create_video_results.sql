CREATE TABLE IF NOT EXISTS `video_results` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `video_id` BIGINT NOT NULL COMMENT '关联视频ID',
    `frame_number` INT NOT NULL COMMENT '异常出现的帧号',
    `timestamp_sec` DOUBLE NOT NULL COMMENT '异常出现在视频中的时间（秒）',
    `label` VARCHAR(50) NOT NULL COMMENT '检测标签类别，例如P0,P1等，无异常为NORMAL',
    `score` FLOAT NOT NULL COMMENT '置信度得分',
    `x` FLOAT NOT NULL COMMENT '检测框X坐标',
    `y` FLOAT NOT NULL COMMENT '检测框Y坐标',
    `width` FLOAT NOT NULL COMMENT '检测框宽度',
    `height` FLOAT NOT NULL COMMENT '检测框高度',
    `class_id` INT NOT NULL COMMENT '类别ID',
    `model_name` VARCHAR(100) COMMENT '使用的模型名称',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_video_id` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频检测结果表';
