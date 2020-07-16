package com.huang.starter;

import lombok.Data;

@Data
public class MyStarterService {
    private String name;

    private String projectName;

    public String getMsg(){
        return "Author is " + name + ", Project Name is " + projectName;
    }
}
