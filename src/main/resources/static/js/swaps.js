/* Màn hình "Đổi ca & vắng": danh sách yêu cầu đổi ca, báo vắng đã ghi nhận,
   và các hộp thoại tạo yêu cầu / báo vắng (mở từ lịch trực). */
const SWAP_STATUS_PILL = { CHO_DUYET: 'warn', DA_DUYET: 'ok', TU_CHOI: 'bad' };

const Swaps = {
  async render(root) {
    root.innerHTML = '<p class="muted">Đang tải…</p>';
    try {
      const [swapList, absenceList] = await Promise.all([
        API.get('/api/swaps'), API.get('/api/absences')
      ]);
      root.innerHTML = this.html(swapList, absenceList);
      this.bind(root, swapList);
    } catch (e) {
      root.innerHTML = U.errorBox(e);
    }
  },

  html(swapList, absenceList) {
    const swapRows = swapList.length ? swapList.map(r => this.swapRow(r)).join('') :
      '<tr><td colspan="7" class="muted">Chưa có yêu cầu đổi ca nào.</td></tr>';
    const absRows = absenceList.length ? absenceList.map(a => `
      <tr><td>${U.esc(a.userCode)} · ${U.esc(a.userName)}</td><td>${U.dmy(a.from)} – ${U.dmy(a.to)}</td>
      <td>${U.esc(a.reason || '')}</td><td>${a.affectedShiftIds.length} ca</td></tr>`).join('') :
      '<tr><td colspan="4" class="muted">Chưa ghi nhận trường hợp vắng nào.</td></tr>';
    return `
    <div class="stack">
      <div class="card">
        <h2>Yêu cầu đổi ca</h2>
        <p class="hint">Tạo yêu cầu từ một ca trực ở màn hình Lịch trực. Phạm vi duyệt: NV-01 toàn hệ thống; NV-02 chỉ ca thuộc Đầm Sen.</p>
        <div class="table-wrap"><table>
          <thead><tr><th>Ca</th><th>Người gửi</th><th>Đề xuất thay</th><th>Trạng thái</th><th>Gửi lúc</th><th>Xử lý</th><th></th></tr></thead>
          <tbody>${swapRows}</tbody></table></div>
      </div>
      <div class="card">
        <h2>Báo vắng đã ghi nhận</h2>
        <div class="table-wrap"><table>
          <thead><tr><th>Nhân sự</th><th>Khoảng thời gian</th><th>Lý do</th><th>Ca bị ảnh hưởng</th></tr></thead>
          <tbody>${absRows}</tbody></table></div>
      </div>
    </div>`;
  },

  swapRow(r) {
    const pill = SWAP_STATUS_PILL[r.status] || '';
    const proc = r.processingMinutes != null ? U.hours(r.processingMinutes) + ' giờ (K4)' : '—';
    const actions = r.canApprove
      ? `<button class="btn small primary" data-act="approve" data-id="${r.id}">Duyệt</button>
         <button class="btn small" data-act="reject" data-id="${r.id}">Từ chối</button>`
      : '';
    return `<tr>
      <td>${U.esc(r.shiftSummary)}</td>
      <td>${U.esc(r.requesterCode)}${r.createdByManager ? ' <span class="pill warn">quản lý tạo</span>' : ''}</td>
      <td>${r.proposedUserCode ? U.esc(r.proposedUserCode) : '<span class="muted">chưa chọn</span>'}</td>
      <td><span class="pill ${pill}">${U.esc(r.statusLabel)}</span></td>
      <td>${U.esc(r.submittedAt)}</td>
      <td>${proc}${r.decisionNote ? '<div class="small muted">' + U.esc(r.decisionNote) + '</div>' : ''}</td>
      <td>${actions}</td></tr>`;
  },

  bind(root) {
    root.onclick = async ev => {
      const el = ev.target.closest('[data-act]');
      if (!el) return;
      const id = Number(el.dataset.id);
      if (el.dataset.act === 'approve') await this.decide(id, true);
      else if (el.dataset.act === 'reject') await this.decide(id, false);
    };
  },

  async decide(id, approve) {
    let note = '';
    if (!approve) {
      note = prompt('Lý do từ chối (không bắt buộc):') || '';
    }
    try {
      await API.post(`/api/swaps/${id}/${approve ? 'approve' : 'reject'}`, { note });
      U.toast(approve ? 'Đã duyệt yêu cầu đổi ca' : 'Đã từ chối yêu cầu');
      App.render();
    } catch (e) {
      U.toast(e.message, true);
    }
  },

  // ------------------------------------------------------------ tạo yêu cầu đổi ca (mở từ 1 ca trực)

  async openCreateForm(shift) {
    const m = U.openModal(`<h3>Gửi yêu cầu đổi ca</h3>
      <p>${U.esc(shift.branchName)} · ${DAY_NAMES[shift.dayOfWeek - 1]} ${U.dmy(shift.date)} · ${shift.start}–${shift.end}</p>
      <p>Người hiện tại: <b>${U.esc(shift.userName)}</b></p>
      <p class="muted">Đang tải danh sách người thay phù hợp…</p>`);
    let candidates = [];
    try {
      candidates = await API.get('/api/swaps/candidates/' + shift.id);
    } catch (e) {
      m.innerHTML = U.errorBox(e) + '<div class="modal-actions"><button class="btn" type="button" data-close>Đóng</button></div>';
      m.querySelector('[data-close]').onclick = () => U.closeModal();
      return;
    }
    const options = candidates.map(c => `
      <label style="display:flex;gap:8px;align-items:flex-start;border:1px solid var(--line);border-radius:8px;padding:8px;margin-top:6px">
        <input type="radio" name="cand" value="${c.userId}" ${!c.eligible ? 'disabled' : ''} style="margin-top:3px">
        <span>
          <b>${U.esc(c.userCode)}</b> · ${U.esc(c.titleLabel)} · ${U.esc(c.branchName)}
          ${c.eligible ? '<span class="pill ok">Đủ điều kiện</span>' : '<span class="pill bad">Không đủ điều kiện</span>'}
          <ul class="notes">${c.reasons.map(r => '<li>' + U.esc(r) + '</li>').join('')}</ul>
        </span>
      </label>`).join('') || '<p class="muted">Không tìm được người thay thế phù hợp. Yêu cầu quản lý xử lý thủ công.</p>';
    m.innerHTML = `
      <h3>Gửi yêu cầu đổi ca</h3>
      <p>${U.esc(shift.branchName)} · ${DAY_NAMES[shift.dayOfWeek - 1]} ${U.dmy(shift.date)} · ${shift.start}–${shift.end}</p>
      <form id="swapForm" novalidate>
        <label>Chọn người thay (chỉ ứng viên "Đủ điều kiện" duyệt được)</label>
        ${options}
        <label for="swapReason">Lý do</label>
        <input id="swapReason" maxlength="300" placeholder="Ví dụ: bận việc cá nhân">
        <div id="swapErr"></div>
        <p class="hint">Yêu cầu phải gửi trước giờ bắt đầu ca ít nhất 24 giờ.</p>
        <div class="modal-actions">
          <button class="btn" type="button" data-close>Hủy</button>
          <button class="btn primary" type="submit">Gửi yêu cầu</button>
        </div>
      </form>`;
    m.querySelector('[data-close]').onclick = () => U.closeModal();
    m.querySelector('#swapForm').onsubmit = async ev => {
      ev.preventDefault();
      const chosen = m.querySelector('input[name="cand"]:checked');
      const body = {
        shiftId: shift.id,
        proposedUserId: chosen ? Number(chosen.value) : null,
        reason: m.querySelector('#swapReason').value
      };
      try {
        await API.post('/api/swaps', body);
        U.closeModal();
        U.toast('Đã gửi yêu cầu đổi ca, chờ quản lý duyệt');
        if (State.view === 'swaps') App.render(); else App.render();
      } catch (e) {
        m.querySelector('#swapErr').innerHTML = U.errorBox(e);
      }
    };
  },

  // ------------------------------------------------------------ báo vắng

  openAbsenceForm(defaultUserId) {
    const me = State.me;
    const canPickOther = me.role === 'ADMIN' || me.role === 'BRANCH_MANAGER';
    const eligibleUsers = State.meta.users.filter(u => me.role === 'ADMIN' || u.branchId === me.branchId || u.id === me.id);
    const userField = canPickOther
      ? `<select id="absUser">${eligibleUsers.map(u => `<option value="${u.id}" ${u.id === defaultUserId ? 'selected' : ''}>${U.esc(u.displayName)}</option>`).join('')}</select>`
      : `<input value="${U.esc(me.displayName)}" disabled><input type="hidden" id="absUserFixed" value="${me.id}">`;
    const m = U.openModal(`
      <h3>Báo vắng</h3>
      <form id="absForm" novalidate>
        <label for="absUser">Nhân sự</label>
        ${userField}
        <div class="row2">
          <div><label for="absFrom">Từ ngày</label><input id="absFrom" type="date" required></div>
          <div><label for="absTo">Đến ngày</label><input id="absTo" type="date" required></div>
        </div>
        <label for="absReason">Lý do</label>
        <input id="absReason" maxlength="300" placeholder="Ví dụ: nghỉ ốm">
        <div id="absErr"></div>
        <div class="modal-actions">
          <button class="btn" type="button" data-close>Hủy</button>
          <button class="btn primary" type="submit">Ghi nhận</button>
        </div>
      </form>`);
    m.querySelector('[data-close]').onclick = () => U.closeModal();
    m.querySelector('#absForm').onsubmit = async ev => {
      ev.preventDefault();
      const userId = canPickOther ? Number(m.querySelector('#absUser').value) : Number(m.querySelector('#absUserFixed').value);
      const body = {
        userId, from: m.querySelector('#absFrom').value, to: m.querySelector('#absTo').value,
        reason: m.querySelector('#absReason').value
      };
      try {
        const res = await API.post('/api/absences', body);
        U.closeModal();
        this.showAbsenceResult(res);
      } catch (e) {
        m.querySelector('#absErr').innerHTML = U.errorBox(e);
      }
    };
  },

  async showAbsenceResult(res) {
    let body = `<p>Đã ghi nhận vắng cho <b>${U.esc(res.userCode)}</b> (${U.dmy(res.from)} – ${U.dmy(res.to)}).</p>`;
    if (res.affectedShiftIds.length === 0) {
      body += '<p class="muted">Không có ca Đủ người nào của nhân sự này trong khoảng thời gian trên.</p>';
    } else {
      body += `<p><b>${res.affectedShiftIds.length} ca</b> chuyển sang trạng thái "Bị ảnh hưởng do vắng". Gợi ý người thay cho từng ca:</p>`;
      for (const shiftId of res.affectedShiftIds) {
        try {
          const cands = await API.get('/api/swaps/candidates/' + shiftId);
          const top = cands.filter(c => c.eligible).slice(0, 3);
          body += `<div class="card" style="padding:10px;margin-top:8px">
            <b>Ca #${shiftId}</b>
            ${top.length ? '<ul class="notes">' + top.map(c => '<li>' + U.esc(c.userCode) + ' — ' + c.reasons.filter(r=>true)[0] + '</li>').join('') + '</ul>'
              : '<p class="muted" style="margin:6px 0 0">Không tìm được người thay thế. Yêu cầu quản lý xử lý thủ công.</p>'}
          </div>`;
        } catch (e) { /* bỏ qua lỗi tải gợi ý cho một ca */ }
      }
    }
    const m = U.openModal(`<h3>Kết quả báo vắng</h3>${body}
      <div class="modal-actions"><button class="btn primary" type="button" data-close>Đóng</button></div>`);
    m.querySelector('[data-close]').onclick = () => { U.closeModal(); App.render(); };
  }
};
