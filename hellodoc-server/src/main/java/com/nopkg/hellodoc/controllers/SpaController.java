package com.nopkg.hellodoc.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = {
            "/login",
            "/register",
            "/view/**",
            "/kb/**",
            "/shared",
            "/favorites",
            "/recent",
            "/admin",
            "/m",
            "/m/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
