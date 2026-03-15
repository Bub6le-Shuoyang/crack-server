
-- 8. Add modelName to image_results table
ALTER TABLE `image_results` ADD COLUMN `modelName` varchar(50) DEFAULT 'YOLOv8' COMMENT '模型名称';
