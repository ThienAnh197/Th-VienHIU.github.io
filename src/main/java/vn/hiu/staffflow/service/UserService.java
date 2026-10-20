package vn.hiu.staffflow.service;

import java.util.List;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.dto.Views.UserView;
import vn.hiu.staffflow.repository.OrgRepository;

@Service
public class UserService {
    private final OrgRepository org;

    public UserService(OrgRepository org) {
        this.org = org;
    }

    public UserView view(AppUser u) {
        String branch = org.branch(u.branchId()).map(Branch::name).orElse("?");
        return new UserView(u.id(), u.code(), u.displayName(), u.titleGroup().name(), u.titleGroup().label(),
                u.role().name(), u.branchId(), branch);
    }

    public List<UserView> list() {
        return org.users().stream().map(this::view).toList();
    }
}
