package com.example.pulsediagnosis;

public class GuardianUser {

    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role;

    // Required by Firebase
    public GuardianUser() {
    }

    public GuardianUser(
            String uid,
            String name,
            String email,
            String phone,
            String role
    ) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}