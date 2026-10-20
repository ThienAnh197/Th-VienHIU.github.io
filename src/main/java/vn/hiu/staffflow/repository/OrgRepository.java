package vn.hiu.staffflow.repository;

import java.util.List;
import java.util.Optional;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.Holiday;

/** Dữ liệu danh mục: cơ sở, nhân sự, ngày đóng cửa. Phase 5: cài đặt bằng JdbcTemplate. */
public interface OrgRepository {
    List<Branch> branches();

    Optional<Branch> branch(long id);

    List<AppUser> users();

    Optional<AppUser> user(long id);

    Optional<AppUser> userByCode(String code);

    List<Holiday> holidays();
}
