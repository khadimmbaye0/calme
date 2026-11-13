import { describe, expect, it } from 'vitest'
import { layoutGraph, GRAPH_HEIGHT, GRAPH_WIDTH } from './graphLayout'
import type { GraphEdge, GraphNode } from '../api/graph'

const NODES: GraphNode[] = [
  { id: 'project:1', type: 'PROJECT', label: 'Mémoire', subtitle: '', status: 'ACTIVE', weight: 3.4 },
  { id: 'task:1', type: 'TASK', label: 'Cadrage', subtitle: '', status: 'TODO', weight: 2 },
  { id: 'task:2', type: 'TASK', label: 'Rédaction', subtitle: '', status: 'TODO', weight: 2 },
  { id: 'event:1', type: 'EVENT', label: 'Cadrage', subtitle: '', status: 'PLANNED', weight: 2 },
]

const EDGES: GraphEdge[] = [
  { id: 'e1', source: 'project:1', target: 'task:1', type: 'CONTAINS', label: 'contient' },
  { id: 'e2', source: 'project:1', target: 'task:2', type: 'CONTAINS', label: 'contient' },
  { id: 'e3', source: 'task:2', target: 'task:1', type: 'DEPENDS_ON', label: 'dépend de' },
  { id: 'e4', source: 'event:1', target: 'task:1', type: 'SCHEDULED_FOR', label: 'planifie' },
]

describe('layoutGraph', () => {
  it('place chaque nœud dans les limites et de façon déterministe', () => {
    const first = layoutGraph(NODES, EDGES)
    const second = layoutGraph(NODES, EDGES)

    expect(first).toHaveLength(NODES.length)
    for (const node of first) {
      expect(node.x).toBeGreaterThanOrEqual(50)
      expect(node.x).toBeLessThanOrEqual(GRAPH_WIDTH - 50)
      expect(node.y).toBeGreaterThanOrEqual(50)
      expect(node.y).toBeLessThanOrEqual(GRAPH_HEIGHT - 50)
    }
    expect(first.map(({ x, y }) => [x, y])).toEqual(second.map(({ x, y }) => [x, y]))
  })

  it('ignore les arêtes dont un nœud est filtré', () => {
    const layout = layoutGraph(NODES.slice(0, 2), EDGES)
    expect(layout).toHaveLength(2)
  })

  it('garde un espace minimal entre deux nœuds', () => {
    const layout = layoutGraph(NODES, EDGES)
    for (let left = 0; left < layout.length; left += 1) {
      for (let right = left + 1; right < layout.length; right += 1) {
        const a = layout[left]
        const b = layout[right]
        const distance = Math.hypot(a.x - b.x, a.y - b.y)
        expect(distance).toBeGreaterThan(a.r + b.r)
      }
    }
  })
})
