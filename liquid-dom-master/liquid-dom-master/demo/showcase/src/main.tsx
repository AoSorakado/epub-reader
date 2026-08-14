import { StrictMode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import App from './App'
import './style.css'

declare global {
  interface Window {
    __liquidShowcaseRoot?: Root
  }
}

const rootElement = document.getElementById('root')!
const root = window.__liquidShowcaseRoot ?? createRoot(rootElement)
window.__liquidShowcaseRoot = root

root.render(
  <StrictMode>
    <App />
  </StrictMode>,
)
