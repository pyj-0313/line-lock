package com.linelock.linelock.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter 
@AllArgsConstructor 
public class SignupRequest {
    
    private String loginId;
    private String password;
    private String name;
}
