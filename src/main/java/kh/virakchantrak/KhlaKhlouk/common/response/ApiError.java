package kh.virakchantrak.KhlaKhlouk.common.response;

import java.util.Map;

public record ApiError(
        String code,
        String message,
        Map<String, String> errors
) { }
