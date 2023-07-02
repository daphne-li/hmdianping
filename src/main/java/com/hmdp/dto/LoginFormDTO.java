package com.hmdp.dto;

import lombok.Data;

/**
 * 登陆列表
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
