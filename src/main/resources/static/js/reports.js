/* Màn hình Báo cáo: xuất CSV, nhập CSV, khảo sát K7, báo cáo tháng K8, phiếu đối soát hằng tuần. */
const Reports = {
  period: 'TUAN_4',
  surveyList: [],
  monthlyList: [],
  reconList: [],

  async render(root) {
    root.innerHTML = '<p class="muted">Đang tải báo cáo…</p>';
    try {
      const [surveyList, monthlyList, reconList] = await Promise.all([
        API.get('/api/survey?period=' + this.period),
        API.get('/api/monthly-reports'),
        API.get('/api/reconciliation?week=' + State.week)
      ]);
      this.surveyList = surveyList; this.monthlyList = monthlyList; this.reconList = reconList;
      root.innerHTML = this.html();
      this.bind(root);
    } catch (e) {
      root.innerHTML = U.errorBox(e);
    }
  },

  html() {
    return `
    <div class="stack">
      ${this.exportCard()}
      ${this.importCard()}
      ${this.surveyCard()}
      ${this.monthlyCard()}
      ${this.reconCard()}
    </div>`;
  },

  // -------------------------------------------------------------- Xuất CSV

  exportCard() {
    return `
    <div class="card">
      <h2>Xuất báo cáo (CSV UTF-8, mở được bằng Excel)</h2>
      <p class="hint">Số liệu lấy trực tiếp từ dữ liệu hệ thống tại thời điểm xuất, không viết cứng.</p>
      <div class="toolbar">
        <button class="btn primary" data-act="export-week">Xuất CSV tuần ${U.dm(State.week)}–${U.dm(U.addDays(State.week, 6))}</button>
        <button class="btn" data-act="export-month">Xuất CSV báo cáo tháng (K8)</button>
      </div>
    </div>`;
  },

  // -------------------------------------------------------------- Nhập CSV

  importCard() {
    return `
    <div class="card">
      <h2>Nhập CSV ca trực / nhiệm vụ</h2>
      <p class="hint">Mỗi dòng được kiểm tra và báo lỗi riêng; dòng lỗi không làm hỏng các dòng còn lại.<br>
        Ca trực: <code>branchCode,date,start,end,userCode,note</code> (userCode để trống nếu ca trống).<br>
        Nhiệm vụ: <code>title,templateOrd,branchCode,assigneeCode,estimatedMinutes,dueDate,priority</code>
        (templateOrd/branchCode để trống nếu không dùng mẫu / phạm vi toàn hệ thống).</p>
      <div class="row2">
        <div>
          <label for="shiftCsv">Tệp CSV ca trực</label>
          <input id="shiftCsv" type="file" accept=".csv,text/csv">
          <button class="btn" type="button" data-act="import-shifts" style="margin-top:8px">Nhập ca trực</button>
        </div>
        <div>
          <label for="taskCsv">Tệp CSV nhiệm vụ</label>
          <input id="taskCsv" type="file" accept=".csv,text/csv">
          <button class="btn" type="button" data-act="import-tasks" style="margin-top:8px">Nhập nhiệm vụ</button>
        </div>
      </div>
      <div id="importResult"></div>
    </div>`;
  },

  importResultHtml(summary) {
    const rows = summary.rows.map(r => `<tr class="${r.ok ? '' : 'row-warn'}">
        <td>${r.row}</td><td>${r.ok ? 'OK' : 'Lỗi'}</td><td>${U.esc(r.message)}</td></tr>`).join('');
    return `<div class="card" style="margin-top:12px">
      <p><b>${summary.okRows}/${summary.totalRows}</b> dòng nhập thành công (${summary.errorRows} lỗi).</p>
      <div class="table-wrap"><table><thead><tr><th>Dòng</th><th>Kết quả</th><th>Chi tiết</th></tr></thead>
      <tbody>${rows}</tbody></table></div>
    </div>`;
  },

  // -------------------------------------------------------------- K7

  surveyCard() {
    const rows = this.surveyList.map(s => `<tr><td>${U.esc(s.userCode)}</td><td>${s.q1}</td><td>${s.q2}</td>
        <td>${s.q3}</td><td><b>${s.avg}</b></td><td class="muted small">${U.esc(s.note || '')}</td></tr>`).join('')
      || '<tr><td colspan="6" class="muted">Chưa có phản hồi cho kỳ này</td></tr>';
    return `
    <div class="card">
      <h2>Khảo sát hài lòng nhân sự (K7)</h2>
      <p class="hint">3 câu, thang điểm 1 (rất không hài lòng) – 5 (rất hài lòng). Chỉ nộp được cho chính mình;
        nộp lại cùng kỳ sẽ ghi đè. Mục tiêu K7 ≥ 3,5 điểm, tính ở đúng tuần 4 và tuần 8 kể từ tuần triển khai.</p>
      <form id="surveyForm" novalidate>
        <label for="surveyPeriod">Kỳ khảo sát</label>
        <select id="surveyPeriod">
          <option value="TUAN_4" ${this.period === 'TUAN_4' ? 'selected' : ''}>Tuần 4</option>
          <option value="TUAN_8" ${this.period === 'TUAN_8' ? 'selected' : ''}>Tuần 8</option>
        </select>
        <div class="row2">
          <div><label for="q1">Câu 1: Khối lượng công việc hợp lý</label><input id="q1" type="number" min="1" max="5" value="4"></div>
          <div><label for="q2">Câu 2: Lịch ca rõ ràng, công bằng</label><input id="q2" type="number" min="1" max="5" value="4"></div>
        </div>
        <label for="q3">Câu 3: Hài lòng chung với StaffFlow</label>
        <input id="q3" type="number" min="1" max="5" value="4">
        <label for="surveyNote">Ghi chú (tùy chọn)</label>
        <input id="surveyNote" maxlength="300">
        <div id="surveyErr"></div>
        <div class="modal-actions" style="justify-content:flex-start">
          <button class="btn primary" type="submit">Gửi khảo sát (cho ${U.esc(State.me.code)})</button>
        </div>
      </form>
      <div class="table-wrap" style="margin-top:12px"><table>
        <thead><tr><th>Nhân sự</th><th>Câu 1</th><th>Câu 2</th><th>Câu 3</th><th>Trung bình</th><th>Ghi chú</th></tr></thead>
        <tbody>${rows}</tbody></table></div>
    </div>`;
  },

  // -------------------------------------------------------------- K8

  monthlyCard() {
    const canRecord = State.me.role === 'ADMIN';
    const rows = this.monthlyList.map(m => `<tr class="${m.submitted && !m.onTime ? 'row-warn' : ''}">
        <td>${U.esc(m.yearMonth)}</td><td>${U.dmy(m.dueDate)}</td>
        <td>${m.submittedDate ? U.dmy(m.submittedDate) : 'Chưa nộp'}</td>
        <td>${m.submitted ? (m.onTime ? '<span class="pill ok">Đúng hạn</span>' : '<span class="pill bad">Trễ hạn</span>') : ''}</td>
        <td class="muted small">${U.esc(m.note || '')}</td></tr>`).join('')
      || '<tr><td colspan="5" class="muted">Chưa ghi nhận báo cáo tháng nào</td></tr>';
    const form = canRecord ? `
      <form id="monthlyForm" novalidate>
        <div class="row2">
          <div><label for="ymInput">Tháng (yyyy-MM)</label><input id="ymInput" placeholder="2026-10" required></div>
          <div><label for="dueInput">Hạn nộp</label><input id="dueInput" type="date" required></div>
        </div>
        <div class="row2">
          <div><label for="subInput">Ngày nộp (để trống nếu chưa nộp)</label><input id="subInput" type="date"></div>
          <div><label for="monthlyNote">Ghi chú</label><input id="monthlyNote" maxlength="300"></div>
        </div>
        <div id="monthlyErr"></div>
        <div class="modal-actions" style="justify-content:flex-start">
          <button class="btn primary" type="submit">Ghi nhận báo cáo tháng</button>
        </div>
      </form>` : '<p class="muted">Chỉ Giám đốc (NV-01) được ghi nhận báo cáo tháng.</p>';
    return `
    <div class="card">
      <h2>Báo cáo tháng nộp đúng hạn (K8)</h2>
      <p class="hint">Mục tiêu K8 = 100% báo cáo tháng nộp đúng hạn (toàn hệ thống).</p>
      ${form}
      <div class="table-wrap" style="margin-top:12px"><table>
        <thead><tr><th>Tháng</th><th>Hạn nộp</th><th>Ngày nộp</th><th>Trạng thái</th><th>Ghi chú</th></tr></thead>
        <tbody>${rows}</tbody></table></div>
    </div>`;
  },

  // -------------------------------------------------------------- Đối soát

  reconCard() {
    const canCreate = State.me.role === 'ADMIN' || State.me.role === 'BRANCH_MANAGER';
    const branchOpts = ['<option value="">Toàn hệ thống</option>']
      .concat(State.meta.branches.map(b => `<option value="${b.id}">${U.esc(b.name)}</option>`)).join('');
    const checks = this.reconList.map(c => this.reconCheckHtml(c)).join('')
      || '<p class="muted">Chưa có phiếu đối soát cho tuần này</p>';
    return `
    <div class="card">
      <h2>Đối soát hằng tuần</h2>
      <p class="hint">Chọn ngẫu nhiên 10% số ca trong tuần (làm tròn lên, tối thiểu 1 ca) để đối chiếu thực tế tại
        quầy, kèm danh sách nhiệm vụ đã đóng "Xong" mà không có minh chứng trong tuần.</p>
      ${canCreate ? `
      <div class="toolbar">
        <select id="reconBranch">${branchOpts}</select>
        <button class="btn primary" data-act="recon-create">Tạo phiếu đối soát tuần ${U.dm(State.week)}</button>
      </div>` : ''}
      <div id="reconErr"></div>
      <div class="stack" style="margin-top:10px">${checks}</div>
    </div>`;
  },

  reconCheckHtml(c) {
    const items = c.items.map(it => `
      <div class="recon-item" data-check="${c.id}" data-shift="${it.shiftId}">
        <div class="tc-meta">${U.esc(it.shiftSummary)}</div>
        <div class="toolbar">
          <select class="recon-matched">
            <option value="">Chưa đối chiếu</option>
            <option value="true" ${it.matched === true ? 'selected' : ''}>Khớp</option>
            <option value="false" ${it.matched === false ? 'selected' : ''}>Lệch</option>
          </select>
          <input class="recon-note" placeholder="Ghi chú" value="${U.esc(it.note || '')}">
          <button class="btn small" data-act="recon-save">Lưu</button>
        </div>
      </div>`).join('') || '<p class="muted small">Không có ca nào trong tuần để lấy mẫu</p>';
    const flagged = c.flaggedTasks.map(t => `<li>${U.esc(t.title)} — ${U.esc(t.assigneeCode)}, hạn ${U.dmy(t.dueDate)}</li>`).join('')
      || '<li class="muted">Không có nhiệm vụ nào bị gắn cờ</li>';
    return `<div class="card">
      <p><b>Phiếu #${c.id}</b> · ${U.esc(c.branchName)} · tạo bởi ${U.esc(c.createdByCode)} lúc ${U.esc(c.createdAt)}</p>
      <h3>Mẫu ca được chọn (${c.items.length})</h3>
      ${items}
      <h3 style="margin-top:12px">Nhiệm vụ đóng không có minh chứng trong tuần (${c.flaggedTasks.length})</h3>
      <ul class="notes">${flagged}</ul>
    </div>`;
  },

  // -------------------------------------------------------------- Sự kiện

  bind(root) {
    root.onclick = async ev => {
      const el = ev.target.closest('[data-act]');
      if (!el) return;
      if (el.dataset.act === 'export-week') {
        window.open('/api/reports/week.csv?week=' + State.week, '_blank');
      } else if (el.dataset.act === 'export-month') {
        window.open('/api/reports/month.csv', '_blank');
      } else if (el.dataset.act === 'import-shifts') {
        await this.doImport(root, 'shiftCsv', '/api/import/shifts');
      } else if (el.dataset.act === 'import-tasks') {
        await this.doImport(root, 'taskCsv', '/api/import/tasks');
      } else if (el.dataset.act === 'recon-create') {
        await this.createRecon(root);
      } else if (el.dataset.act === 'recon-save') {
        await this.saveReconItem(el);
      }
    };
    const surveyForm = root.querySelector('#surveyForm');
    if (surveyForm) {
      surveyForm.querySelector('#surveyPeriod').onchange = ev => { this.period = ev.target.value; App.render(); };
      surveyForm.onsubmit = async ev => {
        ev.preventDefault();
        try {
          await API.post('/api/survey', {
            period: surveyForm.querySelector('#surveyPeriod').value,
            q1: Number(surveyForm.querySelector('#q1').value),
            q2: Number(surveyForm.querySelector('#q2').value),
            q3: Number(surveyForm.querySelector('#q3').value),
            note: surveyForm.querySelector('#surveyNote').value
          });
          U.toast('Đã gửi khảo sát');
          App.render();
        } catch (e) {
          surveyForm.querySelector('#surveyErr').innerHTML = U.errorBox(e);
        }
      };
    }
    const monthlyForm = root.querySelector('#monthlyForm');
    if (monthlyForm) {
      monthlyForm.onsubmit = async ev => {
        ev.preventDefault();
        try {
          await API.post('/api/monthly-reports', {
            yearMonth: monthlyForm.querySelector('#ymInput').value,
            dueDate: monthlyForm.querySelector('#dueInput').value,
            submittedDate: monthlyForm.querySelector('#subInput').value || null,
            note: monthlyForm.querySelector('#monthlyNote').value
          });
          U.toast('Đã ghi nhận báo cáo tháng');
          App.render();
        } catch (e) {
          monthlyForm.querySelector('#monthlyErr').innerHTML = U.errorBox(e);
        }
      };
    }
  },

  async doImport(root, inputId, url) {
    const input = root.querySelector('#' + inputId);
    const box = root.querySelector('#importResult');
    if (!input.files || !input.files[0]) {
      box.innerHTML = U.errorBox({ message: 'Chưa chọn tệp CSV', details: [] });
      return;
    }
    try {
      const summary = await API.upload(url, input.files[0]);
      box.innerHTML = this.importResultHtml(summary);
      if (summary.okRows > 0) {
        U.toast(summary.okRows + ' dòng đã nhập thành công');
      }
    } catch (e) {
      box.innerHTML = U.errorBox(e);
    }
  },

  async createRecon(root) {
    const branchVal = root.querySelector('#reconBranch').value;
    try {
      await API.post('/api/reconciliation', { week: State.week, branchId: branchVal ? Number(branchVal) : null });
      U.toast('Đã tạo phiếu đối soát');
      App.render();
    } catch (e) {
      root.querySelector('#reconErr').innerHTML = U.errorBox(e);
    }
  },

  async saveReconItem(btn) {
    const box = btn.closest('.recon-item');
    const checkId = box.dataset.check;
    const shiftId = box.dataset.shift;
    const sel = box.querySelector('.recon-matched').value;
    const note = box.querySelector('.recon-note').value;
    try {
      await API.put(`/api/reconciliation/${checkId}/items/${shiftId}`, { matched: sel === '' ? null : sel === 'true', note });
      U.toast('Đã lưu kết quả đối soát');
      App.render();
    } catch (e) {
      U.toast(e.message, true);
    }
  }
};
