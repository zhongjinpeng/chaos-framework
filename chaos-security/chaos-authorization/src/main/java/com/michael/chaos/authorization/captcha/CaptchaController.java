package com.michael.chaos.authorization.captcha;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图形验证码公开挑战端点。
 */
@RestController
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    /**
     * 创建新的图形验证码。
     */
    @GetMapping("${chaos.authorization.captcha.path:/api/v1/auth/captcha}")
    public ResponseEntity<CaptchaChallenge> create() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(captchaService.create());
    }
}
