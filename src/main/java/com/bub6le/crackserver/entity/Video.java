package com.bub6le.crackserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("videos")
public class Video {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private Double duration;
    private String coverPath;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
