package com.bub6le.crackserver.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用返回结果封装，继承 HashMap 以保持与现有前端完全兼容
 */
public class Result extends HashMap<String, Object> {

    public Result() {
    }

    public static Result success() {
        Result result = new Result();
        result.put("ok", true);
        return result;
    }

    public static Result success(String message) {
        Result result = new Result();
        result.put("ok", true);
        result.put("message", message);
        return result;
    }

    public static Result success(Map<String, Object> data) {
        Result result = new Result();
        result.put("ok", true);
        result.putAll(data);
        return result;
    }

    public static Result successData(Object data) {
        Result result = new Result();
        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    public static Result error(String message) {
        Result result = new Result();
        result.put("error", true);
        result.put("message", message);
        return result;
    }

    public static Result error(String code, String message) {
        Result result = new Result();
        result.put("error", true);
        result.put("code", code);
        result.put("message", message);
        return result;
    }

    @Override
    public Result put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
