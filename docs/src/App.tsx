import { Route, Routes, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Header from './components/Header';
import Footer from './components/Footer';
import Home from './pages/Home';
import QuickStart from './pages/QuickStart';
import CLI from './pages/CLI';
import Files from './pages/Files';
import Build from './pages/Build';
import FAQ from './pages/FAQ';

function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, [pathname]);
  return null;
}

export default function App() {
  return (
    <>
      <Header />
      <ScrollToTop />
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/quickstart" element={<QuickStart />} />
          <Route path="/cli" element={<CLI />} />
          <Route path="/files" element={<Files />} />
          <Route path="/build" element={<Build />} />
          <Route path="/faq" element={<FAQ />} />
          <Route path="*" element={<Home />} />
        </Routes>
      </main>
      <Footer />
    </>
  );
}
