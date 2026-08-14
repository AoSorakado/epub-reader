import { useRef, useState } from 'react'
import { useDrag } from '@use-gesture/react'
import {
  Frame,
  Glass,
  GlassContainer,
  Html,
  LiquidCanvas,
  Transform,
  ZStack,
  spring,
  type GlassProps,
  type TransformRef,
} from '@liquid-dom/react'

const GLASS_WIDTH = 340
const GLASS_HEIGHT = 188
const DRAG_SCALE = 1.1
const MAX_SQUASH_SPEED = 1.6
const MAX_SQUASH_AMOUNT = 0.2
const SNAP_SPRING = spring({ stiffness: 420, damping: 25 })
const SCALE_SPRING = spring({ stiffness: 620, damping: 25 })

type GlassDragBind = Pick<GlassProps, 'onPointerDown' | 'onPointerMove' | 'onPointerUp' | 'onPointerCancel'>

export default function DraggableGlassDemo() {
  const transformRef = useRef<TransformRef | null>(null)
  const [{ x, y }, setPosition] = useState({ x: 0, y: 0 })
  const [{ scaleX, scaleY }, setScale] = useState({ scaleX: 1, scaleY: 1 })
  const [snappingToCenter, setSnappingToCenter] = useState(false)
  const liftGlass = () => {
    setSnappingToCenter(false)
    setScale({ scaleX: DRAG_SCALE, scaleY: DRAG_SCALE })
  }
  const releaseGlass = () => {
    setPosition({ x: 0, y: 0 })
    setScale({ scaleX: 1, scaleY: 1 })
    setSnappingToCenter(true)
  }
  const bind = useDrag(({
    active,
    direction: [directionX, directionY],
    first,
    last,
    offset: [nextX, nextY],
    velocity: [velocityX, velocityY],
  }) => {
    if (first) {
      liftGlass()
    }

    if (last || !active) {
      releaseGlass()
      return
    }

    setPosition({ x: nextX, y: nextY })

    const speed = Math.hypot(velocityX, velocityY)
    const amount = Math.min(speed / MAX_SQUASH_SPEED, 1) * MAX_SQUASH_AMOUNT
    if (amount <= 0) {
      setScale({ scaleX: DRAG_SCALE, scaleY: DRAG_SCALE })
      return
    }

    const dominantDirection = Math.abs(directionX) >= Math.abs(directionY)
      ? directionX
      : directionY
    if (dominantDirection < 0) {
      setScale({ scaleX: DRAG_SCALE * (1 - amount), scaleY: DRAG_SCALE * (1 + amount) })
    } else {
      setScale({ scaleX: DRAG_SCALE * (1 + amount), scaleY: DRAG_SCALE * (1 - amount) })
    }
  }, {
    from: () => [
      transformRef.current?.x ?? x,
      transformRef.current?.y ?? y,
    ],
    axis: 'x',
    preventDefault: true,
    pointer: {
      capture: false,
      keys: false,
    },
  }) as () => GlassDragBind
  const dragBind = bind()
  const handlePointerDown: GlassProps['onPointerDown'] = (event) => {
    liftGlass()
    dragBind.onPointerDown?.(event)
  }
  const handlePointerUp: GlassProps['onPointerUp'] = (event) => {
    releaseGlass()
    dragBind.onPointerUp?.(event)
  }
  const handlePointerCancel: GlassProps['onPointerCancel'] = (event) => {
    releaseGlass()
    dragBind.onPointerCancel?.(event)
  }

  return (
    <section className="draggable-glass-demo">
      <LiquidCanvas className="canvas-shell draggable-glass-canvas-shell" canvasClassName="demo-canvas draggable-glass-canvas">
        <ZStack alignment="center">
          <Html zIndex={-1} sizing="fill">
            <div className="draggable-glass-word">Jiggle</div>
          </Html>
          <Frame maxWidth={Infinity} maxHeight={Infinity}>
            <GlassContainer
              blur={1}
              bezelWidth={50}
              thickness={30}
              tint={{ r: 1, g: 1, b: 1, a: 0.07 }}
              shadowColor={{ r: 0, g: 0, b: 0, a: 0.2 }}
              shadowOffsetX={0}
              shadowOffsetY={0}
              specularOpacity={0.8}
              dispersion={0.05}
            >
              <Transform
                ref={transformRef}
                x={x}
                y={y}
                scaleX={scaleX}
                scaleY={scaleY}
                origin={{ x: 0.5, y: 0.5 }}
                transition={{
                  x: snappingToCenter ? SNAP_SPRING : false,
                  y: snappingToCenter ? SNAP_SPRING : false,
                  scaleX: SCALE_SPRING,
                  scaleY: SCALE_SPRING,
                }}
              >
                <Glass
                  cornerRadius={999}
                  pointerEvents
                  {...dragBind}
                  onPointerDown={handlePointerDown}
                  onPointerUp={handlePointerUp}
                  onPointerCancel={handlePointerCancel}
                >
                  <Frame width={GLASS_WIDTH} height={GLASS_HEIGHT} />
                </Glass>
              </Transform>
            </GlassContainer>
          </Frame>
        </ZStack>
      </LiquidCanvas>
    </section>
  )
}
