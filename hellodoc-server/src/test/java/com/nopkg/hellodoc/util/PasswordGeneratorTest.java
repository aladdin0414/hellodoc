package com.nopkg.hellodoc.util;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
public class PasswordGeneratorTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void generatePassword() {
        // 你可以在这里修改要生成的密码
        String[] passwordsToEncrypt = {"admin123", "123456", "password", "11111"};

        System.out.println("================= Password Generator =================");
        System.out.println("Algorithm: " + passwordEncoder.getClass().getSimpleName());
        
        for (String rawPassword : passwordsToEncrypt) {
            String encodedPassword = passwordEncoder.encode(rawPassword);
            System.out.println("--------------------------------------------------");
            System.out.println("Raw Password    : " + rawPassword);
            System.out.println("Encoded Password: " + encodedPassword);
        }
        System.out.println("======================================================");
    }
}
