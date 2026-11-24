/**
 * Client Authentication & Role Authorization Manager
 */

const Auth = {
  getUser() {
    const raw = localStorage.getItem('user');
    try {
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      return null;
    }
  },

  setUser(user) {
    const previous = this.getUser();
    const scope = value => value && JSON.stringify([
      value.id || value.userId || value.memberId || value.username,
      value.groupId, value.role, value.designation
    ]);
    if (scope(previous) !== scope(user) && typeof clearApiCache === 'function') void clearApiCache();
    if (user) {
      localStorage.setItem('user', JSON.stringify(user));
    } else {
      localStorage.removeItem('user');
    }
  },

  isAuthenticated() {
    return !!localStorage.getItem('token') && !!this.getUser();
  },

  isAdmin() {
    const user = this.getUser();
    if (!user) return false;
    const r = (user.role || '').toUpperCase();
    const d = (user.designation || '').toUpperCase();
    return (
      r === 'ADMIN' ||
      r === 'SUPER_ADMIN' ||
      r === 'PRESIDENT' ||
      r === 'SECRETARY' ||
      r === 'TREASURER' ||
      d === 'PRESIDENT' ||
      d === 'SECRETARY' ||
      d === 'TREASURER'
    );
  },

  isSuperAdmin() {
    const user = this.getUser();
    return user && (user.role || '').toUpperCase() === 'SUPER_ADMIN';
  },

  isPresident() {
    const user = this.getUser();
    if (!user) return false;
    const r = (user.role || '').toUpperCase();
    const d = (user.designation || '').toUpperCase();
    return r === 'PRESIDENT' || r === 'ADMIN' || r === 'SUPER_ADMIN' || d === 'PRESIDENT';
  },

  isOfficeBearer() {
    const user = this.getUser();
    if (!user) return false;
    const r = (user.role || '').toUpperCase();
    const d = (user.designation || '').toUpperCase();
    return (
      r === 'PRESIDENT' ||
      r === 'SECRETARY' ||
      r === 'TREASURER' ||
      d === 'PRESIDENT' ||
      d === 'SECRETARY' ||
      d === 'TREASURER'
    );
  },

  isUser() {
    const user = this.getUser();
    if (!user) return false;
    const r = (user.role || '').toUpperCase();
    return r === 'USER' || r === 'MEMBER';
  },

  /**
   * Enforces role protection on frontend HTML pages.
   * If not logged in, redirects to /login.html.
   * If a USER attempts to open an admin page, redirects to /user/dashboard.html!
   */
  requireAuth(expectedRole = null) {
    if (!this.isAuthenticated()) {
      window.location.href = '/login.html';
      return false;
    }

    const user = this.getUser();
    if (expectedRole === 'ADMIN' && !this.isAdmin()) {
      const message = window.Common?.t('apiAccessDenied', 'Access Denied: You do not have administrator permissions.')
        || 'Access Denied: You do not have administrator permissions.';
      alert(message);
      window.location.href = '/user/dashboard.html';
      return false;
    }

    if (expectedRole === 'USER' && this.isAdmin()) {
      // If admin visits a user page, optionally allow or redirect to admin dashboard
    }

    return true;
  },

  requireAdmin() {
    return this.requireAuth('ADMIN');
  },

  requireUser() {
    return this.requireAuth('USER');
  },

  async logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('activeGroupId');
    if (typeof clearApiCache === 'function') await clearApiCache();
    window.location.href = '/login.html';
  }
};

window.Auth = Auth;
