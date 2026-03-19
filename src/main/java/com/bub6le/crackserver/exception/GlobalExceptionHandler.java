package com.bub6le.crackserver.exception;

import com.bub6le.crackserver.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件大小超限: {}", e.getMessage());
        return Result.error("FILE_TOO_LARGE", "上传文件大小超过系统限制");
    }

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("系统内部异常", e);
        return Result.error("SYSTEM_ERROR", "系统内部错误，请稍后重试");
    }
}
