(function () {
  const CHANNEL_NAME = 'bachatgat-finance-updates';
  const STORAGE_KEY = 'bachatgat-finance-updated';
  const subscribers = new Set();
  let channel = null;

  function notify(payload) {
    subscribers.forEach((subscriber) => {
      try {
        subscriber(payload || {});
      } catch (error) {
        console.warn('Finance update subscriber failed:', error);
      }
    });
  }

  if ('BroadcastChannel' in window) {
    channel = new BroadcastChannel(CHANNEL_NAME);
    channel.addEventListener('message', (event) => notify(event.data));
  }

  window.FinanceSync = {
    publish(groupId) {
      const payload = { groupId: groupId || null, updatedAt: Date.now() };
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      } catch (error) {
        console.warn('Could not persist finance update signal:', error);
      }
      if (channel) channel.postMessage(payload);
      notify(payload);
    },

    subscribe(callback) {
      if (typeof callback !== 'function') return () => {};
      subscribers.add(callback);
      const onStorage = (event) => {
        if (event.key !== STORAGE_KEY || !event.newValue) return;
        try {
          callback(JSON.parse(event.newValue));
        } catch (error) {
          console.warn('Could not read finance update signal:', error);
        }
      };
      window.addEventListener('storage', onStorage);
      return () => {
        subscribers.delete(callback);
        window.removeEventListener('storage', onStorage);
      };
    },

    startPolling(refresh, intervalMs = 15000) {
      if (typeof refresh !== 'function') return () => {};
      let running = false;
      const run = async () => {
        if (running || document.hidden) return;
        running = true;
        try {
          await refresh();
        } catch (error) {
          console.warn('Finance refresh failed:', error);
        } finally {
          running = false;
        }
      };
      const timer = window.setInterval(run, intervalMs);
      const onVisible = () => {
        if (!document.hidden) run();
      };
      document.addEventListener('visibilitychange', onVisible);
      return () => {
        window.clearInterval(timer);
        document.removeEventListener('visibilitychange', onVisible);
      };
    }
  };
})();
