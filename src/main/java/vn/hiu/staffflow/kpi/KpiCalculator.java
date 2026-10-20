package vn.hiu.staffflow.kpi;

import java.time.LocalDate;

import vn.hiu.staffflow.dto.Views.KpiResult;

/**
 * Mỗi KPI (K1-K6) là một class riêng, có phương thức tính riêng, nguồn dữ liệu riêng.
 * compute(monday) tính cho tuần bắt đầu từ thứ Hai đó; không dữ liệu -> KpiStatus.NA (không hiện số 0 giả).
 */
public interface KpiCalculator {
    KpiResult compute(LocalDate monday);
}
