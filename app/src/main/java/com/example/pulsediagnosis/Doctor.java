package com.example.pulsediagnosis;

public class Doctor {
    public String name;
    public String email;

    public String about;
    public String contact;

    public String id;
    public String password;

    public Doctor() {
        // Required empty constructor
    }

    public Doctor(String name, String email, String contact, String about,  String id, String password) {
        this.name = name;
        this.email = email;
        this.about = about;
        this.contact = contact;
        this.id = id;
        this.password = password;
    }
}

