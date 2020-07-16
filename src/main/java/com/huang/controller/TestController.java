package com.huang.controller;

import com.huang.starter.MyStarterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private MyStarterService myStarterService;

    @GetMapping("/teststarter")
    public String test(){
        return myStarterService.getMsg();
    }
}
