/**
 * Main App Component
 * Wraps the application with ThemeProvider, Router, and AuthProvider
 */

import { RouterProvider } from 'react-router-dom';
import { ThemeProvider } from './lib/theme';
import { router } from './routes';

/**
 * App component
 */
function App() {
  return (
    <ThemeProvider>
      <RouterProvider router={router} />
    </ThemeProvider>
  );
}

export default App;
