/* Màn hình Danh mục: cơ sở và nhân sự (chỉ mã vị trí, không có họ tên thật). */
const Catalog = {
  render(root) {
    const m = State.meta;
    const branchRows = m.branches.map(b => `
      <tr><td><b>${U.esc(b.name)}</b></td><td>${U.esc(b.address)}</td>
      <td>${b.note ? '<span class="pill warn">' + U.esc(b.note) + '</span>' : ''}</td></tr>`).join('');
    const userRows = m.users.map(u => `
      <tr><td><b>${U.esc(u.code)}</b></td><td>${U.esc(u.title)}</td><td>${U.esc(u.branchName)}</td>
      <td>${U.esc(U.roleLabel(u.role))}</td></tr>`).join('');
    root.innerHTML = `
      <div class="stack">
        <div class="card">
          <h2>Giờ mở cửa (giả định GĐ-07: cả ba cơ sở dùng chung một lịch)</h2>
          <p>Thứ Hai–Thứ Sáu 08:00–20:00 (12 giờ/ngày) · Thứ Bảy 08:00–17:00 (9 giờ) · Chủ Nhật và ngày lễ đóng cửa.<br>
          Mỗi cơ sở <b>69 giờ/tuần</b>; tổng nhu cầu <b>207 giờ-cơ sở/tuần</b>.</p>
        </div>
        <div class="card">
          <h2>Cơ sở</h2>
          <div class="table-wrap"><table>
            <thead><tr><th>Tên</th><th>Địa chỉ</th><th>Ghi chú</th></tr></thead>
            <tbody>${branchRows}</tbody></table></div>
        </div>
        <div class="card">
          <h2>Nhân sự (8 vị trí)</h2>
          <div class="table-wrap"><table>
            <thead><tr><th>Mã</th><th>Nhóm chức danh</th><th>Cơ sở</th><th>Vai trò hệ thống</th></tr></thead>
            <tbody>${userRows}</tbody></table></div>
          <p class="hint">Giới hạn tải việc: 40 giờ/tuần/người (giả định GĐ-03). Dữ liệu demo chỉ dùng mã vị trí NV-01…NV-08.</p>
        </div>
      </div>`;
  }
};
