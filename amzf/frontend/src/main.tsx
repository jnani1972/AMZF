import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles/theme.css';

// Get root element
const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('Failed to find root element. Make sure index.html has a <div id="root"></div>');
}

// Create root and render app
ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
