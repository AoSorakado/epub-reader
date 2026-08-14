import {
  createContext,
  useContext,
  useMemo,
  useRef,
  type ReactNode,
} from 'react'
import type { AnimationTimeScaleRef } from './animation'

type AnimationConfigContextValue = {
  timeScaleRef: AnimationTimeScaleRef
}

export type AnimationConfigProviderProps = {
  children?: ReactNode
  timeScale?: number
}

const defaultTimeScaleRef: AnimationTimeScaleRef = { current: 1 }
const AnimationConfigContext = createContext<AnimationConfigContextValue>({
  timeScaleRef: defaultTimeScaleRef,
})

function normalizeTimeScale(timeScale: number | undefined) {
  return typeof timeScale === 'number' && Number.isFinite(timeScale) && timeScale > 0
    ? timeScale
    : 1
}

export function AnimationConfigProvider({
  children,
  timeScale = 1,
}: AnimationConfigProviderProps) {
  const parent = useContext(AnimationConfigContext)
  const timeScaleRef = useRef(1)
  timeScaleRef.current = parent.timeScaleRef.current * normalizeTimeScale(timeScale)

  const value = useMemo(() => ({ timeScaleRef }), [])

  return (
    <AnimationConfigContext.Provider value={value}>
      {children}
    </AnimationConfigContext.Provider>
  )
}

export function useAnimationTimeScaleRef() {
  return useContext(AnimationConfigContext).timeScaleRef
}
