/* Màn hình Lịch trực: 3 cơ sở, theo tuần, kèm thanh bao phủ giờ-cơ sở. */
const DAY_NAMES = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật'];

const Schedule = {
  data: null,

  async render(root) {
    root.innerHTML = '<p class="muted">Đang tải lịch trực…</p>';
    try {
      const q = new URLSearchParams({ week: State.week });
      if (State.branchId) q.set('branchId', State.branchId);
      this.data = await API.get('/api/schedule?' + q.toString());
      root.innerHTML = this.html(this.data);
      this.bind(root);
    } catch (e) {
      root.innerHTML = U.errorBox(e);
    }
  },

  meterClass(p) { return p == null ? 'low' : p < 50 ? 'low' : p < 95 ? 'mid' : 'high'; },

  canAdd(branchId) {
    const me = State.me;
    return me.role === 'ADMIN' || (me.role === 'BRANCH_MANAGER' && me.branchId === branchId);
  },

  html(d) {
    const branchOpts = ['<option value="">Cả 3 cơ sở</option>']
      .concat(State.meta.branches.map(b => `<option value="${b.id}" ${String(State.branchId) === String(b.id) ? 'selected' : ''}>${U.esc(b.name)}</option>`)).join('');
    const missing = d.requiredMinutes - d.coveredMinutes;
    const notes = d.notes.map((n, i) => `<li class="${n.startsWith('Chưa phủ') ? 'alert' : ''}">${U.esc(n)}</li>`).join('');

    return `
    <div class="stack">
      <div class="card toolbar">
        <button class="btn" data-act="prev" aria-label="Tuần trước">‹ Tuần trước</button>
        <div class="week-label">Tuần ${U.dm(d.weekStart)} – ${U.dmy(d.weekEnd)}
          ${d.hasTestData ? '<span class="pill test">Dữ liệu kiểm thử</span>' : ''}</div>
        <button class="btn" data-act="next" aria-label="Tuần sau">Tuần sau ›</button>
        <span class="grow"></span>
        <label class="small" for="weekPick" style="margin:0">Chọn ngày</label>
        <input id="weekPick" type="date" value="${U.esc(d.weekStart)}">
        <label class="small" for="branchPick" style="margin:0">Cơ sở</label>
        <select id="branchPick">${branchOpts}</select>
      </div>

      <div class="card">
        <div class="summary">
          <div class="stat"><div class="num">${U.hours(d.requiredMinutes)}</div><div class="lbl">Giờ-cơ sở cần phủ (giờ mở cửa)</div></div>
          <div class="stat"><div class="num">${U.hours(d.coveredMinutes)}</div><div class="lbl">Giờ-cơ sở đã có người phụ trách</div></div>
          <div class="stat"><div class="num">${U.hours(Math.max(missing, 0))}</div><div class="lbl">Giờ-cơ sở còn thiếu</div></div>
          <div class="stat"><div class="num">${U.pct(d.coveragePct)}</div><div class="lbl">Mức bao phủ (K1, mục tiêu ≥ 95%)</div></div>
        </div>
        <div class="meter" role="img" aria-label="Mức bao phủ ${U.pct(d.coveragePct)}"><span class="${this.meterClass(d.coveragePct)}" style="width:${Math.min(d.coveragePct || 0, 100)}%"></span></div>
        <ul class="notes" style="margin-top:10px">${notes}</ul>
      </div>

      <div class="legend">
        <span><i style="background:var(--ok)"></i>Đủ người</span>
        <span><i style="background:var(--bad)"></i>Trống (chưa có người)</span>
        <span><i style="background:#d98e04"></i>Bị ảnh hưởng do vắng</span>
      </div>

      ${d.branches.map(b => this.branchHtml(b)).join('')}
    </div>`;
  },

  branchHtml(b) {
    const cls = this.meterClass(b.coveragePct);
    const cols = b.days.map(day => {
      const shifts = b.shifts.filter(s => s.date === day.date);
      const chips = shifts.map(s => this.shiftHtml(s)).join('');
      const add = (!day.closed && this.canAdd(b.branchId))
        ? `<button class="add-shift" data-act="add" data-branch="${b.branchId}" data-date="${day.date}">+ Thêm ca</button>` : '';
      return `<div class="day ${day.closed ? 'closed' : ''}">
        <div class="day-head"><b>${DAY_NAMES[day.dayOfWeek - 1]} ${U.dm(day.date)}</b><span>${U.esc(day.hours)}</span></div>
        ${chips}${add}</div>`;
    }).join('');
    return `
    <section class="card" aria-label="Cơ sở ${U.esc(b.name)}">
      <div class="branch-head">
        <h3>${U.esc(b.name)} <span class="muted small">· ${U.esc(b.address)}</span></h3>
        <div>${b.emptyShifts > 0 ? `<span class="pill bad">${b.emptyShifts} ca chưa có người</span>` : '<span class="pill ok">Không có ca trống</span>'}</div>
      </div>
      ${b.note ? `<p class="small" style="color:var(--warn);margin:2px 0 6px">⚠ ${U.esc(b.note)}</p>` : ''}
      <div class="small muted">Có người ${U.hours(b.coveredMinutes)} / ${U.hours(b.requiredMinutes)} giờ · <b>${U.pct(b.coveragePct)}</b></div>
      <div class="meter"><span class="${cls}" style="width:${Math.min(b.coveragePct || 0, 100)}%"></span></div>
      <div class="week-grid">${cols}</div>
    </section>`;
  },

  shiftHtml(s) {
    const who = s.userCode ? U.esc(s.userCode) : '<span class="tag">Chưa có người</span>';
    const tag = s.status === 'BI_ANH_HUONG' ? '<div class="tag">Bị ảnh hưởng do vắng</div>' : '';
    return `<button class="shift ${s.status}" data-act="open" data-id="${s.id}">
      <div class="t">${s.start}–${s.end}</div><div class="w">${who}</div>${tag}</button>`;
  },

  bind(root) {
    root.onclick = ev => {
      const el = ev.target.closest('[data-act]');
      if (!el) return;
      const act = el.dataset.act;
      if (act === 'prev') { State.week = U.addDays(State.week, -7); App.render(); }
      else if (act === 'next') { State.week = U.addDays(State.week, 7); App.render(); }
      else if (act === 'add') this.openForm({ branchId: Number(el.dataset.branch), date: el.dataset.date });
      else if (act === 'open') this.openShift(Number(el.dataset.id));
    };
    root.querySelector('#weekPick').onchange = ev => {
      if (ev.target.value) { State.week = U.mondayOf(ev.target.value); App.render(); }
    };
    root.querySelector('#branchPick').onchange = ev => {
      State.branchId = ev.target.value ? Number(ev.target.value) : null; App.render();
    };
  },

  findShift(id) {
    for (const b of this.data.branches) {
      const s = b.shifts.find(x => x.id === id);
      if (s) return s;
    }
    return null;
  },

  openShift(id) {
    const s = this.findShift(id);
    if (!s) return;
    const isOwner = s.userId === State.me.id;
    const isManager = s.canManage;
    const canSwap = (isOwner || isManager) && s.userId != null;
    const canReportAbsence = isOwner || isManager;
    const m = U.openModal(`
      <h3>Ca trực ${s.start}–${s.end}</h3>
      <p>${U.esc(s.branchName)} · ${DAY_NAMES[s.dayOfWeek - 1]} ${U.dmy(s.date)}</p>
      <p>Người phụ trách: <b>${U.esc(s.userName || 'Chưa có người')}</b></p>
      <p>Trạng thái: <b>${U.esc(s.statusLabel)}</b></p>
      ${s.note ? `<p class="muted">${U.esc(s.note)}</p>` : ''}
      <div class="modal-actions">
        ${isManager ? '<button class="btn" type="button" data-act="edit">Sửa ca</button>' : ''}
        ${canSwap ? '<button class="btn" type="button" data-act="swap">Gửi yêu cầu đổi ca</button>' : ''}
        ${canReportAbsence && s.userId ? '<button class="btn" type="button" data-act="absent">Báo vắng</button>' : ''}
        <button class="btn" type="button" data-close>Đóng</button>
      </div>`);
    m.querySelector('[data-close]').onclick = () => U.closeModal();
    const edit = m.querySelector('[data-act="edit"]');
    if (edit) edit.onclick = () => this.openForm(s);
    const swap = m.querySelector('[data-act="swap"]');
    if (swap) swap.onclick = () => { U.closeModal(); Swaps.openCreateForm(s); };
    const abs = m.querySelector('[data-act="absent"]');
    if (abs) abs.onclick = () => { U.closeModal(); Swaps.openAbsenceForm(s.userId); };
  },

  /* s có id => sửa; không có id => thêm mới (chỉ có branchId + date) */
  openForm(s) {
    const editing = !!s.id;
    const branch = State.meta.branches.find(b => b.id === s.branchId);
    const userOpts = ['<option value="">— Để trống (ca chưa có người) —</option>']
      .concat(State.meta.users.map(u => `<option value="${u.id}" ${s.userId === u.id ? 'selected' : ''}>${U.esc(u.displayName)} (${U.esc(u.branchName)})</option>`)).join('');
    const m = U.openModal(`
      <h3>${editing ? 'Sửa ca trực' : 'Thêm ca trực'} · ${U.esc(branch ? branch.name : '')}</h3>
      <form id="shiftForm" novalidate>
        <label for="fDate">Ngày</label>
        <input id="fDate" type="date" value="${U.esc(s.date)}" required>
        <div class="row2">
          <div><label for="fStart">Bắt đầu</label><input id="fStart" type="time" value="${U.esc(s.start || '08:00')}" required></div>
          <div><label for="fEnd">Kết thúc</label><input id="fEnd" type="time" value="${U.esc(s.end || '12:00')}" required></div>
        </div>
        <label for="fUser">Người phụ trách</label>
        <select id="fUser">${userOpts}</select>
        <label for="fNote">Ghi chú</label>
        <input id="fNote" maxlength="200" value="${U.esc(s.note || '')}">
        <div id="formErr"></div>
        <div class="modal-actions">
          <button class="btn" type="button" data-close>Hủy</button>
          <button class="btn primary" type="submit">${editing ? 'Lưu thay đổi' : 'Tạo ca'}</button>
        </div>
      </form>`);
    m.querySelector('[data-close]').onclick = () => U.closeModal();
    m.querySelector('#shiftForm').onsubmit = async ev => {
      ev.preventDefault();
      const body = {
        branchId: s.branchId,
        date: m.querySelector('#fDate').value,
        start: m.querySelector('#fStart').value,
        end: m.querySelector('#fEnd').value,
        userId: m.querySelector('#fUser').value ? Number(m.querySelector('#fUser').value) : null,
        note: m.querySelector('#fNote').value
      };
      try {
        if (editing) await API.put('/api/shifts/' + s.id, body);
        else await API.post('/api/shifts', body);
        U.closeModal();
        U.toast(editing ? 'Đã lưu ca trực' : 'Đã tạo ca trực');
        App.render();
      } catch (e) {
        m.querySelector('#formErr').innerHTML = U.errorBox(e);
      }
    };
  }
};
