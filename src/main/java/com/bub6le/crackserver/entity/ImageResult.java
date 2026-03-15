
package com.bub6le.crackserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_results")
public class ImageResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long imageId;
    private String label;
    private Float score;
    private Float x;
    private Float y;
    private Float width;
    private Float height;
    private Integer classId;
    private String modelName;
    private LocalDateTime createdAt;
}
