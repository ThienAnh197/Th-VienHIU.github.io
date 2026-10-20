/* Màn hình KPI & tải việc: K1-K6, tải việc theo người/nhóm, gợi ý mốc tuần 4/8. */
const KPI_META = {
  K1: 'K1 – Tỉ lệ bao phủ ca trực', K2: 'K2 – Ca trống trong 24 giờ tới',
  K3: 'K3 – Tỉ lệ nhiệm vụ đúng hạn', K4: 'K4 – Thời gian xử lý yêu cầu đổi ca',
  K5: 'K5 – Mất cân bằng tải việc giữa các nhóm', K6: 'K6 – Tỉ lệ cập nhật trạng thái đúng hạn'
};
const KPI_STATUS_PILL = { DAT: 'ok', CHUA_DAT: 'bad', NA: '' };
const KPI_STATUS_LABEL = { DAT: 'Đạt', CHUA_DAT: 'Chưa đạt', NA: 'Chưa đủ dữ liệu' };

const Dashboard = {
  data: null,

  async render(root) {
    root.innerHTML = '<p class="muted">Đang tải KPI & tải việc…</p>';
    try {
      this.data = await API.get('/api/dashboard?week=' + State.week);
      root.innerHTML = this.html();
      this.bind(root);
    } catch (e) {
      root.innerHTML = U.errorBox(e);
    }
  },

  html() {
    const d = this.data;
    const kpiCards = d.kpis.map(k => this.kpiCardHtml(k)).join('');
    const milestone = d.milestoneWeek ? `
      <div class="card notice">
        <b>Mốc tuần ${d.milestoneWeek}</b> — hệ thống gợi ý xem lại các chỉ số sau khi triển khai:
        <ul>${d.suggestions.map(s => `<li>${U.esc(s)}</li>`).join('')}</ul>
      </div>` : '';
    return `
    <div class="stack">
      <div class="card toolbar">
        <button class="btn" data-act="prev">‹ Tuần trước</button>
        <div class="week-label">Tuần ${U.dm(State.week)} – ${U.dmy(U.addDays(State.week, 6))}</div>
        <button class="btn" data-act="next">Tuần sau ›</button>
      </div>
      ${milestone}
      <div class="kpi-grid">${kpiCards}</div>
      <div class="card">
        <h3>Tải việc theo người (tuần)</h3>
        <p class="hint">tải việc (phút) = tổng phút ca trực trong tuần + tổng phút nhiệm vụ có hạn trong tuần;
          hệ số tải (%) = tải việc / 2400 phút (40 giờ) × 100%. Cảnh báo "Vượt 100% tải việc" chỉ hiện khi thực sự vượt.</p>
        <table class="data-table">
          <thead><tr><th>Nhân sự</th><th>Nhóm</th><th>Ca trực</th><th>Nhiệm vụ</th><th>Tổng</th><th>Hệ số tải</th></tr></thead>
          <tbody>${d.workloadByUser.map(w => this.userRowHtml(w)).join('')}</tbody>
        </table>
      </div>
      <div class="card">
        <h3>Tải việc theo nhóm chuyên môn</h3>
        <p class="hint">So sánh giờ làm thấp nhất/cao nhất trong từng nhóm chuyên môn (ca trực + nhiệm vụ) để phát hiện mất cân bằng.</p>
        <table class="data-table">
          <thead><tr><th>Nhóm</th><th>Số người</th><th>Thấp nhất</th><th>Cao nhất</th><th>Tỉ lệ cao/thấp</th><th>Ghi chú</th></tr></thead>
          <tbody>${d.workloadByGroup.map(g => this.groupRowHtml(g)).join('')}</tbody>
        </table>
      </div>
    </div>`;
  },

  kpiCardHtml(k) {
    const pill = KPI_STATUS_PILL[k.status] || '';
    return `<button class="kpi-card" data-act="formula" data-code="${k.code}">
      <div class="kpi-head">${U.esc(KPI_META[k.code] || k.code)} <span class="pill ${pill}">${KPI_STATUS_LABEL[k.status]}</span></div>
      <div class="kpi-value">${U.esc(k.valueLabel)}</div>
      <div class="muted small">${U.esc(k.detail)}</div>
    </button>`;
  },

  userRowHtml(w) {
    return `<tr class="${w.overLimit ? 'row-warn' : ''}">
      <td>${U.esc(w.userCode)} — ${U.esc(w.userName)}</td>
      <td>${U.esc(w.titleGroupLabel)}</td>
      <td>${U.hours(w.shiftMinutes)} giờ</td>
      <td>${U.hours(w.taskMinutes)} giờ</td>
      <td>${U.hours(w.totalMinutes)} giờ</td>
      <td>${U.pct(w.pct)}${w.overLimit ? ' <span class="pill bad">Vượt 100% tải việc</span>' : ''}</td>
    </tr>`;
  },

  groupRowHtml(g) {
    const h = v => v == null ? 'N/A' : v.toLocaleString('vi-VN', { maximumFractionDigits: 1 }) + ' giờ';
    return `<tr>
      <td>${U.esc(g.titleGroupLabel)}</td>
      <td>${g.memberCount}</td>
      <td>${h(g.minHours)}</td>
      <td>${h(g.maxHours)}</td>
      <td>${g.ratio == null ? 'N/A' : g.ratio.toLocaleString('vi-VN', { maximumFractionDigits: 2 }) + '×'}</td>
      <td class="muted small">${U.esc(g.note || '')}</td>
    </tr>`;
  },

  bind(root) {
    root.onclick = ev => {
      const el = ev.target.closest('[data-act]');
      if (!el) return;
      if (el.dataset.act === 'prev') { State.week = U.addDays(State.week, -7); App.render(); }
      else if (el.dataset.act === 'next') { State.week = U.addDays(State.week, 7); App.render(); }
      else if (el.dataset.act === 'formula') this.openFormula(el.dataset.code);
    };
  },

  openFormula(code) {
    const k = this.data.kpis.find(x => x.code === code);
    if (!k) return;
    const m = U.openModal(`
      <h3>${U.esc(KPI_META[code] || code)}</h3>
      <p><b>Giá trị:</b> ${U.esc(k.valueLabel)} — <span class="pill ${KPI_STATUS_PILL[k.status]}">${KPI_STATUS_LABEL[k.status]}</span></p>
      <p><b>Cách tính:</b> ${U.esc(k.formula)}</p>
      <p><b>Diễn giải:</b> ${U.esc(k.detail)}</p>
      <p><b>Mục tiêu:</b> ${k.target == null ? 'Không áp dụng' : U.esc(k.target)}</p>
      <div class="modal-actions"><button class="btn" type="button" data-close>Đóng</button></div>
    `);
    m.querySelector('[data-close]').onclick = () => U.closeModal();
  }
};
