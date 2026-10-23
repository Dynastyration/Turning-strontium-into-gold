package com.bngold.service;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bngold.common.BizException;
import com.bngold.config.JwtUtil;
import com.bngold.entity.SysUser;
import com.bngold.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    public Map<String, Object> login(String username, String password) {
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (u == null || !SecureUtil.md5(password).equalsIgnoreCase(u.getPassword())) {
            throw new BizException(40102, "账号或密码错误");
        }
        if (!Integer.valueOf(1).equals(u.getStatus())) {
            throw new BizException(40103, "账号已停用");
        }

        SysUser patch = new SysUser();
        patch.setId(u.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);

        String token = jwtUtil.create(u.getId(), u.getUsername());
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", u.getId());
        user.put("username", u.getUsername());
        user.put("realName", u.getRealName());
        user.put("projectScope", u.getProjectScope());
        user.put("perms", perms(u.getId()));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("accessToken", token);
        r.put("refreshToken", token);
        r.put("expiresIn", jwtUtil.getExpireHours() * 3600);
        r.put("user", user);
        return r;
    }

    public List<String> perms(Long userId) {
        return jdbc.queryForList("""
                SELECT r.perms FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND r.perms IS NOT NULL
                """, String.class, userId)
                .stream()
                .flatMap(s -> java.util.Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    public Map<String, Object> me(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(40101, "登录已过期");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        m.put("projectScope", u.getProjectScope());
        m.put("perms", perms(userId));
        return m;
    }
}
