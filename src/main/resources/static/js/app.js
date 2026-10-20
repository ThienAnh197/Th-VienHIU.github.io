/* Khung ứng dụng: đăng nhập, điều hướng, trạng thái chung. */
const State = { me: null, meta: null, week: null, branchId: null, view: 'schedule' };

/* Các màn hình. "soon" = sẽ có ở phase sau (hiển thị mờ để nhóm biết lộ trình). */
const VIEWS = [
  { id: 'schedule', label: 'Lịch trực', render: root => Schedule.render(root) },
  { id: 'swaps', label: 'Đổi ca & vắng', render: root => Swaps.render(root) },
  { id: 'catalog', label: 'Danh mục', render: root => Catalog.render(root) },
  { id: 'tasks', label: 'Nhiệm vụ', render: root => Tasks.render(root) },
  { id: 'dashboard', label: 'KPI & tải việc', render: root => Dashboard.render(root) },
  { id: 'reports', label: 'Báo cáo', render: root => Reports.render(root) }
];

const App = {
  async start() {
    document.getElementById('loginForm').addEventListener('submit', ev => this.login(ev));
    document.getElementById('logoutBtn').addEventListener('click', () => this.logout());
    try {
      const me = await API.get('/api/auth/me');
      await this.enter(me);
    } catch (e) {
      this.showLogin();
    }
  },

  showLogin() {
    document.getElementById('appView').hidden = true;
    document.getElementById('loginView').hidden = false;
    document.getElementById('loginCode').focus();
  },

  onUnauthorized() {
    if (State.me) { State.me = null; U.toast('Phiên đăng nhập đã hết hạn', true); this.showLogin(); }
  },

  async login(ev) {
    ev.preventDefault();
    const err = document.getElementById('loginError');
    err.hidden = true;
    try {
      const me = await API.post('/api/auth/login', {
        code: document.getElementById('loginCode').value,
        password: document.getElementById('loginPass').value
      });
      document.getElementById('loginPass').value = '';
      await this.enter(me);
    } catch (e) {
      err.textContent = e.message; err.hidden = false;
    }
  },

  async enter(me) {
    API.csrf = me.csrfToken;
    State.me = me.user;
    State.meta = await API.get('/api/meta');
    if (!State.week) State.week = State.meta.defaultWeek;
    document.getElementById('loginView').hidden = true;
    document.getElementById('appView').hidden = false;
    document.getElementById('userChip').textContent = me.user.displayName + ' · ' + me.user.branchName;
    document.getElementById('footer').textContent = State.meta.principle + '. Thời điểm hệ thống: ' + State.meta.now
      + (State.meta.clockFixed ? ' (cố định cho demo)' : '') + '.';
    this.renderNav();
    this.render();
  },

  async logout() {
    try { await API.post('/api/auth/logout'); } catch (e) { /* bỏ qua */ }
    API.csrf = null; State.me = null;
    this.showLogin();
  },

  renderNav() {
    const nav = document.getElementById('nav');
    nav.innerHTML = VIEWS.map(v => v.soon
      ? `<button type="button" disabled title="Sẽ có ở ${v.soon}">${U.esc(v.label)}<span class="soon">${v.soon}</span></button>`
      : `<button type="button" data-view="${v.id}" ${State.view === v.id ? 'aria-current="page"' : ''}>${U.esc(v.label)}</button>`).join('');
    nav.onclick = ev => {
      const b = ev.target.closest('[data-view]');
      if (b) { State.view = b.dataset.view; this.renderNav(); this.render(); }
    };
  },

  render() {
    const v = VIEWS.find(x => x.id === State.view);
    v.render(document.getElementById('main'));
  }
};

App.start();
