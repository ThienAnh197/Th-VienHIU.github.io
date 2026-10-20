/* Màn hình Nhiệm vụ: Kanban 4 trạng thái, lọc theo tuần/cơ sở/người phụ trách, cờ thiếu minh chứng. */
const KANBAN_COLUMNS = ['MOI', 'DANG_LAM', 'CHO_DUYET', 'XONG'];
const PRIORITY_PILL = { CAO: 'bad', TRUNG_BINH: 'warn', THAP: '' };

const Tasks = {
  list: [], templates: [],

  async render(root) {
    root.innerHTML = '<p class="muted">Đang tải nhiệm vụ…</p>';
    try {
      const q = new URLSearchParams({ week: State.week });
      if (State.branchId) q.set('branchId', State.branchId);
      const [list, templates] = await Promise.all([
        API.get('/api/tasks?' + q.toString()), API.get('/api/task-templates')
      ]);
      this.list = list; this.templates = templates;
      root.innerHTML = this.html();
      this.bind(root);
    } catch (e) {
      root.innerHTML = U.errorBox(e);
    }
  },

  html() {
    const branchOpts = ['<option value="">Cả 3 cơ sở</option>']
      .concat(State.meta.branches.map(b => `<option value="${b.id}" ${String(State.branchId) === String(b.id) ? 'selected' : ''}>${U.esc(b.name)}</option>`)).join('');
    const canCreate = State.me.role === 'ADMIN' || State.me.role === 'BRANCH_MANAGER';
    const cols = KANBAN_COLUMNS.map(st => this.columnHtml(st)).join('');
    return `
    <div class="stack">
      <div class="card toolbar">
        <button class="btn" data-act="prev">‹ Tuần trước</button>
        <div class="week-label">Tuần ${U.dm(State.week)} – ${U.dmy(U.addDays(State.week, 6))}</div>
        <button class="btn" data-act="next">Tuần sau ›</button>
        <span class="grow"></span>
        <select id="branchPick">${branchOpts}</select>
        ${canCreate ? '<button class="btn primary" data-act="new">+ Nhiệm vụ mới</button>' : ''}
      </div>
      <p class="hint">Nhiệm vụ được lọc theo HẠN nằm trong tuần đang xem. Chuyển sang "Xong" mà chưa ghi minh chứng
        vẫn được phép nhưng sẽ gắn cờ ⚠ để phục vụ đối soát hằng tuần.</p>
      <div class="kanban">${cols}</div>
    </div>`;
  },

  columnHtml(status) {
    const items = this.list.filter(t => t.status === status);
    const cards = items.map(t => this.cardHtml(t)).join('') || '<p class="muted small" style="padding:6px">Không có nhiệm vụ</p>';
    const label = { MOI: 'Mới', DANG_LAM: 'Đang làm', CHO_DUYET: 'Chờ duyệt', XONG: 'Xong' }[status];
    return `<div class="kanban-col">
      <div class="kanban-head">${label} <span class="muted small">(${items.length})</span></div>
      <div class="kanban-body">${cards}</div>
    </div>`;
  },

  cardHtml(t) {
    const pr = PRIORITY_PILL[t.priority] || '';
    return `<button class="task-card" data-act="open" data-id="${t.id}">
      <div class="tc-title">${U.esc(t.title)}</div>
      <div class="tc-meta">${U.esc(t.assigneeCode)} · ${U.esc(t.branchName)}</div>
      <div class="tc-meta ${t.overdue ? 'tc-overdue' : ''}">Hạn ${U.dmy(t.dueDate)}${t.overdue ? ' (quá hạn)' : ''}</div>
      <div class="tc-tags">
        ${pr ? `<span class="pill ${pr}">${U.esc(t.priorityLabel)}</span>` : `<span class="pill">${U.esc(t.priorityLabel)}</span>`}
        <span class="muted small">${U.hours(t.estimatedMinutes)} giờ</span>
        ${t.closedWithoutEvidence ? '<span class="pill warn">⚠ Chưa có minh chứng</span>' : ''}
      </div>
    </button>`;
  },

  bind(root) {
    root.onclick = ev => {
      const el = ev.target.closest('[data-act]');
      if (!el) return;
      if (el.dataset.act === 'prev') { State.week = U.addDays(State.week, -7); App.render(); }
      else if (el.dataset.act === 'next') { State.week = U.addDays(State.week, 7); App.render(); }
      else if (el.dataset.act === 'new') this.openForm(null);
      else if (el.dataset.act === 'open') this.openDetail(Number(el.dataset.id));
    };
    root.querySelector('#branchPick').onchange = ev => {
      State.branchId = ev.target.value ? Number(ev.target.value) : null; App.render();
    };
  },

  openDetail(id) {
    const t = this.list.find(x => x.id === id);
    if (!t) return;
    const statusOpts = KANBAN_COLUMNS.filter(s => s !== t.status)
      .map(s => `<option value="${s}">${({ MOI: 'Mới', DANG_LAM: 'Đang làm', CHO_DUYET: 'Chờ duyệt', XONG: 'Xong' })[s]}</option>`).join('');
    const m = U.openModal(`
      <h3>${U.esc(t.title)}</h3>
      <p>${U.esc(t.branchName)} · Phụ trách: <b>${U.esc(t.assigneeCode)}</b> (${U.esc(t.assigneeName)})</p>
      <p>Hạn: <b class="${t.overdue ? 'tc-overdue' : ''}">${U.dmy(t.dueDate)}</b> · Ưu tiên: ${U.esc(t.priorityLabel)}
        · Thời lượng dự kiến: ${U.hours(t.estimatedMinutes)} giờ</p>
      <p>Trạng thái hiện tại: <b>${U.esc(t.statusLabel)}</b>${t.closedWithoutEvidence ? ' <span class="pill warn">⚠ Đóng không có minh chứng</span>' : ''}</p>
      ${t.evidenceNote ? `<p class="muted">Minh chứng: ${U.esc(t.evidenceNote)}</p>` : ''}
      <div id="histBox" class="muted small">Đang tải nhật ký…</div>
      ${t.canUpdateStatus ? `
      <form id="statusForm" novalidate>
        <label for="newStatus">Đổi trạng thái</label>
        <select id="newStatus">${statusOpts}</select>
        <label for="evidence">Ghi chú / minh chứng (bắt buộc nếu chuyển sang Xong để tránh gắn cờ)</label>
        <input id="evidence" maxlength="300">
        <div id="statusErr"></div>
        <div class="modal-actions">
          ${t.canManage ? '<button class="btn" type="button" data-act="edit">Sửa nhiệm vụ</button>' : ''}
          <button class="btn" type="button" data-close>Đóng</button>
          <button class="btn primary" type="submit">Cập nhật trạng thái</button>
        </div>
      </form>` : `<div class="modal-actions">
          ${t.canManage ? '<button class="btn" type="button" data-act="edit">Sửa nhiệm vụ</button>' : ''}
          <button class="btn" type="button" data-close>Đóng</button></div>`}
    `);
    m.querySelector('[data-close]').onclick = () => U.closeModal();
    const editBtn = m.querySelector('[data-act="edit"]');
    if (editBtn) editBtn.onclick = () => this.openForm(t);
    const form = m.querySelector('#statusForm');
    if (form) form.onsubmit = async ev => {
      ev.preventDefault();
      try {
        await API.post(`/api/tasks/${t.id}/status`,
          { status: m.querySelector('#newStatus').value, evidenceNote: m.querySelector('#evidence').value });
        U.closeModal();
        U.toast('Đã cập nhật trạng thái nhiệm vụ');
        App.render();
      } catch (e) {
        m.querySelector('#statusErr').innerHTML = U.errorBox(e);
      }
    };
    API.get('/api/tasks/' + t.id + '/history').then(hist => {
      const box = m.querySelector('#histBox');
      if (!box) return;
      box.innerHTML = hist.length ? ('Nhật ký: ' + hist.map(h => `${h.fromLabel} → ${h.toLabel} (${h.byCode}, ${h.at})`).join('; ')) : '';
    }).catch(() => {});
  },

  /* t == null => tạo mới; có id => sửa */
  openForm(t) {
    const editing = !!(t && t.id);
    const branchOpts = ['<option value="">Toàn hệ thống</option>']
      .concat(State.meta.branches.map(b => `<option value="${b.id}" ${t && t.branchId === b.id ? 'selected' : ''}>${U.esc(b.name)}</option>`)).join('');
    const userOpts = State.meta.users.map(u => `<option value="${u.id}" ${t && t.assigneeId === u.id ? 'selected' : ''}>${U.esc(u.displayName)}</option>`).join('');
    const tplOpts = ['<option value="">— Không dùng mẫu —</option>']
      .concat(this.templates.map(tpl => `<option value="${tpl.id}" data-minutes="${tpl.estimatedMinutes}" ${t && t.templateId === tpl.id ? 'selected' : ''}>${U.esc(tpl.name)} (${U.hours(tpl.estimatedMinutes)} giờ)</option>`)).join('');
    const m = U.openModal(`
      <h3>${editing ? 'Sửa nhiệm vụ' : 'Tạo nhiệm vụ mới'}</h3>
      <form id="taskForm" novalidate>
        <label for="tplPick">Mẫu nhiệm vụ (điền sẵn thời lượng, tùy chọn)</label>
        <select id="tplPick">${tplOpts}</select>
        <label for="tTitle">Tên nhiệm vụ</label>
        <input id="tTitle" required maxlength="200" value="${t ? U.esc(t.title) : ''}">
        <div class="row2">
          <div><label for="tBranch">Cơ sở / phạm vi</label><select id="tBranch">${branchOpts}</select></div>
          <div><label for="tAssignee">Người phụ trách</label><select id="tAssignee">${userOpts}</select></div>
        </div>
        <div class="row2">
          <div><label for="tDue">Hạn</label><input id="tDue" type="date" required value="${t ? t.dueDate : ''}"></div>
          <div><label for="tMinutes">Thời lượng dự kiến (phút)</label><input id="tMinutes" type="number" min="1" required value="${t ? t.estimatedMinutes : 60}"></div>
        </div>
        <label for="tPriority">Độ ưu tiên</label>
        <select id="tPriority">
          <option value="CAO" ${t && t.priority === 'CAO' ? 'selected' : ''}>Cao</option>
          <option value="TRUNG_BINH" ${!t || t.priority === 'TRUNG_BINH' ? 'selected' : ''}>Trung bình</option>
          <option value="THAP" ${t && t.priority === 'THAP' ? 'selected' : ''}>Thấp</option>
        </select>
        <div id="taskErr"></div>
        <div class="modal-actions">
          <button class="btn" type="button" data-close>Hủy</button>
          <button class="btn primary" type="submit">${editing ? 'Lưu thay đổi' : 'Tạo nhiệm vụ'}</button>
        </div>
      </form>`);
    m.querySelector('[data-close]').onclick = () => U.closeModal();
    m.querySelector('#tplPick').onchange = ev => {
      const opt = ev.target.selectedOptions[0];
      if (opt && opt.dataset.minutes) m.querySelector('#tMinutes').value = opt.dataset.minutes;
    };
    m.querySelector('#taskForm').onsubmit = async ev => {
      ev.preventDefault();
      const body = {
        title: m.querySelector('#tTitle').value,
        templateId: m.querySelector('#tplPick').value ? Number(m.querySelector('#tplPick').value) : null,
        branchId: m.querySelector('#tBranch').value ? Number(m.querySelector('#tBranch').value) : null,
        assigneeId: Number(m.querySelector('#tAssignee').value),
        estimatedMinutes: Number(m.querySelector('#tMinutes').value),
        dueDate: m.querySelector('#tDue').value,
        priority: m.querySelector('#tPriority').value
      };
      try {
        if (editing) await API.put('/api/tasks/' + t.id, body);
        else await API.post('/api/tasks', body);
        U.closeModal();
        U.toast(editing ? 'Đã lưu nhiệm vụ' : 'Đã tạo nhiệm vụ');
        App.render();
      } catch (e) {
        m.querySelector('#taskErr').innerHTML = U.errorBox(e);
      }
    };
  }
};
