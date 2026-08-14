import { useState } from 'react'
import type { JSX } from 'react'
import { AnimatedExamplesTab } from './tabs/AnimatedExamplesTab'
import { DomLeavesTab } from './tabs/DomLeavesTab'
import { LayoutCasesTab } from './tabs/LayoutCasesTab'
import { PerfProfileTab } from './tabs/PerfProfileTab'
import { StressTestTab } from './tabs/StressTestTab'

type PlaygroundTab = 'cases' | 'animations' | 'stress' | 'dom' | 'perf'

type TabDefinition = {
  id: PlaygroundTab
  label: string
  Component: () => JSX.Element
}

const tabs: TabDefinition[] = [
  { id: 'cases', label: 'Layout Cases', Component: LayoutCasesTab },
  { id: 'animations', label: 'Animations', Component: AnimatedExamplesTab },
  { id: 'stress', label: 'Stress Test', Component: StressTestTab },
  { id: 'dom', label: 'DOM Leaves', Component: DomLeavesTab },
  { id: 'perf', label: 'Perf Profile', Component: PerfProfileTab },
]

export function App() {
  const [activeTab, setActiveTab] = useState<PlaygroundTab>('cases')
  const active = tabs.find((tab) => tab.id === activeTab) ?? tabs[0]
  const ActiveTab = active.Component

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">@liquid-dom/layout</p>
          <h1>Generic layout engine playground</h1>
        </div>
        <nav className="tabs" aria-label="Playground views">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              type="button"
              data-tab={tab.id}
              className={tab.id === activeTab ? 'active' : undefined}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </header>
      <section className="view" id="playground-view">
        <ActiveTab />
      </section>
    </main>
  )
}
