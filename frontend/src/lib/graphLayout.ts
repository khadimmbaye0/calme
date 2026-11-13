import type { GraphEdge, GraphNode, GraphNodeType } from '../api/graph'

export const GRAPH_WIDTH = 1000
export const GRAPH_HEIGHT = 700

const MARGIN = 52

const CLUSTER: Record<GraphNodeType, [number, number]> = {
  PROJECT: [0.34, 0.43],
  TASK: [0.52, 0.55],
  EVENT: [0.77, 0.34],
  ACTION: [0.46, 0.82],
  ENERGY: [0.87, 0.7],
  BLOCK: [0.13, 0.7],
  MESSAGE: [0.15, 0.2],
}

const RADIUS: Record<GraphNodeType, number> = {
  PROJECT: 21,
  TASK: 7,
  EVENT: 9,
  ACTION: 4.5,
  ENERGY: 5,
  BLOCK: 13,
  MESSAGE: 4.5,
}

const SPACING: Record<GraphNodeType, number> = {
  PROJECT: 34,
  TASK: 40,
  EVENT: 40,
  ACTION: 34,
  ENERGY: 28,
  BLOCK: 34,
  MESSAGE: 26,
}

const GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5))

export interface GraphLayoutNode extends GraphNode {
  x: number
  y: number
  r: number
}

function hash(value: string): number {
  let result = 2166136261
  for (let index = 0; index < value.length; index += 1) {
    result ^= value.charCodeAt(index)
    result = Math.imul(result, 16777619)
  }
  return result >>> 0
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function radiusFor(node: GraphNode): number {
  const base = RADIUS[node.type]
  return base + Math.min(4, Math.max(0, node.weight - 1) * 1.2)
}

/**
 * Deterministic constellation layout.
 *
 * Each family gets its own region and a phyllotaxis spiral. That keeps a sparse
 * graph composed, a dense graph readable, and — unlike a free force layout —
 * never lets a narrow viewport collapse every node onto one vertical line.
 * A short collision pass resolves the few overlaps at the cluster edges.
 */
export function layoutGraph(
  nodes: GraphNode[],
  _edges: GraphEdge[],
  width = GRAPH_WIDTH,
  height = GRAPH_HEIGHT,
): GraphLayoutNode[] {
  if (nodes.length === 0) {
    return []
  }

  const densityScale = Math.min(1, Math.min(width, height) / 720)
  const groups = new Map<GraphNodeType, GraphNode[]>()
  for (const node of nodes) {
    const group = groups.get(node.type) ?? []
    group.push(node)
    groups.set(node.type, group)
  }

  const positioned: GraphLayoutNode[] = []
  for (const [type, group] of groups) {
    const [cx, cy] = CLUSTER[type]
    const centerX = cx * width
    const centerY = cy * height
    const sorted = [...group].sort((left, right) => left.label.localeCompare(right.label, 'fr'))
    const spacing = SPACING[type] * densityScale

    sorted.forEach((node, index) => {
      const angle = hash(`${type}:${node.id}`) % 360 * (Math.PI / 180) + index * GOLDEN_ANGLE
      const radius = spacing * Math.sqrt(index + 0.5)
      positioned.push({
        ...node,
        x: clamp(centerX + Math.cos(angle) * radius, MARGIN, width - MARGIN),
        y: clamp(centerY + Math.sin(angle) * radius * 0.9, MARGIN, height - MARGIN),
        r: radiusFor(node),
      })
    })
  }

  // A short collision relaxation handles the rare overlap between two
  // neighbouring families. It is deterministic and never re-runs a simulation.
  for (let pass = 0; pass < 24; pass += 1) {
    for (let left = 0; left < positioned.length; left += 1) {
      const a = positioned[left]
      for (let right = left + 1; right < positioned.length; right += 1) {
        const b = positioned[right]
        let dx = b.x - a.x
        let dy = b.y - a.y
        let distance = Math.sqrt(dx * dx + dy * dy)
        if (distance < 1) {
          dx = 0.5
          dy = 0.5
          distance = Math.SQRT1_2
        }
        const minimum = a.r + b.r + 12
        if (distance < minimum) {
          const push = (minimum - distance) / (2 * distance)
          a.x -= dx * push
          a.y -= dy * push
          b.x += dx * push
          b.y += dy * push
        }
      }
    }
    for (const node of positioned) {
      node.x = clamp(node.x, MARGIN, width - MARGIN)
      node.y = clamp(node.y, MARGIN, height - MARGIN)
    }
  }

  return positioned
}
