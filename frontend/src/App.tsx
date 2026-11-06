import { Route, Routes } from 'react-router-dom'
import BenchmarksPage from './pages/BenchmarksPage.tsx'
import HomePage from './pages/HomePage.tsx'
import MethodPage from './pages/MethodPage.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'
import PrivacyPage from './pages/PrivacyPage.tsx'
import RunPage from './pages/RunPage.tsx'
import ScreenPage from './pages/ScreenPage.tsx'
import TermsPage from './pages/TermsPage.tsx'

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/screen" element={<ScreenPage />} />
      <Route path="/runs/:id" element={<RunPage />} />
      <Route path="/benchmarks" element={<BenchmarksPage />} />
      <Route path="/method" element={<MethodPage />} />
      <Route path="/privacy" element={<PrivacyPage />} />
      <Route path="/terms" element={<TermsPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
