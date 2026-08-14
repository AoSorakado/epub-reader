import { startTransition, useEffect, useRef, useState } from 'react'
import { Container, Glass, Html, Renderer, Scene } from '@liquid-dom/core'

export default function HtmlLayersDemo() {
  const stageRef = useRef<HTMLDivElement | null>(null)
  const [buttonClicks, setButtonClicks] = useState(0)

  useEffect(() => {
    const mount = stageRef.current
    if (!mount) {
      return
    }

    const scene = new Scene()

    const backgroundElement = document.createElement('div')
    backgroundElement.className = 'tracker-backdrop'
    backgroundElement.innerHTML = `
      <div class="tracker-grid"></div>
      <div class="tracker-glow tracker-glow-a"></div>
      <div class="tracker-glow tracker-glow-b"></div>
    `
    const background = new Html({
      zIndex: -2,
      element: backgroundElement,
    })
    scene.add(background)

    const lowerCardElement = document.createElement('div')
    lowerCardElement.className = 'backdrop-card'
    lowerCardElement.innerHTML = `
      <span class="eyebrow">scene html</span>
      <h1>Independent HTML layers</h1>
      <p>This card is a scene-level Html node below the glass container.</p>
    `
    scene.add(new Html({
      x: 34,
      y: 34,
      width: 340,
      height: 300,
      zIndex: -1,
      element: lowerCardElement,
    }))

    const container = new Container({
      x: 118,
      y: 180,
      blur: 8,
      spacing: 28,
      bezelWidth: 18,
      thickness: 92,
      contentDepth: 22,
      tint: { r: 0.12, g: 0.16, b: 0.19, a: 0.62 },
      zIndex: 0,
    })

    const primaryGlass = new Glass({
      x: 0,
      y: 0,
      width: 330,
      height: 210,
      cornerRadius: 58,
      zIndex: 0,
    })

    const secondaryGlass = new Glass({
      x: 244,
      y: 78,
      width: 230,
      height: 168,
      cornerRadius: 48,
      zIndex: 1,
    })

    const labelElement = document.createElement('div')
    labelElement.className = 'tracker-badge'
    labelElement.innerHTML = `
      <span>glass child html</span>
      <strong>Layered content</strong>
      <p>This text is one Html child of the left glass.</p>
    `
    primaryGlass.add(new Html({
      x: 22,
      y: 22,
      width: 230,
      height: 146,
      zIndex: 0,
      element: labelElement,
    }))

    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'glass-button'
    button.textContent = 'Native button'
    button.addEventListener('click', () => {
      startTransition(() => setButtonClicks((value) => value + 1))
    })
    primaryGlass.add(new Html({
      x: 54,
      y: 132,
      width: 220,
      height: 58,
      zIndex: 1,
      element: button,
    }))

    const chipElement = document.createElement('div')
    chipElement.className = 'drag-me-label'
    chipElement.textContent = 'second html node'
    secondaryGlass.add(new Html({
      x: 22,
      y: 34,
      width: 174,
      height: 82,
      zIndex: 0,
      element: chipElement,
    }))

    container.add(primaryGlass)
    container.add(secondaryGlass)
    scene.add(container)

    const upperElement = document.createElement('div')
    upperElement.className = 'backdrop-card html-layer-floating'
    upperElement.innerHTML = `
      <span class="eyebrow">above glass</span>
      <p>This scene Html node has a higher z-index than the glass container.</p>
    `
    scene.add(new Html({
      x: 440,
      y: 48,
      width: 240,
      height: 142,
      zIndex: 1,
      element: upperElement,
    }))

    const renderer = new Renderer({ scene })
    renderer.canvas.className = 'demo-canvas'
    mount.append(renderer.canvas)

    const syncBackgroundSize = () => {
      const bounds = mount.getBoundingClientRect()
      background.width = bounds.width
      background.height = bounds.height
    }
    const resizeObserver = new ResizeObserver(syncBackgroundSize)
    resizeObserver.observe(mount)
    syncBackgroundSize()

    let frameId = 0
    const frame = () => {
      renderer.render()
      frameId = requestAnimationFrame(frame)
    }
    frame()

    return () => {
      cancelAnimationFrame(frameId)
      resizeObserver.disconnect()
      renderer.destroy()
    }
  }, [])

  return (
    <section className="demo-layout">
      <div ref={stageRef} className="canvas-shell" />

      <aside className="inspector">
        <div className="panel">
          <h2>Native DOM</h2>
          <dl className="metric-grid">
            <div>
              <dt>button clicks</dt>
              <dd>{buttonClicks}</dd>
            </div>
            <div>
              <dt>content model</dt>
              <dd>Html nodes</dd>
            </div>
          </dl>
        </div>

        <div className="panel">
          <h2>Checks</h2>
          <ol className="check-list">
            <li>Background and cards are scene-level Html nodes.</li>
            <li>The button and labels are independent Html children of glass nodes.</li>
            <li>The upper card covers the glass because its scene z-index is higher.</li>
          </ol>
        </div>
      </aside>
    </section>
  )
}
