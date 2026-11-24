/**
 * Centralized API Client for Bachat Gat / SHG Platform
 * Manages JWT tokens, authorization headers, 401 redirects, 403 alerts, and consistent JSON responses.
 */

const API_BASE = '/api';

// These policies apply only to explicitly opted-in read-only displays.
const cachePolicies = {
  adminDashboard: [30_000, 300_000],
  userDashboard: [45_000, 300_000],
  groupDropdown: [300_000, 600_000]
};
const cacheSessionKey = 'bachatgat.cache.session';
const cacheChangePrefix = 'bachatgat.cache.change.';
const memoryCache = new Map();
const inFlightReads = new Map();
let cacheDatabase;

function cachePolicy(name, group = '') {
  const [defaultTtl, defaultMax] = cachePolicies[name];
  const configured = window.BachatGatCachePolicy?.[name];
  const duration = (value, fallback, max) => Number.isFinite(value) ? Math.max(0, Math.min(value, max)) : fallback;
  const ttl = duration(configured?.ttlMs, defaultTtl, 600_000);
  return { family: name === 'groupDropdown' ? 'groups' : 'dashboard', group, ttl,
    maxAge: Math.max(ttl, duration(configured?.maxAgeMs, defaultMax, 1_800_000)) };
}

function cacheScope(token, policy, url) {
  if (!policy || !token) return null;
  try {
    const user = JSON.parse(localStorage.getItem('user'));
    const identity = user?.id || user?.userId || user?.memberId || user?.username;
    const expires = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp * 1000;
    if (!identity || user.firstLogin || !Number.isFinite(expires) || expires <= Date.now()) return null;
    let session = localStorage.getItem(cacheSessionKey);
    if (!session) {
      session = crypto.randomUUID();
      localStorage.setItem(cacheSessionKey, session);
    }
    const group = policy.group || user.groupId || '';
    const revision = cacheRevision(policy.family, group);
    const key = JSON.stringify([session, identity, user.role, user.designation, user.groupId, group, url]);
    return { session, revision, key, expires, group };
  } catch {
    return null;
  }
}

function openCacheDatabase() {
  if (!cacheDatabase) {
    cacheDatabase = new Promise(resolve => {
      try {
        if (!window.indexedDB) return resolve(null);
        indexedDB.deleteDatabase('bachatgat-api-cache-v1');
        const request = indexedDB.open('bachatgat-display-cache-v2', 1);
        const timeout = setTimeout(() => resolve(null), 250);
        request.onupgradeneeded = () => {
          const store = request.result.createObjectStore('responses', { keyPath: 'key' });
          store.createIndex('session', 'session');
          store.createIndex('expires', 'expires');
        };
        request.onsuccess = () => {
          clearTimeout(timeout);
          const db = request.result;
          db.onversionchange = () => { db.close(); cacheDatabase = null; };
          resolve(db);
        };
        request.onerror = request.onblocked = () => { clearTimeout(timeout); resolve(null); };
      } catch {
        resolve(null);
      }
    });
  }
  return cacheDatabase;
}

