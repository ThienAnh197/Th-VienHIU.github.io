/* Gọi backend Java bằng Fetch API. Không có dữ liệu giả trong JavaScript. */
class ApiError extends Error {
  constructor(status, message, details) {
    super(message);
    this.status = status;
    this.details = details || [];
  }
}

const API = {
  csrf: null,

  async request(method, url, body) {
    const headers = { 'Accept': 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (method !== 'GET' && this.csrf) headers['X-CSRF-Token'] = this.csrf;
    let res;
    try {
      res = await fetch(url, {
        method, headers, credentials: 'same-origin',
        body: body === undefined ? undefined : JSON.stringify(body)
      });
    } catch (e) {
      throw new ApiError(0, 'Không kết nối được máy chủ. Kiểm tra backend Java đã chạy chưa.');
    }
    let data = null;
    const text = await res.text();
    if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
    if (!res.ok) {
      const err = new ApiError(res.status, (data && data.error) || ('Lỗi ' + res.status), data && data.details);
      if (res.status === 401 && typeof App !== 'undefined') App.onUnauthorized();
      throw err;
    }
    return data;
  },

  get(url) { return this.request('GET', url); },
  post(url, body) { return this.request('POST', url, body === undefined ? {} : body); },
  put(url, body) { return this.request('PUT', url, body); },

  /* Tải tệp lên (multipart/form-data) — không đặt Content-Type thủ công để trình duyệt tự thêm boundary. */
  async upload(url, file) {
    const form = new FormData();
    form.append('file', file);
    const headers = { 'Accept': 'application/json' };
    if (this.csrf) headers['X-CSRF-Token'] = this.csrf;
    let res;
    try {
      res = await fetch(url, { method: 'POST', headers, credentials: 'same-origin', body: form });
    } catch (e) {
      throw new ApiError(0, 'Không kết nối được máy chủ. Kiểm tra backend Java đã chạy chưa.');
    }
    let data = null;
    const text = await res.text();
    if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
    if (!res.ok) {
      const err = new ApiError(res.status, (data && data.error) || ('Lỗi ' + res.status), data && data.details);
      if (res.status === 401 && typeof App !== 'undefined') App.onUnauthorized();
      throw err;
    }
    return data;
  }
};

/* Tiện ích dùng chung */
const U = {
  esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  },
  addDays(iso, n) {
    const [y, m, d] = iso.split('-').map(Number);
    const t = new Date(Date.UTC(y, m - 1, d + n));
    return t.toISOString().slice(0, 10);
  },
  mondayOf(iso) {
    const [y, m, d] = iso.split('-').map(Number);
    const dow = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = CN
    return U.addDays(iso, dow === 0 ? -6 : 1 - dow);
  },
  dm(iso) { const p = iso.split('-'); return p[2] + '/' + p[1]; },
  dmy(iso) { const p = iso.split('-'); return p[2] + '/' + p[1] + '/' + p[0]; },
  hours(min) { return (min / 60).toLocaleString('vi-VN', { maximumFractionDigits: 1 }); },
  pct(v) { return v == null ? 'N/A' : v.toLocaleString('vi-VN', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + '%'; },
  roleLabel(r) {
    return { ADMIN: 'Quản lý cấp cao', BRANCH_MANAGER: 'Quản lý chi nhánh', TECH: 'Quản trị kỹ thuật', STAFF: 'Chuyên viên' }[r] || r;
  },
  toast(msg, isErr) {
    const t = document.getElementById('toast');
    t.textContent = msg; t.className = 'toast' + (isErr ? ' err' : ''); t.hidden = false;
    clearTimeout(U._t); U._t = setTimeout(() => { t.hidden = true; }, 3500);
  },
  errorBox(e) {
    const list = (e.details && e.details.length) ? '<ul>' + e.details.map(d => '<li>' + U.esc(d) + '</li>').join('') + '</ul>' : '';
    return '<div class="form-error" role="alert">' + U.esc(e.message) + list + '</div>';
  },
  /* Hộp thoại đơn giản. Trả về phần tử .modal; gọi U.closeModal() để đóng. */
  openModal(html) {
    const root = document.getElementById('modalRoot');
    root.innerHTML = '<div class="modal-backdrop" data-close="1"><div class="modal" role="dialog" aria-modal="true">' + html + '</div></div>';
    const back = root.firstElementChild;
    back.addEventListener('mousedown', ev => { if (ev.target === back) U.closeModal(); });
    document.addEventListener('keydown', U._esc);
    const first = root.querySelector('input,select,button');
    if (first) first.focus();
    return root.querySelector('.modal');
  },
  closeModal() {
    document.getElementById('modalRoot').innerHTML = '';
    document.removeEventListener('keydown', U._esc);
  },
  _esc(ev) { if (ev.key === 'Escape') U.closeModal(); }
};
