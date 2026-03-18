package com.bub6le.crackserver.dto;

import lombok.Data;

@Data
public class AdminUpdateUserRequest {
    private String name;
    private String email;
    private String roleId;
    private Integer status;
}
