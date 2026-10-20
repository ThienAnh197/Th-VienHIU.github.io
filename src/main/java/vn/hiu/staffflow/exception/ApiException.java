package vn.hiu.staffflow.exception;

import java.util.List;

/** Lỗi nghiệp vụ có mã HTTP rõ ràng; GlobalExceptionHandler chuyển thành JSON thống nhất. */
public class ApiException extends RuntimeException {
    private final int status;
    private final List<String> details;

    public ApiException(int status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public int status() {
        return status;
    }

    public List<String> details() {
        return details;
    }

    public static ApiException unauthorized(String m) {
        return new ApiException(401, m, null);
    }

    public static ApiException forbidden(String m) {
        return new ApiException(403, m, null);
    }

    public static ApiException notFound(String m) {
        return new ApiException(404, m, null);
    }

    public static ApiException conflict(String m) {
        return new ApiException(409, m, null);
    }

    public static ApiException unprocessable(String m, List<String> details) {
        return new ApiException(422, m, details);
    }
}
