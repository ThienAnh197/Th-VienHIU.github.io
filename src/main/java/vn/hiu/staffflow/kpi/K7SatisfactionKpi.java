package vn.hiu.staffflow.kpi;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import vn.hiu.staffflow.domain.Domain.SurveyPeriod;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.service.SurveyService;

/**
 * K7 = điểm hài lòng trung bình (3 câu, thang 1-5) của khảo sát nộp ở tuần 4 hoặc tuần 8 kể từ tuần triển khai.
 * Mục tiêu ≥ 3,5 điểm. Nhập tay qua form khảo sát (survey_responses), không tính tự động từ ca/nhiệm vụ.
 * Ngoài đúng hai tuần mốc (4 và 8), trả về N/A vì chưa đến kỳ khảo sát.
 */
@Component
public class K7SatisfactionKpi implements KpiCalculator {
    private final SurveyService surveys;
    private final LocalDate mainWeek;

    public K7SatisfactionKpi(SurveyService surveys,
                             @Value("${staffflow.demo-week-start:2026-10-19}") String mainWeek) {
        this.surveys = surveys;
        this.mainWeek = LocalDate.parse(mainWeek);
    }

    @Override
    public KpiResult compute(LocalDate monday) {
        SurveyPeriod period = periodFor(monday);
        if (period == null) {
            return new KpiResult("K7", "Hài lòng nhân sự (khảo sát)", "N/A", null, "≥ 3,5 điểm", KpiStatus.NA,
                    "Tuần này không phải tuần 4 hoặc tuần 8 kể từ tuần triển khai (" + mainWeek + ") nên chưa đến kỳ khảo sát",
                    formula());
        }
        Double avg = surveys.averageFor(period);
        int n = surveys.countFor(period);
        if (avg == null) {
            return new KpiResult("K7", "Hài lòng nhân sự (khảo sát)", "N/A", null, "≥ 3,5 điểm", KpiStatus.NA,
                    "Chưa có phản hồi khảo sát nào cho " + period.label(), formula());
        }
        KpiStatus status = avg >= 3.5 ? KpiStatus.DAT : KpiStatus.CHUA_DAT;
        double rounded = Math.round(avg * 10) / 10.0;
        return new KpiResult("K7", "Hài lòng nhân sự (khảo sát)", rounded + " điểm", rounded, "≥ 3,5 điểm", status,
                period.label() + " · " + n + " phản hồi", formula());
    }

    private SurveyPeriod periodFor(LocalDate monday) {
        if (monday.isBefore(mainWeek)) {
            return null;
        }
        int weekNo = (int) ChronoUnit.WEEKS.between(mainWeek, monday) + 1;
        if (weekNo == 4) {
            return SurveyPeriod.TUAN_4;
        }
        if (weekNo == 8) {
            return SurveyPeriod.TUAN_8;
        }
        return null;
    }

    private static String formula() {
        return "K7 = trung bình cộng (câu 1 + câu 2 + câu 3) / 3 của mọi phản hồi khảo sát trong kỳ (tuần 4 hoặc tuần 8), thang điểm 1-5";
    }
}
