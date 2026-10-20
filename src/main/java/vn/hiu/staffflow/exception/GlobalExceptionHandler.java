package vn.hiu.staffflow.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return body(e.status(), e.getMessage(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return body(422, "Dữ liệu không hợp lệ", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(400, "Nội dung yêu cầu không đọc được (kiểm tra định dạng JSON, ngày yyyy-MM-dd, giờ HH:mm)",
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception e) {
        return body(500, "Lỗi hệ thống", List.of());
    }

    private ResponseEntity<Map<String, Object>> body(int status, String message, List<String> details) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("error", message);
        m.put("details", details);
        return ResponseEntity.status(status).body(m);
    }
}
