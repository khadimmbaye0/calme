import { request } from './client'

export type GraphNodeType = 'PROJECT' | 'TASK' | 'EVENT' | 'ACTION' | 'ENERGY' | 'BLOCK' | 'MESSAGE'

export type GraphEdgeType =
  | 'CONTAINS'
  | 'DEPENDS_ON'
  | 'SCHEDULED_FOR'
  | 'ACTED_ON'
  | 'DEFERRED'
  | 'COVERS'
  | 'FOLLOWS'

export interface GraphNode {
  id: string
  type: GraphNodeType
  label: string
  subtitle: string
  status: string
  weight: number
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  type: GraphEdgeType
  label: string
}

export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export function fetchGraph(): Promise<GraphData> {
  return request<GraphData>('/api/graph')
}
