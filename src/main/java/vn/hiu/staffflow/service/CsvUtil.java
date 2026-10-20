package vn.hiu.staffflow.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc/ghi CSV theo RFC 4180 tối giản, tự viết thay vì phụ thuộc Apache Commons CSV (mục 2: "hoặc tương đương")
 * để tránh thêm một thư viện chưa kiểm chứng được trong môi trường lập trình hiện tại — GIẢ ĐỊNH BỔ SUNG, cần
 * nhóm cân nhắc đổi sang Apache Commons CSV nếu muốn khi triển khai thật. Ghi UTF-8 có BOM để Excel mở đúng
 * tiếng Việt có dấu (mục 2, mục 11 - T6).
 */
public final class CsvUtil {
    private static final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    private CsvUtil() {
    }

    public static byte[] write(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escape(row[i]));
            }
            sb.append("\r\n");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(BOM);
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    private static String escape(String v) {
        if (v == null) {
            return "";
        }
        boolean needsQuote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (!needsQuote) {
            return v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** Đọc toàn bộ CSV (có hoặc không có BOM) thành danh sách dòng, mỗi dòng là mảng ô. */
    public static List<String[]> parse(InputStream in) {
        String text;
        try {
            byte[] bytes = in.readAllBytes();
            int offset = (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                    && (bytes[2] & 0xFF) == 0xBF) ? 3 : 0;
            text = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<String[]> rows = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    cur.add(field.toString());
                    field.setLength(0);
                } else if (c == '\r') {
                    // bỏ qua, xử lý ở '\n'
                } else if (c == '\n') {
                    cur.add(field.toString());
                    field.setLength(0);
                    rows.add(cur.toArray(new String[0]));
                    cur = new ArrayList<>();
                } else {
                    field.append(c);
                }
            }
            i++;
        }
        if (field.length() > 0 || !cur.isEmpty()) {
            cur.add(field.toString());
            rows.add(cur.toArray(new String[0]));
        }
        rows.removeIf(r -> r.length == 1 && r[0].isBlank());
        return rows;
    }
}
