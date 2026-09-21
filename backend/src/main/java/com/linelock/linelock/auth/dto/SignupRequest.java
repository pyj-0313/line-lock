package com.linelock.linelock.auth.dto;

import lombok.Getter;

@Getter 
public class SignupRequest {
    
    private String loginId;
    private String password;
    private String name;
}
