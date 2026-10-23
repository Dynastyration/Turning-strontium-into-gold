package com.bngold.config;

import cn.hutool.core.convert.Convert;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private final byte[] key;
    private final int expireHours;

    public JwtUtil(@Value("${bn.jwt.secret}") String secret,
                   @Value("${bn.jwt.expire-hours}") int expireHours) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.expireHours = expireHours;
    }

    public String create(Long userId, String username) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", userId);
        payload.put("uname", username);
        payload.put("exp", System.currentTimeMillis() + expireHours * 3600_000L);
        return JWTUtil.createToken(payload, key);
    }

    /** 校验签名与有效期，失败返回 null。 */
    public AuthContext.CurrentUser parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            if (!JWTUtil.verify(token, key)) {
                return null;
            }
            JWT jwt = JWTUtil.parseToken(token).setKey(key);
            long exp = Convert.toLong(jwt.getPayload("exp"), 0L);
            if (exp > 0 && exp < System.currentTimeMillis()) {
                return null;
            }
            Long uid = Convert.toLong(jwt.getPayload("uid"));
            if (uid == null) {
                return null;
            }
            return new AuthContext.CurrentUser(uid, Convert.toStr(jwt.getPayload("uname")));
        } catch (Exception e) {
            return null;
        }
    }

    public int getExpireHours() {
        return expireHours;
    }
}
