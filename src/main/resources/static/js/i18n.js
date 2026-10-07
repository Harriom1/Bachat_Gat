/**
 * Internationalization (i18n) Engine for Multilingual Support
 * Supports English (en), Marathi (mr), and Hindi (hi)
 * Translates table headers, cards, badges, buttons, form labels, and placeholders.
 */

class I18nManager {
  constructor() {
    this.currentLang = localStorage.getItem('app_lang') || 'en';
    this.translations = {};
    this.englishTranslations = {};
    this.englishTextToKey = new Map();
    this.observer = null;
    this.translationPassQueued = false;
  }

  async init() {
    await this.loadEnglishDictionary();
    await this.setLanguage(this.currentLang);
    this.bindLanguageSelectors();
    this.observeDynamicContent();
  }

  async loadEnglishDictionary() {
    try {
      const response = await fetch('/i18n/en.json?v=' + Date.now());
      if (!response.ok) return;
      this.englishTranslations = await response.json();
      Object.entries(this.englishTranslations).forEach(([key, value]) => {
        if (typeof value === 'string' && value.trim()) {
          // Keep the first key when two concepts intentionally share the same
          // English label, so the reverse lookup remains deterministic.
          if (!this.englishTextToKey.has(value.trim())) this.englishTextToKey.set(value.trim(), key);
        }
      });
    } catch (e) {
      console.warn('Could not load the English translation dictionary:', e);
    }
  }

  async setLanguage(lang) {
    this.currentLang = lang;
    localStorage.setItem('app_lang', lang);
    try {
      const response = await fetch(`/i18n/${lang}.json?v=` + Date.now());
      if (response.ok) {
        this.translations = await response.json();
        this.applyTranslations();
        document.documentElement.lang = lang;
        window.dispatchEvent(new CustomEvent('languageChanged', { detail: { lang, translations: this.translations } }));
      }
    } catch (e) {
      console.warn(`Could not load translations for ${lang}:`, e);
    }
  }

  t(key, fallback = '') {
    if (!key) return fallback;
    return this.translations[key] || fallback || key;
  }

  translateStatus(status) {
    if (!status) return '';
    const s = String(status).toUpperCase();
    const badgeKey = 'badge' + s.charAt(0) + s.slice(1).toLowerCase();
    return this.t(badgeKey, this.t(s.toLowerCase(), status));
  }

  translateEnum(value, fallback = '') {
    if (value === null || value === undefined || value === '') return fallback || '';
    const normalized = String(value).trim().toUpperCase().replace(/[^A-Z0-9]+/g, '_');
    const words = normalized.toLowerCase().split('_').filter(Boolean);
    if (!words.length) return fallback || String(value);
    const camel = words[0] + words.slice(1).map(word => word.charAt(0).toUpperCase() + word.slice(1)).join('');
    return this.t(`enum${camel.charAt(0).toUpperCase()}${camel.slice(1)}`, fallback || String(value));
  }

  applyTranslations() {
    // Translate text elements (preserving any leading icons)
    document.querySelectorAll('[data-i18n]').forEach(el => {
      const key = el.getAttribute('data-i18n');
      const translation = this.translations[key];
      if (translation) {
        const icon = el.querySelector('i');
        if (icon) {
          const iconHtml = icon.outerHTML;
          const nextHtml = `${iconHtml} ${translation}`;
          if (el.innerHTML !== nextHtml) el.innerHTML = nextHtml;
        } else if (el.textContent !== translation) {
          el.textContent = translation;
        }
      }
    });

    // Translate placeholder attributes
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      const key = el.getAttribute('data-i18n-placeholder');
      if (this.translations[key]) {
        if (el.getAttribute('placeholder') !== this.translations[key]) {
          el.setAttribute('placeholder', this.translations[key]);
        }
      }
    });

    // Translate title attributes
    document.querySelectorAll('[data-i18n-title]').forEach(el => {
      const key = el.getAttribute('data-i18n-title');
      if (this.translations[key]) {
        if (el.getAttribute('title') !== this.translations[key]) {
          el.setAttribute('title', this.translations[key]);
        }
      }
    });

    // Existing pages contain some older hard-coded labels and JavaScript
    // templates. Translate exact dictionary phrases globally so those pages
    // participate in the same language switch without duplicated HTML pages.
    this.translateDictionaryTextNodes();

    // Sync all language selector dropdowns
    document.querySelectorAll('.lang-selector').forEach(sel => {
      sel.value = this.currentLang;
    });
  }

  translateDictionaryTextNodes(root = document.body) {
    if (!root || !this.englishTextToKey.size) return;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    let node;
    while ((node = walker.nextNode())) nodes.push(node);

    nodes.forEach(textNode => {
      const parent = textNode.parentElement;
      if (!parent || ['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEXTAREA'].includes(parent.tagName)) return;
      if (parent.closest('[data-i18n]')) return;
      const original = textNode.nodeValue || '';
      const trimmed = original.trim();
      const key = this.englishTextToKey.get(trimmed);
      if (!key || !this.translations[key]) return;
      const leading = original.slice(0, original.indexOf(trimmed));
      const trailing = original.slice(original.indexOf(trimmed) + trimmed.length);
      textNode.nodeValue = leading + this.translations[key] + trailing;
    });
  }

  observeDynamicContent() {
    if (!document.body || this.observer) return;
    this.observer = new MutationObserver(() => {
      if (this.translationPassQueued) return;
      this.translationPassQueued = true;
      requestAnimationFrame(() => {
        this.translationPassQueued = false;
        this.applyTranslations();
      });
    });
    this.observer.observe(document.body, { childList: true, subtree: true });
  }

  bindLanguageSelectors() {
    document.querySelectorAll('.lang-selector').forEach(sel => {
      sel.value = this.currentLang;
      // Remove previous listener to avoid duplicate trigger
      sel.onchange = (e) => {
        this.setLanguage(e.target.value);
      };
    });
  }
}

window.i18n = new I18nManager();
document.addEventListener('DOMContentLoaded', () => {
  window.i18n.init();
});
