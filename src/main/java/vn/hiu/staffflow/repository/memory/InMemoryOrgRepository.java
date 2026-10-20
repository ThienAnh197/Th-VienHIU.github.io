package vn.hiu.staffflow.repository.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.Holiday;
import vn.hiu.staffflow.repository.OrgRepository;

@Repository
@Profile("memory")
public class InMemoryOrgRepository implements OrgRepository {
    private volatile List<Branch> branches = List.of();
    private volatile List<AppUser> users = List.of();
    private volatile List<Holiday> holidays = List.of();

    /** Chỉ dùng cho DemoDataSeeder / test. */
    public void load(List<Branch> branches, List<AppUser> users, List<Holiday> holidays) {
        this.branches = new ArrayList<>(branches);
        this.users = new ArrayList<>(users);
        this.holidays = new ArrayList<>(holidays);
    }

    @Override
    public List<Branch> branches() {
        return branches;
    }

    @Override
    public Optional<Branch> branch(long id) {
        return branches.stream().filter(b -> b.id() == id).findFirst();
    }

    @Override
    public List<AppUser> users() {
        return users;
    }

    @Override
    public Optional<AppUser> user(long id) {
        return users.stream().filter(u -> u.id() == id).findFirst();
    }

    @Override
    public Optional<AppUser> userByCode(String code) {
        return users.stream().filter(u -> u.code().equalsIgnoreCase(code)).findFirst();
    }

    @Override
    public List<Holiday> holidays() {
        return holidays;
    }
}