async function readApiCache(key) {
  if (memoryCache.has(key)) return memoryCache.get(key);
  const db = await openCacheDatabase();
  if (!db) return null;
  return new Promise(resolve => {
    try {
      const read = db.transaction('responses').objectStore('responses').get(key);
      read.onsuccess = () => resolve(read.result || null);
      read.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
}

function deleteCacheRecords(store, index, range) {
  store.index(index).openKeyCursor(range).onsuccess = event => {
    const cursor = event.target.result;
    if (!cursor) return;
    store.delete(cursor.primaryKey);
    cursor.continue();
  };
}

async function writeApiCache(record, isCurrent) {
  if (memoryCache.size >= 32) memoryCache.delete(memoryCache.keys().next().value);
  memoryCache.set(record.key, record);
  const db = await openCacheDatabase();
  if (!db || !isCurrent()) return;
  try {
    const store = db.transaction('responses', 'readwrite').objectStore('responses');
    store.put(record);
    deleteCacheRecords(store, 'expires', IDBKeyRange.upperBound(Date.now()));
  } catch {
    // Browser storage is optional.
  }
}

async function clearApiCache() {
  const session = localStorage.getItem(cacheSessionKey);
  localStorage.removeItem(cacheSessionKey);
  memoryCache.clear();
  if (!session) return;
  const db = await openCacheDatabase();
  if (!db) return;
  try {
    const cleanup = db.transaction('responses', 'readwrite');
    deleteCacheRecords(cleanup.objectStore('responses'), 'session', IDBKeyRange.only(session));
    await new Promise(resolve => {
      const timeout = setTimeout(resolve, 250);
      cleanup.oncomplete = cleanup.onabort = () => { clearTimeout(timeout); resolve(); };
    });
  } catch {
    // Old entries are inaccessible after the session key is removed.
  }
}

function cacheRevision(family, group = '') {
  const prefix = cacheChangePrefix + family;
  return [localStorage.getItem(prefix), group && localStorage.getItem(prefix + '.' + encodeURIComponent(group))].join('|');
}

function invalidateApiReads(endpoint, token) {
  const path = endpoint.split('?')[0];
  if (!token || /^(\/auth\/login|\/verification\/|\/admin\/reports\/|.*\/documents\/?$|.*\/meetings\/?$|\/payments\/order$)/.test(path)) return;
  const groupMatch = path.match(/^\/admin\/groups\/([^/]+)(?:\/|$)/);
  const groupKey = groupMatch?.[1] || '';
  const groupChange = /^\/admin\/groups(?:\/[^/]+(?:\/(?:archive|restore))?)?$/.test(path)
    || /\/office-bearers$|^\/admin\/client-admins/.test(path);
  const revision = Date.now() + ':' + Math.random();
  const scoped = groupKey && !/\/enroll-existing-member$/.test(path);
  try {
    localStorage.setItem(cacheChangePrefix + 'reads', revision);
    if (groupChange) localStorage.setItem(cacheChangePrefix + 'groups', revision);
    localStorage.setItem(cacheChangePrefix + 'dashboard' + (scoped ? '.' + groupKey : ''), revision);
  } catch {
    // A confirmed server mutation must still succeed if browser storage is full.
    void clearApiCache();
  }
}

function dashboardFreshness({ state, savedAt }) {
  const time = new Date(savedAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  const labels = {
    network: ['dashboardUpdated', 'Updated'],
    cached: ['dashboardSaved', 'Saved snapshot from'],
    refreshing: ['dashboardRefreshing', 'Refreshing saved snapshot from'],
    stale: ['dashboardStale', 'Refresh failed. Showing saved snapshot from'],
    offline: ['dashboardOffline', 'Offline. Showing saved snapshot from']
  };
  const [key, fallback] = labels[state];
  return (window.Common?.t(key, fallback) || fallback) + ' ' + time;
}

function watchDashboardRefresh(getGroup, refresh) {
  const visibleRefresh = () => { if (!document.hidden) void refresh(); };
  document.addEventListener('visibilitychange', visibleRefresh);
  window.addEventListener('online', visibleRefresh);
  window.addEventListener('offline', visibleRefresh);
  window.addEventListener('pageshow', event => { if (event.persisted) visibleRefresh(); });
  window.addEventListener('storage', event => {
    if (event.key === 'token' || event.key === 'user') {
      window.location.reload();
    } else if (event.key === cacheChangePrefix + 'dashboard' ||
               event.key === cacheChangePrefix + 'dashboard.' + encodeURIComponent(getGroup() || '')) {
      visibleRefresh();
    }
  });
}

class ApiClient {
  setToken(token) {
    if (token === this.getToken()) return;
    void clearApiCache();
    if (token) localStorage.setItem('token', token);
    else localStorage.removeItem('token');
  }

  getToken() {
    return localStorage.getItem('token');
  }

  async request(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : API_BASE + endpoint;
    const { displayCache, onSnapshot, ...fetchOptions } = options;
    const method = (options.method || 'GET').toUpperCase();
    const token = this.getToken();
    const relative = url.startsWith('/api/');
    const auth = /^\/auth\/(?:login|member-login|member-first-login-setup|change-password)$/.test(endpoint);
    const scope = relative && method === 'GET' && cacheScope(token, displayCache, url);
    const sameSession = () => this.getToken() === token && (!scope
      || (localStorage.getItem(cacheSessionKey) === scope.session && scope.expires > Date.now()));
    const isCurrent = () => sameSession() && (!scope
      || cacheRevision(displayCache.family, scope.group) === scope.revision);
    const stored = scope ? await readApiCache(scope.key) : null;
    // An identical GET can finish while this caller is waiting on IndexedDB.
    const cached = scope ? (memoryCache.get(scope.key) || stored) : null;
    const age = cached ? Date.now() - cached.savedAt : Infinity;
    const usable = cached && isCurrent() && cached.revision === scope.revision && cached.expires > Date.now()
      && age >= 0 && age <= displayCache.maxAge;
    const deliver = (record, state) => {
      onSnapshot?.(record.data, { state, savedAt: record.savedAt });
      return record.data;
    };
    if (usable && (age <= displayCache.ttl || !navigator.onLine)) {
      return deliver(cached, navigator.onLine ? 'cached' : 'offline');
    }
    if (usable && onSnapshot) deliver(cached, 'refreshing');

    const headers = { Accept: 'application/json', ...fetchOptions.headers };
    if (token && relative && !auth) headers.Authorization = 'Bearer ' + token;
    if (fetchOptions.body && !(fetchOptions.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      fetchOptions.body = JSON.stringify(fetchOptions.body);
    }
    const canShare = relative && method === 'GET' && Object.keys(fetchOptions).every(key => key === 'method');
    const readKey = canShare ? JSON.stringify([token, cacheRevision('reads'), url]) : null;
    let pending = readKey && inFlightReads.get(readKey);
    if (!pending) {
      pending = this.fetchResult(url, { ...fetchOptions, headers }, token, auth);
      if (readKey) {
        inFlightReads.set(readKey, pending);
        const remove = () => { if (inFlightReads.get(readKey) === pending) inFlightReads.delete(readKey); };
        pending.then(remove, remove);
      }
    }
    try {
      const result = await pending;
      if (method !== 'GET' && relative) invalidateApiReads(endpoint, token);
      if (method === 'GET' && !sameSession()) throw new Error('Session changed. Reload the page to continue.');
      if (method === 'GET' && !isCurrent()) return this.request(endpoint, options);
      const savedAt = Date.now();
      if (scope) void writeApiCache({ ...scope, savedAt,
        expires: Math.min(savedAt + displayCache.maxAge, scope.expires), data: result }, isCurrent);
      return deliver({ data: result, savedAt }, 'network');
    } catch (err) {
      if (usable && isCurrent() && cached.expires > Date.now()
          && (err.network === true || err.status >= 500)) {
        return deliver(cached, navigator.onLine ? 'stale' : 'offline');
      }
      throw err;
    }
  }

  async fetchResult(url, requestOptions, token, auth) {
    let response;
    try {
      response = await fetch(url, requestOptions);
    } catch (cause) {
      if (cause.name === 'AbortError') throw cause;
      throw Object.assign(new Error('Could not connect to the server.', { cause }), { network: true });
    }
    const data = await response.json().catch(() => null);
    if (response.status === 401 && !auth && this.getToken() === token) {
      this.setToken(null);
      localStorage.removeItem('user');
      if (!window.location.pathname.includes('login.html')) window.location.href = '/login.html?expired=true';
    }
    if (response.status === 403 && this.getToken() === token) {
      void clearApiCache();
      const message = data?.message || window.Common?.t('apiAccessDenied',
        'Access denied: You do not have permission to view or execute this action.');
      if (window.Common?.showToast) window.Common.showToast(message, 'danger');
      else alert(message || 'Access denied.');
    }
    if (!response.ok || !data || data.success === false) {
      const message = data?.message || data?.error?.message;
      throw Object.assign(new Error(message || (response.status === 401
        ? (auth ? 'Invalid credentials. Please check your username, email, or password.' : 'Session expired. Please log in again.')
        : 'Server error (' + response.status + ')')), {
        status: response.status, details: data?.data || null, code: data?.data?.code || null
      });
    }
    return data.data !== undefined ? data.data : data;
  }


  // ==========================================
  // AUTHENTICATION APIs
  // ==========================================
  login(username, password, groupId = null, memberId = null) {
    const body = { username, password };
    if (groupId) body.groupId = groupId;
    if (memberId) body.memberId = memberId;
    return this.request('/auth/login', {
      method: 'POST',
      body
    });
  }

  memberLogin(groupId, memberId, password) {
    return this.request('/auth/login', {
      method: 'POST',
      body: { groupId, memberId, password }
    });
  }

  getPublicGroups() {
    return this.request('/auth/groups');
  }

  searchPublicGroups(query) {
    return this.request(`/auth/groups/search?q=${encodeURIComponent(query)}`);
  }

  getPublicGroupMembers(groupId) {
    return this.request(`/auth/groups/${encodeURIComponent(groupId)}/members`);
  }

  changePassword(currentPassword, newPassword) {
    return this.request('/auth/change-password', {
      method: 'POST',
      body: { currentPassword, newPassword }
    });
  }

  changeUsername(newUsername, confirmUsername) {
    return this.request('/auth/change-username', {
      method: 'POST',
      body: { newUsername, confirmUsername }
    });
  }

  firstLoginChangePassword(newPassword, confirmPassword = null) {
    return this.request('/auth/first-login-change-password', {
      method: 'POST',
      body: { newPassword, confirmPassword: confirmPassword || newPassword }
    });
  }

  memberFirstLoginSetup(groupId, identifier, password, confirmPassword) {
    return this.request('/auth/member-first-login-setup', {
      method: 'POST',
      body: { groupId, identifier, password, confirmPassword }
    });
  }

  // ==========================================
  // CURRENT USER (SELF-SERVICE) APIs
  // ==========================================
  getCurrentUserProfile() {
    return this.request('/me');
  }

  getUserDashboard(onSnapshot) {
    return this.request('/me/dashboard', { displayCache: onSnapshot && cachePolicy('userDashboard'), onSnapshot });
  }

  getMySavings() {
    return this.request('/me/savings');
  }

  payMyDue(paymentData) {
    return this.request('/me/pay', {
      method: 'POST',
      body: paymentData
    });
  }

  getMyCollections() {
    return this.request('/me/collections');
  }

  getMyGroupCollections() {
    return this.request('/me/group-collections');
  }

  getMyMonthlyReport(month, year) {
    return this.request(`/me/report/monthly?month=${encodeURIComponent(month)}&year=${encodeURIComponent(year)}`);
  }

  getMyGroupLoans() {
    return this.request('/me/group-loans');
  }

  getMyLoans() {
    return this.request('/me/loans');
  }

  getMyLoanSchedule(loanId) {
    return this.request(`/me/loans/${loanId}/schedule`);
  }

  submitLoanApplication(applicationData) {
    return this.request('/me/loan-applications', {
      method: 'POST',
      body: applicationData
    });
  }

  getMyLoanApplications() {
    return this.request('/me/loan-applications');
  }

  getMyTransactions() {
    return this.request('/me/transactions');
  }

  getMyDocuments() {
    return this.request('/me/documents');
  }

  uploadMyDocument(formData) {
    return this.request('/me/documents', {
      method: 'POST',
      body: formData
    });
  }

  getMyNotifications() {
    return this.request('/me/notifications');
  }

  getMyStatement() {
    return this.request('/me/statement');
  }

  // ==========================================
  // ADMIN APIs
  // ==========================================
  resolveAdminGroupId(groupId) {
    const user = (window.Auth && window.Auth.getUser) ? window.Auth.getUser() : null;
    const isScoped = user && user.groupId && ['ADMIN', 'PRESIDENT', 'SECRETARY', 'TREASURER'].includes((user.role || '').toUpperCase());
    if (isScoped) {
      return user.groupId;
    }
    return groupId || (user ? user.groupId : 'bg-001');
  }

  getAdminDashboard(groupId, onSnapshot) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const query = targetGroupId ? `?groupId=${encodeURIComponent(targetGroupId)}` : '';
    return this.request(`/admin/dashboard${query}`, { displayCache: onSnapshot && targetGroupId && cachePolicy('adminDashboard', targetGroupId), onSnapshot });
  }

  getGroups() {
    return this.request('/admin/groups');
  }

  getArchivedGroups() {
    return this.request('/admin/groups/archived');
  }

  getGroupDropdown() {
    return this.request('/admin/groups/dropdown', { displayCache: cachePolicy('groupDropdown') });
  }

  // ==========================================
  // CLIENT ADMIN APIs (Super Admin)
  // ==========================================
  getClientAdmins(includeGroupProfiles = false) {
    return this.request(includeGroupProfiles ? '/admin/client-admins?includeGroupProfiles=true' : '/admin/client-admins');
  }

  createClientAdmin(adminData) {
    return this.request('/admin/client-admins', {
      method: 'POST',
      body: adminData
    });
  }

  sendEmailVerificationOtp(sessionId, target, challengeId = null) {
    return this.request('/verification/email/send', {
      method: 'POST',
      body: { sessionId, target, challengeId }
    });
  }

  verifyEmailVerificationOtp(challengeId, sessionId, target, code) {
    return this.request('/verification/email/verify', {
      method: 'POST',
      body: { challengeId, sessionId, target, code }
    });
  }

  verifyFirebaseMobile(sessionId, phoneNumber, idToken) {
    return this.request('/verification/mobile/firebase/verify', {
      method: 'POST',
      body: { sessionId, phoneNumber, idToken }
    });
  }

  resetClientAdminPassword(userId, newPassword) {
    return this.request(`/admin/client-admins/${userId}/reset-password`, {
      method: 'POST',
      body: { temporaryPassword: newPassword, newPassword: newPassword }
    });
  }

  toggleClientAdminStatus(userId, enabled) {
    return this.request(`/admin/client-admins/${userId}/status`, {
      method: 'PATCH',
      body: { enabled }
    });
  }

  createGroup(groupData) {
    return this.request('/admin/groups', {
      method: 'POST',
      body: groupData
    });
  }

  getGroupById(id) {
    return this.request(`/admin/groups/${id}`);
  }

  updateGroup(id, groupData) {
    return this.request(`/admin/groups/${id}`, {
      method: 'PUT',
      body: groupData
    });
  }

  archiveGroup(id) {
    return this.request(`/admin/groups/${encodeURIComponent(id)}/archive`, { method: 'POST' });
  }

  restoreGroup(id) {
    return this.request(`/admin/groups/${encodeURIComponent(id)}/restore`, { method: 'POST' });
  }

  getAllMembers() {
    return this.request('/admin/members');
  }

  getMembers(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    if (!targetGroupId) return this.getAllMembers();
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/members`);
  }

  getMemberById(id) {
    return this.request(`/admin/members/${id}`);
  }

  getMemberLoginStatus(id) {
    return this.request(`/admin/members/${id}/login-status`);
  }

  createMemberLogin(id, loginData) {
    return this.request(`/admin/members/${id}/create-login`, {
      method: 'POST',
      body: loginData
    });
  }

  resetMemberPassword(id, newPassword) {
    return this.request(`/admin/members/${id}/reset-password`, {
      method: 'POST',
      body: { temporaryPassword: newPassword, newPassword: newPassword }
    });
  }

  enrollExistingMember(groupId, enrollData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/enroll-existing-member`, {
      method: 'POST',
      body: enrollData
    });
  }

  removeMemberFromGroup(groupId, memberId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/members/${memberId}`, {
      method: 'DELETE'
    });
  }

  createMember(groupId, memberData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/members`, {
      method: 'POST',
      body: memberData
    });
  }

  updateMember(id, memberData) {
    return this.request(`/admin/members/${id}`, {
      method: 'PUT',
      body: memberData
    });
  }

  updateMemberStatus(id, status) {
    return this.request(`/admin/members/${id}/status`, {
      method: 'PATCH',
      body: { status }
    });
  }

  archiveMember(id) {
    return this.request(`/admin/members/${encodeURIComponent(id)}/archive`, { method: 'POST' });
  }

  restoreMember(id) {
    return this.request(`/admin/members/${encodeURIComponent(id)}/restore`, { method: 'POST' });
  }

  getMemberRefunds(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/refunds`);
  }

  updateRefundStatus(refundId, status, failureReason = null) {
    return this.request(`/admin/refunds/${encodeURIComponent(refundId)}/status`, {
      method: 'PATCH', body: { status, failureReason }
    });
  }

  getMyExitRefunds() { return this.request('/me/exit-refunds'); }

  requestMemberExit(reason = 'Member self-exit') {
    return this.request('/me/exit', { method: 'POST', body: { reason } });
  }

  getCollections(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/collections`);
  }

  recordCollectionPayment(groupId, paymentData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/collections`, {
      method: 'POST',
      body: paymentData
    });
  }

  generateMonthlyCollections(groupId, month, year) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/collections/monthly`, {
      method: 'POST',
      body: { groupId: targetGroupId, month, year }
    });
  }

  getLoanApplications(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const query = targetGroupId ? `?groupId=${encodeURIComponent(targetGroupId)}` : '';
    return this.request(`/admin/loan-applications${query}`);
  }

  getLoanApplicationById(id) {
    return this.request(`/admin/loan-applications/${id}`);
  }

  approveLoanApplication(id, approvalData) {
    return this.request(`/admin/loan-applications/${id}/approve`, {
      method: 'POST',
      body: approvalData
    });
  }

  rejectLoanApplication(id, reason) {
    return this.request(`/admin/loan-applications/${id}/reject`, {
      method: 'POST',
      body: { reason }
    });
  }

  getLoans(groupId, month = null, year = null, status = null, memberId = null) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const params = new URLSearchParams();
    if (targetGroupId) params.append('groupId', targetGroupId);
    if (month) params.append('month', month);
    if (year) params.append('year', year);
    if (status && status !== 'ALL') params.append('status', status);
    if (memberId) params.append('memberId', memberId);
    const qs = params.toString() ? `?${params.toString()}` : '';
    return this.request(`/admin/loans${qs}`);
  }

  getMonthlyLoanStatus(groupId, month = null, year = null) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const params = new URLSearchParams();
    if (targetGroupId) params.append('groupId', targetGroupId);
    if (month) params.append('month', month);
    if (year) params.append('year', year);
    const qs = params.toString() ? `?${params.toString()}` : '';
    return this.request(`/admin/loans/monthly-status${qs}`);
  }

  disburseLoan(loanId, disbursementData = {}) {
    return this.request(`/admin/loans/${loanId}/disburse`, {
      method: 'POST',
      body: disbursementData
    });
  }

  getPendingSavings(groupId, month = null, year = null) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const params = new URLSearchParams();
    if (month) params.append('month', month);
    if (year) params.append('year', year);
    const qs = params.toString() ? `?${params.toString()}` : '';
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/collections/pending${qs}`);
  }

  getPendingSavingsForYear(groupId, year) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request('/admin/groups/' + encodeURIComponent(targetGroupId)
      + '/collections/pending/year?year=' + encodeURIComponent(year));
  }

  getMyGroupInfo() {
    return this.request('/me/group-info');
  }

  getMyGroupRules() {
    return this.request('/me/group-rules');
  }

  getLoanById(id) {
    return this.request(`/admin/loans/${id}`);
  }

  getLoanSchedule(id) {
    return this.request(`/admin/loans/${id}/schedule`);
  }

  recordLoanRepayment(loanId, repaymentData) {
    return this.request(`/admin/loans/${loanId}/repayment`, {
      method: 'POST',
      body: repaymentData
    });
  }

  recordLoanPartialPayment(loanId, paymentData) {
    return this.request(`/admin/loans/${loanId}/partial-payment`, {
      method: 'POST',
      body: paymentData
    });
  }

  recordLoanExtraPayment(loanId, extraPaymentData) {
    return this.request(`/admin/loans/${loanId}/extra-payment`, {
      method: 'POST',
      body: extraPaymentData
    });
  }

  getTransactions(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const query = targetGroupId ? `?groupId=${encodeURIComponent(targetGroupId)}` : '';
    return this.request(`/admin/transactions${query}`);
  }

  getMonthlyReport(groupId, month, year) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/reports/monthly?groupId=${encodeURIComponent(targetGroupId)}&month=${month}&year=${year}`);
  }

  getFinancialSummary(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/reports/summary?groupId=${encodeURIComponent(targetGroupId)}`);
  }

  getMemberStatement(memberId) {
    return this.request(`/admin/reports/member-statement/${memberId}`);
  }

  getAuditLogs(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const query = targetGroupId ? `?groupId=${encodeURIComponent(targetGroupId)}` : '';
    return this.request(`/admin/audit-logs${query}`);
  }

  getGroupDocuments(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/documents/group/${encodeURIComponent(targetGroupId)}`);
  }

  uploadDocument(formData) {
    return this.request('/admin/documents', {
      method: 'POST',
      body: formData
    });
  }

  getExpenses(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/expenses`);
  }

  recordExpense(groupId, expenseData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/expenses`, {
      method: 'POST',
      body: expenseData
    });
  }

  distributeExpense(groupId, expenseId, distributionData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/expenses/${expenseId}/distribute`, {
      method: 'POST',
      body: distributionData
    });
  }

  // ==========================================
  // OTHER INCOME APIs (Section 14 & 16)
  // ==========================================
  getIncomes(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/income`);
  }

  getIncome(groupId) {
    return this.getIncomes(groupId);
  }

  recordIncome(groupId, incomeData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/income`, {
      method: 'POST',
      body: incomeData
    });
  }

  distributeIncome(groupId, incomeId, distributionData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/income/${incomeId}/distribute`, {
      method: 'POST',
      body: distributionData
    });
  }

  // ==========================================
  // MASTER DATA APIs (Section 7, 8, 9, 58)
  // ==========================================
  getMasterData(groupId, effectiveToday = false) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const query = effectiveToday ? '?effectiveToday=true' : '';
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/master-data${query}`);
  }

  getMasterDataHistory(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/master-data/history`);
  }

  updateMasterData(groupId, masterData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/master-data`, {
      method: 'PUT',
      body: masterData
    });
  }

  // ==========================================
  // EXTENDED REPORT & SHARING APIs (Section 50-53)
  // ==========================================
  getYearlyReport(groupId, year) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/reports/yearly?groupId=${encodeURIComponent(targetGroupId)}&year=${year}`);
  }

  getOutstandingReport(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/reports/outstanding?groupId=${encodeURIComponent(targetGroupId)}`);
  }

  getReportDeliveryStatus() {
    return this.request('/admin/reports/delivery-status');
  }

  shareReportWhatsApp(groupId, shareData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/reports/share-whatsapp?groupId=${encodeURIComponent(targetGroupId)}`, {
      method: 'POST',
      body: shareData
    });
  }

  sendReportEmail(groupId, shareData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/reports/send-email?groupId=${encodeURIComponent(targetGroupId)}`, {
      method: 'POST',
      body: shareData
    });
  }

  getMeetings(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/meetings`);
  }

  recordMeeting(groupId, meetingData) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/meetings`, {
      method: 'POST',
      body: meetingData
    });
  }

  // ==========================================
  // PAYMENT INTEGRATION APIs
  // ==========================================
  createPaymentOrder(orderData) {
    return this.request('/payments/order', {
      method: 'POST',
      body: orderData
    });
  }

  verifyPayment(verificationData) {
    return this.request('/payments/verify', {
      method: 'POST',
      body: verificationData
    });
  }

  createPaymentOrder(orderData) {
    return this.request('/payments/order', {
      method: 'POST',
      body: orderData
    });
  }

  verifyPayment(verificationData) {
    return this.request('/payments/verify', {
      method: 'POST',
      body: verificationData
    });
  }

  getPaymentReceipt(orderId) {
    return this.request(`/payments/receipt/${encodeURIComponent(orderId)}`);
  }

  recordManualPayment(paymentData) {
    return this.request('/payments/manual', {
      method: 'POST',
      body: paymentData
    });
  }

  getPaymentHistory(groupId = null, memberId = null) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const params = new URLSearchParams();
    if (targetGroupId) params.append('groupId', targetGroupId);
    const qs = params.toString() ? `?${params.toString()}` : '';
    return this.request(`/payments/history${qs}`);
  }

  designateOfficeBearer(groupId, data) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${targetGroupId}/office-bearers`, {
      method: 'POST',
      body: data
    });
  }

  getLoanEligibility() {
    return this.request('/me/loan-eligibility');
  }

  getLoanRepaymentPreview(amount, durationMonths) {
    const query = new URLSearchParams({amount, durationMonths});
    return this.request(`/me/loan-repayment-preview?${query}`);
  }
}

// Global Singleton Instance
const api = new ApiClient();
window.api = api;

