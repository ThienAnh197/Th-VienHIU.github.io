package vn.hiu.staffflow.controller;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.dto.Views.ImportSummary;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.ImportService;

/** Nhập CSV ca trực và nhiệm vụ (mục 2, mục 8). Mỗi dòng được kiểm tra và báo lỗi riêng. */
@RestController
@RequestMapping("/api/import")
public class ImportController {
    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping("/shifts")
    public ImportSummary shifts(@RequestParam("file") MultipartFile file,
                                @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return service.importShifts(actor, stream(file));
    }

    @PostMapping("/tasks")
    public ImportSummary tasks(@RequestParam("file") MultipartFile file,
                               @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return service.importTasks(actor, stream(file));
    }

    private static java.io.InputStream stream(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.unprocessable("Chưa chọn tệp CSV", java.util.List.of());
        }
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
