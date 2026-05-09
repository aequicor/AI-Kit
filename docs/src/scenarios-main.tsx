import React from 'react';
import ReactDOM from 'react-dom/client';
import Header from './components/Header';
import Footer from './components/Footer';
import Scenarios from './pages/Scenarios';
import { ThemeProvider } from './theme';
import './i18n';
import './styles/global.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <Header />
      <main>
        <Scenarios />
      </main>
      <Footer />
    </ThemeProvider>
  </React.StrictMode>,
);
