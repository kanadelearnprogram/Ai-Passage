package com.kanade.aipassage.common.feign;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.exception.BusinessException;

/**
 * Feign 响应解包：非 0 错误码抛业务异常，保留跨服务的错误信息
 */
public class FeignResponseUtils {

    public static <T> T unwrap(BaseResponse<T> response) {
        if (response == null) {
            throw new BusinessException(50000, "内部服务无响应");
        }
        if (response.getCode() != 0) {
            throw new BusinessException(response.getCode(), response.getMessage());
        }
        return response.getData();
    }

    private FeignResponseUtils() {
    }
}
