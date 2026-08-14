import { Leaf } from '@liquid-dom/layout'
import type { Size } from '@liquid-dom/layout'

export class MeasuredLeaf extends Leaf {
  private readonly measureFn: () => Size

  constructor(measure: () => Size, options: { measureKey?: unknown } = {}) {
    super(options)
    this.measureFn = measure
  }

  protected override measureLeaf(): Size {
    return this.measureFn()
  }
}
