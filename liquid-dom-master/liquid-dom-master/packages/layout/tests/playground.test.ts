import { describe, expect, it, vi } from 'vitest'

describe('playground', () => {
  it('renders all playground tabs and records perf counters', async () => {
    document.body.innerHTML = '<div id="app"></div>'
    Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
      configurable: true,
      get: () => 720,
    })
    globalThis.requestAnimationFrame = ((callback: FrameRequestCallback) => {
      setTimeout(() => callback(performance.now()), 0)
      return 1
    }) as typeof requestAnimationFrame
    globalThis.cancelAnimationFrame = vi.fn() as typeof cancelAnimationFrame

    await import('../playground/src/main.tsx')
    await waitForSelector('.case-card')

    expect(document.querySelectorAll('.case-card').length).toBeGreaterThan(0)

    clickTab('Animations')
    await waitForSelector('.animation-card')
    expect(document.querySelectorAll('.animation-card').length).toBeGreaterThan(0)
    expect(document.querySelector('.fps-meter')?.textContent).toContain('FPS')

    clickTab('Stress Test')
    await waitForSelector('.stress-stage')
    await waitForRender()
    expect(document.querySelector('.stress-stage')).toBeTruthy()
    expect(document.querySelector('.stress-output')?.textContent).toContain('Elements')

    clickTab('DOM Leaves')
    await waitForSelector('.dom-stage')
    expect(document.querySelector('.dom-stage')).toBeTruthy()

    clickTab('Perf Profile')
    await waitForSelector('.perf-output')
    expect(document.querySelector('.perf-output')?.textContent).toContain('Measure calls')
    expect(document.querySelector('.perf-output')?.textContent).toMatch(/Cache hit ratio/)

    vi.restoreAllMocks()
  })
})

function clickTab(label: string) {
  const button = [...document.querySelectorAll<HTMLButtonElement>('[data-tab]')].find(
    (candidate) => candidate.textContent === label,
  )
  if (!button) throw new Error(`Missing tab: ${label}`)
  button.click()
}

function waitForRender() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function waitForSelector(selector: string) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    if (document.querySelector(selector)) return
    await waitForRender()
  }
  throw new Error(`Timed out waiting for selector: ${selector}`)
}
