package com.bub6le.crackserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("verificationCodes")
public class VerificationCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String code;
    /**
     * 1=register, 2=reset password
     */
    private Integer type;
    private LocalDateTime expiredAt;
    private Integer isUsed;
    private LocalDateTime createdAt;
}
