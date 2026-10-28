package com.shop.api.user.domain;

import java.time.LocalDateTime;

/** tb_users LEFT JOIN user_roles 조회 결과. passwd 는 인증 처리에만 쓰고 응답 DTO 로 옮기지 않는다. */
public class User {
    private String userId;
    private String passwd;
    private String userName;
    private String gender;
    private String birth;
    private String email;
    private String phone;
    private String zipCode;
    private String addr;
    private String addrDtl;
    private String roleId;
    private String roleName;
    private LocalDateTime createAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPasswd() { return passwd; }
    public void setPasswd(String passwd) { this.passwd = passwd; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getBirth() { return birth; }
    public void setBirth(String birth) { this.birth = birth; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getAddr() { return addr; }
    public void setAddr(String addr) { this.addr = addr; }
    public String getAddrDtl() { return addrDtl; }
    public void setAddrDtl(String addrDtl) { this.addrDtl = addrDtl; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public LocalDateTime getCreateAt() { return createAt; }
    public void setCreateAt(LocalDateTime createAt) { this.createAt = createAt; }
}
