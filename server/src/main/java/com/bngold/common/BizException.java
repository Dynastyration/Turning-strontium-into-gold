package com.bngold.common;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public static BizException of(int code, String msg) {
        return new BizException(code, msg);
    }

    public static BizException notFound(String what) {
        return new BizException(30001, what + "不存在");
    }

    public static BizException badRequest(String msg) {
        return new BizException(10001, msg);
    }
}
