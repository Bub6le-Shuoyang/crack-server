package com.bub6le.crackserver.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String name;
    private String email;
    private String roleId;
    private Integer status;
}
