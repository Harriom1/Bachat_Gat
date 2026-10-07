/**
 * Centralized API Client for Bachat Gat / SHG Platform
 * Manages JWT tokens, authorization headers, 401 redirects, 403 alerts, and consistent JSON responses.
 */

const API_BASE = '/api';

class ApiClient {
  constructor() {
    this.token = localStorage.getItem('token') || null;
  }

  setToken(token) {
    this.token = token;
    if (token) {
      localStorage.setItem('token', token);
    } else {
      localStorage.removeItem('token');
    }
  }

  getToken() {
    if (!this.token) {
      this.token = localStorage.getItem('token');
    }
    return this.token;
  }

  async request(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
    const headers = {
      'Accept': 'application/json',
      ...options.headers
    };

    // Attach JWT Bearer Token if available
    const token = this.getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    if (options.body && !(options.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(options.body);
    }

    try {
      const response = await fetch(url, { ...options, headers });

      if (response.status === 401) {
        const isAuthEndpoint = url.includes('/api/auth/login') || url.includes('/api/auth/member-login') || url.includes('/api/auth/member-first-login-setup') || url.includes('/api/auth/change-password');
        if (!isAuthEndpoint) {
          localStorage.removeItem('token');
          localStorage.removeItem('user');
          if (!window.location.pathname.includes('login.html')) {
            window.location.href = '/login.html?expired=true';
          }
          throw new Error('Session expired. Please log in again.');
        } else {
          let errData;
          try {
            errData = await response.json();
          } catch (e) {}
          const msg = (errData && (errData.message || (errData.error && errData.error.message))) 
            || 'Invalid credentials. Please check your username, email, or password.';
          throw new Error(msg);
        }
      }

      if (response.status === 403) {
        const accessDeniedMessage = window.Common?.t('apiAccessDenied', 'Access denied: You do not have permission to view or execute this action.');
        if (window.Common && window.Common.showToast) {
          window.Common.showToast(accessDeniedMessage, 'danger');
        } else {
          alert(accessDeniedMessage);
        }
        throw new Error('Access forbidden: 403');
      }

      const data = await response.json();
      if (!response.ok || data.success === false) {
        const errorMsg = data.message || `Server error (${response.status})`;
        const error = new Error(errorMsg);
        error.status = response.status;
        error.details = data.data || null;
        error.code = data.data && data.data.code ? data.data.code : null;
        throw error;
      }

      return data.data !== undefined ? data.data : data;
    } catch (err) {
      console.error(`API Error [${endpoint}]:`, err);
      throw err;
    }
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

  getUserDashboard() {
    return this.request('/me/dashboard');
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

  getAdminDashboard(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    const query = targetGroupId ? `?groupId=${encodeURIComponent(targetGroupId)}` : '';
    return this.request(`/admin/dashboard${query}`);
  }

  getGroups() {
    return this.request('/admin/groups');
  }

  getGroupDropdown() {
    return this.request('/admin/groups/dropdown');
  }

  // ==========================================
  // CLIENT ADMIN APIs (Super Admin)
  // ==========================================
  getClientAdmins() {
    return this.request('/admin/client-admins');
  }

  createClientAdmin(adminData) {
    return this.request('/admin/client-admins', {
      method: 'POST',
      body: adminData
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
  getMasterData(groupId) {
    const targetGroupId = this.resolveAdminGroupId(groupId);
    return this.request(`/admin/groups/${encodeURIComponent(targetGroupId)}/master-data`);
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
}

// Global Singleton Instance
const api = new ApiClient();
window.api = api;

