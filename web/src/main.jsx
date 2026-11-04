import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import './index.css';

const ACTIVE_CACHE_NAME = 'ejournal-skaduta-v7';

if ('caches' in window) {
  caches.keys().then((keys) => {
    keys.forEach((key) => {
      if (key !== ACTIVE_CACHE_NAME) {
        caches.delete(key);
      }
    });
  }).catch(() => {});
}

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js', { updateViaCache: 'none' })
      .then((reg) => {
        reg.update().catch(() => {});
      })
      .catch(() => {});
  });
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
