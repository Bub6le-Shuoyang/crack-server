package com.bub6le.crackserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("userTokens")
public class UserToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String token;
    private Long userId;
    private LocalDateTime expiredAt;
    private Integer isValid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
