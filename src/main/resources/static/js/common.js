/**
 * Common Utilities, Formatting, and UI Component Initializers
 */

const Common = {
  /**
   * Formats a numeric amount in Indian Currency format: e.g. ₹1,00,000.00
   */
  formatINR(amount) {
    if (amount === undefined || amount === null || isNaN(amount)) {
      return '₹0';
    }
    const num = Number(amount);
    const hasDecimals = (num % 1 !== 0);
    return new Intl.NumberFormat(this.getLocale(), {
      style: 'currency',
      currency: 'INR',
      minimumFractionDigits: hasDecimals ? 2 : 0,
      maximumFractionDigits: hasDecimals ? 2 : 0
    }).format(num);
  },

  formatIndianCurrency(amount) {
    return this.formatINR(amount);
  },

  /** Human-facing member number, scoped to the member's own group. */
  getGroupScopedMemberRegistrationId(member) {
    const memberId = String(member?.memberId || '');
    const match = memberId.match(/-M(\d+)$/i);
    if (!match) return member?.registrationId || member?.memberId || '-';
    return `MEM-${new Date().getFullYear()}-${String(parseInt(match[1], 10)).padStart(6, '0')}`;
  },

  formatCurrency(amount) {
    return this.formatINR(amount);
  },

  /**
   * Formats a date string into Indian DD/MM/YYYY format
   */
  formatDate(dateStr) {
    if (!dateStr) return '-';
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return dateStr;
      return d.toLocaleDateString(this.getLocale(), {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
      });
    } catch (e) {
      return dateStr;
    }
  },

  /**
   * Formats a date string into Indian DD/MM/YYYY HH:mm format
   */
  formatDateTime(dateStr) {
    if (!dateStr) return '-';
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return dateStr;
      return d.toLocaleDateString(this.getLocale(), {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
      }) + ' ' + d.toLocaleTimeString(this.getLocale(), {
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch (e) {
      return dateStr;
    }
  },

  getLocale() {
    const lang = window.i18n?.currentLang || localStorage.getItem('app_lang') || 'en';
    return lang === 'mr' ? 'mr-IN' : (lang === 'hi' ? 'hi-IN' : 'en-IN');
  },

  t(key, fallback = '') {
    return window.i18n && typeof window.i18n.t === 'function'
      ? window.i18n.t(key, fallback)
      : (fallback || key || '');
  },

  /**
   * Returns localized month name for month index (1-12)
   */
  getMonthName(monthNum, lang = null) {
    const m = parseInt(monthNum);
    if (!m || m < 1 || m > 12) return String(monthNum || '');
    const monthKeys = ['', 'monthJan', 'monthFeb', 'monthMar', 'monthApr', 'monthMay', 'monthJun', 'monthJul', 'monthAug', 'monthSep', 'monthOct', 'monthNov', 'monthDec'];
    const key = monthKeys[m];
    if (key && window.i18n && typeof window.i18n.t === 'function') {
      const translated = window.i18n.t(key);
      if (translated && translated !== key) return translated;
    }
    const defaultEn = ['', 'January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    return defaultEn[m] || String(monthNum);
  },

  getMonthShortName(monthNum, lang = null) {
    return this.getMonthName(monthNum, lang);
  },

  /**
   * Resolves the authoritative active group ID for the logged-in user.
   * Scoped Group Admins (ADMIN) always use their assigned groupId.
   */
  getSelectedGroupId() {
    const user = (window.Auth && window.Auth.getUser) ? window.Auth.getUser() : null;
    const isScoped = user && user.groupId && ['ADMIN', 'PRESIDENT', 'SECRETARY', 'TREASURER'].includes((user.role || '').toUpperCase());
    if (isScoped) {
      return user.groupId;
    }
    const select = document.getElementById('active-group-select');
    if (select && select.value) {
      return select.value;
    }
    return localStorage.getItem('activeGroupId') || (user ? user.groupId : 'bg-001');
  },

  /**
   * Returns HTML status badge with appropriate color theme
   */
  getStatusBadge(status) {
    if (!status) return '';
    const s = String(status).toUpperCase();
    let badgeClass = 'badge-pending';

    if (['PAID', 'ACTIVE', 'APPROVED', 'COMPLETED', 'VERIFIED'].includes(s)) {
      badgeClass = 'badge-paid';
    } else if (['PARTIAL', 'PARTIALLY_PAID'].includes(s)) {
      badgeClass = 'badge-partial';
    } else if (['OVERDUE', 'REJECTED', 'SUSPENDED'].includes(s)) {
      badgeClass = 'badge-overdue';
    } else if (['UPCOMING'].includes(s)) {
      badgeClass = 'badge-pending';
    } else if (['INACTIVE', 'EXITED', 'CANCELLED', 'CLOSED'].includes(s)) {
      badgeClass = 'badge-inactive';
    }

    const label = window.i18n ? window.i18n.translateStatus(s) : s;
    return `<span class="badge-status ${badgeClass}">${label}</span>`;
  },

  getRoleDisplayName(roleOrUser, lang = null) {
    const currentLang = lang || (window.i18n ? window.i18n.currentLang : (localStorage.getItem('app_lang') || 'en'));
    let roleStr = '';
    let designationStr = '';
    if (typeof roleOrUser === 'object' && roleOrUser !== null) {
      designationStr = roleOrUser.designation || '';
      roleStr = roleOrUser.role || '';
    } else {
      roleStr = String(roleOrUser || '');
    }

    const key = (designationStr || roleStr).toUpperCase();
    if (key.includes('PRESIDENT') || key === 'ADMIN' || key === 'GROUP_ADMIN') {
      if (currentLang === 'mr') return 'अध्यक्ष / President';
      if (currentLang === 'hi') return 'अध्यक्ष / President';
      return 'President';
    }
    if (key.includes('SECRETARY')) {
      if (currentLang === 'mr') return 'सचिव / Sachiva';
      if (currentLang === 'hi') return 'सचिव / Sachiva';
      return 'Secretary / Sachiva';
    }
    if (key.includes('TREASURER')) {
      if (currentLang === 'mr') return 'खजिनदार / Khajindar';
      if (currentLang === 'hi') return 'कोषाध्यक्ष / Khajindar';
      return 'Treasurer / Khajindar';
    }
    if (key.includes('SUPER_ADMIN')) {
      if (currentLang === 'mr') return 'सुपर ॲडमिन / Super Admin';
      if (currentLang === 'hi') return 'सुपर एडमिन / Super Admin';
      return 'Super Admin';
    }
    if (key.includes('MEMBER') || key.includes('USER')) {
      if (currentLang === 'mr') return 'सदस्य / Member';
      if (currentLang === 'hi') return 'सदस्य / Member';
      return 'Member';
    }
    return roleStr || 'Member';
  },

  getRoleBadge(roleOrUser, lang = null) {
    const title = this.getRoleDisplayName(roleOrUser, lang);
    let key = '';
    if (typeof roleOrUser === 'object' && roleOrUser !== null) {
      key = (roleOrUser.designation || roleOrUser.role || '').toUpperCase();
    } else {
      key = String(roleOrUser || '').toUpperCase();
    }
    let badgeColor = 'background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe;';
    if (key.includes('PRESIDENT') || key === 'ADMIN' || key === 'GROUP_ADMIN') {
      badgeColor = 'background: #fdf2f8; color: #be185d; border: 1px solid #fbcfe8;';
    } else if (key.includes('SECRETARY')) {
      badgeColor = 'background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0;';
    } else if (key.includes('TREASURER')) {
      badgeColor = 'background: #fefce8; color: #a16207; border: 1px solid #fef08a;';
    }
    return `<span style="display: inline-block; padding: 2px 8px; border-radius: 9999px; font-size: 0.75rem; font-weight: 700; ${badgeColor}">${title}</span>`;
  },

  /**
   * Shows a clean toast notification banner
   */
  showToast(message, type = 'success') {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.style.position = 'fixed';
      container.style.top = '20px';
      container.style.right = '20px';
      container.style.zIndex = '9999';
      container.style.display = 'flex';
      container.style.flexDirection = 'column';
      container.style.gap = '10px';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast-alert toast-${type}`;
    toast.style.background = type === 'success' ? '#10b981' : (type === 'danger' ? '#ef4444' : '#0284c7');
    toast.style.color = '#fff';
    toast.style.padding = '12px 20px';
    toast.style.borderRadius = '8px';
    toast.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
    toast.style.fontSize = '0.9rem';
    toast.style.fontWeight = '500';
    toast.style.display = 'flex';
    toast.style.alignItems = 'center';
    toast.style.gap = '8px';
    toast.style.transition = 'all 0.3s ease';
    toast.textContent = message;

    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(-10px)';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  },

  /**
   * Cross-page reliable modal opener that properly overcomes !important CSS rules
   */
  openModal(modalIdOrEl) {
    const el = typeof modalIdOrEl === 'string' ? document.getElementById(modalIdOrEl) : modalIdOrEl;
    if (!el) return;
    el.classList.add('show', 'active');
    el.style.setProperty('display', 'flex', 'important');
  },

  /**
   * Cross-page reliable modal closer
   */
  closeModal(modalIdOrEl) {
    const el = typeof modalIdOrEl === 'string' ? document.getElementById(modalIdOrEl) : modalIdOrEl;
    if (!el) return;
    el.classList.remove('show', 'active', 'open');
    el.style.setProperty('display', 'none', 'important');
  },

  /**
   * Escape HTML entities to prevent XSS and rendering errors
   */
  escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str).replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]));
  },

  /**
   * Global modal dismisser: binds clicks on all ✕ close buttons, backdrop clicks, and Escape key
   */
  initModalListeners() {
    // 1. Click listener for ✕ buttons and backdrops
    document.addEventListener('click', (e) => {
      // Check if clicked close button or inside close button (e.g. icon/text)
      const closeBtn = e.target.closest('.btn-close-modal, .modal-close-btn, .close-modal, [data-dismiss="modal"], .modal-close, .btn-close');
      if (closeBtn) {
        e.preventDefault();
        e.stopPropagation();
        const modal = closeBtn.closest('.modal-backdrop, .modal-overlay, .modal, .modal-dialog, [id^="modal-"], [id$="-modal"]');
        if (modal) {
          Common.closeModal(modal);
        }
        return;
      }

      // Check if clicking directly on the backdrop/overlay
      if (e.target.classList.contains('modal-backdrop') || e.target.classList.contains('modal-overlay')) {
        Common.closeModal(e.target);
      }
    });

    // 2. Escape key closes open modals
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' || e.key === 'Esc') {
        const openModals = document.querySelectorAll('.modal-backdrop.show, .modal-backdrop.active, .modal-overlay.show, .modal-overlay.active, .modal-backdrop[style*="flex"], .modal-overlay[style*="flex"]');
        openModals.forEach(m => Common.closeModal(m));
      }
    });
  },
  /**
   * Safe text renderer to prevent raw 'undefined', 'null', 'NaN', '[object Object]'
   */
  text(val, fallback = '-') {
    if (val === undefined || val === null || val === 'undefined' || val === 'null' || Number.isNaN(val)) {
      return fallback;
    }
    if (typeof val === 'object') {
      return fallback;
    }
    const str = String(val).trim();
    return str.length === 0 ? fallback : str;
  },

  /**
   * Resolves localized name for a group or member based on active language
   */
  getLocalizedGroupName(group, lang = null) {
    if (!group) return '-';
    const currentLang = lang || (window.i18n ? window.i18n.currentLang : (localStorage.getItem('app_lang') || 'en'));
    if (currentLang === 'mr' && group.groupNameMr && group.groupNameMr.trim()) {
      return group.groupNameMr.trim();
    }
    if (currentLang === 'hi' && group.groupNameHi && group.groupNameHi.trim()) {
      return group.groupNameHi.trim();
    }
    return group.groupName || group.name || group.groupCode || '-';
  },

  getLocalizedMemberName(member, lang = null) {
    if (!member) return '-';
    const currentLang = lang || (window.i18n ? window.i18n.currentLang : (localStorage.getItem('app_lang') || 'en'));
    if (currentLang === 'mr' && member.fullNameMr && member.fullNameMr.trim()) {
      return member.fullNameMr.trim();
    }
    if (currentLang === 'hi' && member.fullNameHi && member.fullNameHi.trim()) {
      return member.fullNameHi.trim();
    }
    return member.fullName || member.name || '-';
  },

  /**
   * Initializes group dropdown selectors across all admin views.
   * Pulls clean dropdown data from /api/admin/groups/dropdown.
   * Auto-refreshes when language changes.
   */
  cachedGroups: null,
  async initGroupSelector() {
    const selects = document.querySelectorAll('#active-group-select, .group-select');
    if (!selects || selects.length === 0) return;
    if (!Auth.isAuthenticated()) return;

    try {
      if (!this.cachedGroups) {
        this.cachedGroups = await api.getGroupDropdown();
      }
      const groups = this.cachedGroups || [];
      const currentLang = window.i18n ? window.i18n.currentLang : (localStorage.getItem('app_lang') || 'en');
      let activeGroupId = localStorage.getItem('activeGroupId');

      const user = Auth.getUser();
      const isScoped = user && user.groupId && ['ADMIN', 'PRESIDENT', 'SECRETARY', 'TREASURER'].includes((user.role || '').toUpperCase());
      // For Group Admin (President / Office Bearer), strictly scope to their assigned group
      if (isScoped) {
        activeGroupId = user.groupId;
        localStorage.setItem('activeGroupId', activeGroupId);
      }

      // Ensure activeGroupId is valid in current list
      if (!groups.some(g => g.id === activeGroupId)) {
        activeGroupId = groups.length > 0 ? groups[0].id : null;
        if (activeGroupId) {
          localStorage.setItem('activeGroupId', activeGroupId);
        }
      }

      const availableGroups = isScoped
        ? (groups.filter(g => g.id === user.groupId).length > 0 ? groups.filter(g => g.id === user.groupId) : groups)
        : groups;

      selects.forEach(select => {
        select.innerHTML = '';
        if (availableGroups.length === 0) {
          const opt = document.createElement('option');
          opt.value = activeGroupId || '';
          opt.textContent = window.i18n ? window.i18n.t('noGroupAssigned', 'No Groups Available') : 'No Groups Available';
          select.appendChild(opt);
          return;
        }

        availableGroups.forEach(g => {
          const opt = document.createElement('option');
          opt.value = g.id;
          const locName = Common.getLocalizedGroupName(g, currentLang);
          const codePrefix = g.groupCode ? `${g.groupCode} - ` : '';
          opt.textContent = `${codePrefix}${locName}`;
          if (g.id === activeGroupId) {
            opt.selected = true;
          }
          select.appendChild(opt);
        });

        if (isScoped) {
          select.value = user.groupId;
          select.disabled = true; // Office Bearer cannot switch to unauthorized groups
          select.title = "Scoped to your assigned Bachat Gat";
        }

        // Bind change listener once
        if (!select.dataset.groupBound) {
          select.dataset.groupBound = 'true';
          select.addEventListener('change', (e) => {
            const newGroupId = e.target.value;
            localStorage.setItem('activeGroupId', newGroupId);
            // Sync any other group selects on page
            selects.forEach(s => { if (s !== e.target) s.value = newGroupId; });
            window.dispatchEvent(new CustomEvent('groupChanged', { detail: { groupId: newGroupId } }));
          });
        }
      });
    } catch (e) {
      console.warn('Failed to load group dropdown options:', e);
    }
  },

  /**
   * Initializes common topbar profile, sidebar navigation, and mobile menu
   */
  initShell() {
    const user = Auth.getUser();
    if (user) {
      const nameEl = document.getElementById('current-user-name');
      const roleEl = document.getElementById('current-user-role');
      const avatarEl = document.getElementById('current-user-avatar');

      const currentLang = window.i18n ? window.i18n.currentLang : (localStorage.getItem('app_lang') || 'en');
      let displayName = user.fullName || user.username;
      if (currentLang === 'mr' && user.fullNameMr) displayName = user.fullNameMr;
      if (currentLang === 'hi' && user.fullNameHi) displayName = user.fullNameHi;

      if (nameEl) nameEl.textContent = displayName;
      if (roleEl) roleEl.textContent = this.getRoleDisplayName(user, currentLang);
      if (avatarEl) {
        const initials = displayName.substring(0, 2).toUpperCase();
        avatarEl.textContent = initials;
      }

      // Role-specific brand badge in sidebar (Requirement 4)
      const brandBadge = document.querySelector('.brand-badge');
      if (brandBadge) {
        const key = (user.designation || user.role || '').toUpperCase();
        if (key.includes('SUPER_ADMIN')) {
          brandBadge.textContent = 'SUPER ADMIN';
        } else if (key.includes('PRESIDENT') || key === 'ADMIN' || key === 'GROUP_ADMIN') {
          brandBadge.textContent = 'PRESIDENT';
        } else if (key.includes('SECRETARY')) {
          brandBadge.textContent = 'SECRETARY';
        } else if (key.includes('TREASURER')) {
          brandBadge.textContent = 'TREASURER';
        } else if (key.includes('MEMBER') || key.includes('USER')) {
          brandBadge.textContent = 'MEMBER';
        } else {
          brandBadge.textContent = key;
        }
      }

      // Role-specific sidebar adjustments
      if (user.role === 'SUPER_ADMIN') {

        // Redirect away from daily operational dashboard to Super Admin dashboard
        const currentPath = window.location.pathname;
        if (currentPath.endsWith('/admin/dashboard.html') || currentPath.endsWith('/admin/')) {
          window.location.href = '/admin/super-dashboard.html';
          return;
        }

        // Render simplified Super Admin sidebar navigation
        const sidebarMenu = document.querySelector('.sidebar-menu');
        if (sidebarMenu) {
          sidebarMenu.innerHTML = `
            <li class="menu-category">ADMINISTRATION</li>
            <li><a href="/admin/super-dashboard.html" class="menu-item ${currentPath.includes('super-dashboard.html') ? 'active' : ''}"><i class="bi bi-grid-1x2-fill menu-icon"></i><span data-i18n="superDashboard">Super Dashboard</span></a></li>
            <li><a href="/admin/groups.html" class="menu-item ${currentPath.includes('groups.html') ? 'active' : ''}"><i class="bi bi-buildings-fill menu-icon"></i><span data-i18n="groups">Group Management</span></a></li>
            <li><a href="/admin/admins.html" class="menu-item ${currentPath.includes('admins.html') ? 'active' : ''}"><i class="bi bi-person-badge-fill menu-icon"></i><span data-i18n="clientAdmins">Client Admins</span></a></li>
            <li><a href="/admin/members.html" class="menu-item ${currentPath.includes('members.html') ? 'active' : ''}"><i class="bi bi-people-fill menu-icon"></i><span data-i18n="members">All Members</span></a></li>
            <li class="menu-category">SYSTEM</li>
            <li><a href="/admin/audit-logs.html" class="menu-item ${currentPath.includes('audit-logs.html') ? 'active' : ''}"><i class="bi bi-shield-lock-fill menu-icon"></i><span data-i18n="auditLogs">Audit Logs</span></a></li>
          `;
          if (window.i18n && window.i18n.applyTranslations) {
            window.i18n.applyTranslations();
          }
        }
      } else if (user.role === 'ADMIN') {
        // Hide Super-Admin exclusive links if any exist in DOM
        document.querySelectorAll('.super-admin-only').forEach(el => el.remove());
      }
    }

    // Sidebar toggle (Desktop collapse + Mobile drawer)
    const toggleBtn = document.getElementById('sidebar-toggle');
    const sidebar = document.querySelector('.app-sidebar');
    let backdrop = document.querySelector('.sidebar-backdrop');

    // Restore desktop preference
    if (window.innerWidth > 1024 && localStorage.getItem('sidebar-collapsed') === 'true') {
      document.body.classList.add('sidebar-collapsed');
    }

    if (toggleBtn && sidebar) {
      if (!backdrop) {
        backdrop = document.createElement('div');
        backdrop.className = 'sidebar-backdrop';
        document.body.appendChild(backdrop);
      }

      toggleBtn.addEventListener('click', () => {
        if (window.innerWidth > 1024) {
          document.body.classList.toggle('sidebar-collapsed');
          localStorage.setItem('sidebar-collapsed', document.body.classList.contains('sidebar-collapsed'));
        } else {
          sidebar.classList.toggle('show');
          backdrop.classList.toggle('show');
        }
      });

      backdrop.addEventListener('click', () => {
        sidebar.classList.remove('show');
        backdrop.classList.remove('show');
      });

      // Close the drawer after navigation on phones and tablets.
      sidebar.addEventListener('click', (event) => {
        if (window.innerWidth <= 1024 && event.target.closest('a')) {
          sidebar.classList.remove('show');
          backdrop.classList.remove('show');
        }
      });

      // Reset drawer state after device rotation or browser resizing.
      window.addEventListener('resize', () => {
        if (window.innerWidth > 1024) {
          sidebar.classList.remove('show');
          backdrop.classList.remove('show');
        }
      });
    }

    // Logout button bindings
    document.querySelectorAll('.btn-logout').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        Auth.logout();
      });
    });

    // Auto-initialize group dropdowns on admin pages
    this.initGroupSelector();

    // Universal modal close listeners (✕ buttons, backdrop click, Escape key)
    this.initModalListeners();

    // Page scripts often populate tables after shell initialization. The
    // observer below adds labels to those rows when they appear on mobile.
    this.initResponsiveTables();

    // Re-render dropdowns & user display when language changes
    window.addEventListener('languageChanged', () => {
      this.initGroupSelector();
      if (user) {
        const currentLang = window.i18n ? window.i18n.currentLang : 'en';
        let displayName = user.fullName || user.username;
        if (currentLang === 'mr' && user.fullNameMr) displayName = user.fullNameMr;
        if (currentLang === 'hi' && user.fullNameHi) displayName = user.fullNameHi;
        const nameEl = document.getElementById('current-user-name');
        if (nameEl) nameEl.textContent = displayName;
        const roleEl = document.getElementById('current-user-role');
        if (roleEl) roleEl.textContent = this.getRoleDisplayName(user, currentLang);
      }
    });
  },

  initResponsiveTables() {
    const markTables = () => {
      document.querySelectorAll('table.data-table, .content-card table, .card-box table, .printable-sheet table').forEach(table => {
        if (table.closest('.guide-content')) return;
        table.classList.add('mobile-card-table');
        const headers = Array.from(table.querySelectorAll('thead th')).map(th => th.textContent.trim());
        table.querySelectorAll('tbody tr').forEach(row => {
          Array.from(row.children).forEach((cell, index) => {
            if (cell.tagName === 'TD' && !cell.hasAttribute('colspan') && headers[index]) {
              cell.dataset.label = cell.dataset.label || headers[index];
            }
          });
        });
      });
    };
    markTables();
    const observer = new MutationObserver(markTables);
    observer.observe(document.body, { childList: true, subtree: true });
    window.addEventListener('beforeunload', () => observer.disconnect(), { once: true });
  }
};

window.Common = Common;
window.escapeHtml = Common.escapeHtml;

window.togglePasswordVisibility = function(inputId, btn) {
  const input = document.getElementById(inputId);
  if (!input) return;
  const icon = btn ? btn.querySelector('i') : null;
  if (input.type === 'password') {
    input.type = 'text';
    if (icon) {
      icon.className = 'bi bi-eye-slash';
    }
  } else {
    input.type = 'password';
    if (icon) {
      icon.className = 'bi bi-eye';
    }
  }
};

document.addEventListener('DOMContentLoaded', () => {
  Common.initShell();
});
