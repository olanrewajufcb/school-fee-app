package com.fee.app.schoolfeeapp.auth.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolFeeUser {
    private UUID userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private UUID schoolId;
    private String schoolName;
    private String userType;
    private Set<String> roles;
    private List<UUID> childrenIds;
    
    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(userType);
    }
    
    public boolean isSchoolAdmin() {
        return "SCHOOL_ADMIN".equals(userType) || "SCHOOL_OWNER".equals(userType);
    }
    
    public boolean isAccountant() {
        return "ACCOUNTANT".equals(userType);
    }
    
    public boolean isParent() {
        return "PARENT".equals(userType);
    }
    
    public boolean isTeacher() {
        return "TEACHER".equals(userType);
    }
}