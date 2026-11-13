import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent as ReactPointerEvent,
} from 'react'
import { errorMessage, UnauthorizedError } from '../api/client'
import {
  fetchGraph,
  type GraphData,
  type GraphEdge,
  type GraphNode,
  type GraphNodeType,
} from '../api/graph'
import { GRAPH_HEIGHT, GRAPH_WIDTH, layoutGraph, type GraphLayoutNode } from '../lib/graphLayout'

interface KnowledgeGraphPanelProps {
  onUnauthorized: () => void
}

type LoadStatus = 'loading' | 'ready' | 'error'

const TYPE_ORDER: GraphNodeType[] = [
  'PROJECT',
  'TASK',
  'EVENT',
  'ENERGY',
  'BLOCK',
  'ACTION',
  'MESSAGE',
]

const TYPE_LABEL: Record<GraphNodeType, string> = {
  PROJECT: 'Projet',
  TASK: 'Tâche',
  EVENT: 'Moment',
  ENERGY: 'Énergie',
  BLOCK: 'Bloc',
  ACTION: 'Journal',
  MESSAGE: 'Message',
}

const TYPE_PLURAL: Record<GraphNodeType, string> = {
  PROJECT: 'Projets',
  TASK: 'Tâches',
  EVENT: 'Moments',
  ENERGY: 'Énergie',
  BLOCK: 'Blocs',
  ACTION: 'Journal',
  MESSAGE: 'Messages',
}

const TYPE_COLOR: Record<GraphNodeType, string> = {
  PROJECT: '#C4704B',
  TASK: '#2B2620',
  EVENT: '#8AA07C',
  ENERGY: '#8AA07C',
  BLOCK: '#6F6559',
  ACTION: '#C4704B',
  MESSAGE: '#A79B8D',
}

const COLOR = {
  ink: '#2B2620',
  accent: '#C4704B',
  sage: '#8AA07C',
  paper: '#FFFDF8',
  line: '#E7DFD2',
  muted: '#8A7F72',
}

const EDGE_STYLE: Record<string, { color: string; opacity: number; dash?: string }> = {
  CONTAINS: { color: COLOR.ink, opacity: 0.16 },
  DEPENDS_ON: { color: COLOR.accent, opacity: 0.55, dash: '4 5' },
  SCHEDULED_FOR: { color: COLOR.sage, opacity: 0.4 },
  ACTED_ON: { color: COLOR.accent, opacity: 0.16, dash: '2 5' },
  DEFERRED: { color: COLOR.accent, opacity: 0.4, dash: '3 4' },
  COVERS: { color: COLOR.ink, opacity: 0.07, dash: '1 8' },
  FOLLOWS: { color: COLOR.ink, opacity: 0.18 },
}

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'En cours',
  TODO: 'À faire',
  DOING: 'En cours',
  DONE: 'Terminé',
  DEFERRED: 'Reporté',
  FIXED: 'Fixe',
  PLANNED: 'Planifié',
  AVAILABILITY: 'Disponibilité',
  LOW: 'Basse',
  MID: 'Moyenne',
  GOOD: 'Bonne',
  APPLIED: 'Appliquée',
  UNDONE: 'Annulée',
  FAILED: 'Échouée',
  SCHOOL: 'École',
  COMPANY: 'Entreprise',
  USER: 'Vous',
  AGENT: 'Agent',
}

interface Interaction {
  mode: 'pan' | 'node'
  id?: string
  startX: number
  startY: number
  originX: number
  originY: number
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function shortLabel(value: string, max: number): string {
  return value.length <= max ? value : `${value.slice(0, max - 1).trim()}…`
}

function LegendMark({ type }: { type: GraphNodeType }) {
  const color = TYPE_COLOR[type]
  if (type === 'TASK') {
    return (
      <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
        <circle cx="7" cy="7" r="3" fill={color} />
      </svg>
    )
  }
  if (type === 'EVENT') {
    return (
      <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
        <rect x="4" y="4" width="6" height="6" rx="1" fill={color} transform="rotate(45 7 7)" />
      </svg>
    )
  }
  if (type === 'ENERGY') {
    return (
      <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
        <circle cx="7" cy="7" r="3" fill={color} />
      </svg>
    )
  }
  if (type === 'BLOCK') {
    return (
      <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
        <circle cx="7" cy="7" r="5" fill="none" stroke={color} strokeWidth="1" strokeDasharray="2 2" />
      </svg>
    )
  }
  if (type === 'ACTION') {
    return (
      <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
        <circle cx="7" cy="7" r="3" fill="none" stroke={color} strokeWidth="1" />
      </svg>
    )
  }
  if (type === 'MESSAGE') {
    return (
      <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
        <rect x="5" y="5" width="4" height="4" rx="1" fill={color} />
      </svg>
    )
  }
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
      <circle cx="7" cy="7" r="4.5" fill="none" stroke={color} strokeWidth="1.2" />
      <circle cx="7" cy="7" r="1.5" fill={color} />
    </svg>
  )
}

function matchesQuery(node: GraphNode, query: string): boolean {
  if (!query) {
    return false
  }
  return `${node.label} ${node.subtitle} ${node.type}`.toLowerCase().includes(query)
}

export default function KnowledgeGraphPanel({ onUnauthorized }: KnowledgeGraphPanelProps) {
  const [data, setData] = useState<GraphData | null>(null)
  const [status, setStatus] = useState<LoadStatus>('loading')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [activeTypes, setActiveTypes] = useState<Set<GraphNodeType>>(() => new Set(TYPE_ORDER))
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [hoveredId, setHoveredId] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [pan, setPan] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [dragged, setDragged] = useState<Record<string, { x: number; y: number }>>({})
  const [size, setSize] = useState({ width: GRAPH_WIDTH, height: GRAPH_HEIGHT })

  const svgRef = useRef<SVGSVGElement | null>(null)
  const graphAreaRef = useRef<HTMLDivElement | null>(null)
  const interaction = useRef<Interaction | null>(null)
  const view = useRef({ pan, zoom })
  view.current = { pan, zoom }

  const load = useCallback(async () => {
    setStatus('loading')
    setLoadError(null)
    try {
      setData(await fetchGraph())
      setStatus('ready')
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setLoadError(errorMessage(error))
      setStatus('error')
    }
  }, [onUnauthorized])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const element = graphAreaRef.current
    if (!element) {
      return
    }
    const updateSize = () => {
      const rect = element.getBoundingClientRect()
      if (rect.width > 0 && rect.height > 0) {
        setSize({ width: Math.round(rect.width), height: Math.round(rect.height) })
      }
    }
    updateSize()
    if (typeof ResizeObserver === 'undefined') {
      return
    }
    const observer = new ResizeObserver(updateSize)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    setDragged({})
    setPan({ x: 0, y: 0 })
  }, [size.width, size.height])

  const nodes = data?.nodes ?? []
  const edges = data?.edges ?? []
  const nodeById = useMemo(() => new Map(nodes.map((node) => [node.id, node])), [nodes])
  const typeCounts = useMemo(() => {
    const counts = new Map<GraphNodeType, number>()
    for (const node of nodes) {
      counts.set(node.type, (counts.get(node.type) ?? 0) + 1)
    }
    return counts
  }, [nodes])

  const visibleNodes = useMemo(
    () => nodes.filter((node) => activeTypes.has(node.type)),
    [activeTypes, nodes],
  )
  const visibleEdges = useMemo(() => {
    const ids = new Set(visibleNodes.map((node) => node.id))
    return edges.filter((edge) => ids.has(edge.source) && ids.has(edge.target))
  }, [edges, visibleNodes])
  const layout = useMemo(
    () => layoutGraph(visibleNodes, visibleEdges, size.width, size.height),
    [size.height, size.width, visibleEdges, visibleNodes],
  )
  const layoutById = useMemo(
    () => new Map(layout.map((node) => [node.id, node])),
    [layout],
  )

  const neighborMap = useMemo(() => {
    const result = new Map<string, Set<string>>()
    for (const edge of visibleEdges) {
      if (!result.has(edge.source)) {
        result.set(edge.source, new Set())
      }
      if (!result.has(edge.target)) {
        result.set(edge.target, new Set())
      }
      result.get(edge.source)!.add(edge.target)
      result.get(edge.target)!.add(edge.source)
    }
    return result
  }, [visibleEdges])

  const selectedNode = selectedId ? nodeById.get(selectedId) : null
  const activeId = hoveredId ?? selectedId
  const activeNeighbors = activeId ? neighborMap.get(activeId) ?? new Set<string>() : null
  const normalizedQuery = query.trim().toLowerCase()
  const searchActive = normalizedQuery.length > 0

  const selectedConnections = useMemo(() => {
    if (!selectedId) {
      return []
    }
    return visibleEdges
      .filter((edge) => edge.source === selectedId || edge.target === selectedId)
      .map((edge) => {
        const otherId = edge.source === selectedId ? edge.target : edge.source
        const other = nodeById.get(otherId)
        return other ? { edge, other } : null
      })
      .filter((item): item is { edge: GraphEdge; other: GraphNode } => Boolean(item))
  }, [nodeById, selectedId, visibleEdges])

  const resetView = () => {
    setPan({ x: 0, y: 0 })
    setZoom(1)
    setDragged({})
  }

  const changeZoom = (factor: number) => {
    const next = clamp(zoom * factor, 0.55, 2.4)
    const anchorX = size.width / 2
    const anchorY = size.height / 2
    setPan((current) => ({
      x: anchorX - (anchorX - current.x) * (next / zoom),
      y: anchorY - (anchorY - current.y) * (next / zoom),
    }))
    setZoom(next)
  }

  useEffect(() => {
    const svg = svgRef.current
    if (!svg) {
      return
    }
    const onWheel = (event: WheelEvent) => {
      event.preventDefault()
      const rect = svg.getBoundingClientRect()
      const current = view.current
      const factor = event.deltaY < 0 ? 1.1 : 0.9
      const next = clamp(current.zoom * factor, 0.55, 2.4)
      const pointerX = ((event.clientX - rect.left) / rect.width) * size.width
      const pointerY = ((event.clientY - rect.top) / rect.height) * size.height
      setPan({
        x: pointerX - (pointerX - current.pan.x) * (next / current.zoom),
        y: pointerY - (pointerY - current.pan.y) * (next / current.zoom),
      })
      setZoom(next)
    }
    svg.addEventListener('wheel', onWheel, { passive: false })
    return () => svg.removeEventListener('wheel', onWheel)
  }, [size.height, size.width])

  const onBackgroundPointerDown = (event: ReactPointerEvent<SVGRectElement>) => {
    if (event.button !== 0) {
      return
    }
    event.preventDefault()
    interaction.current = {
      mode: 'pan',
      startX: event.clientX,
      startY: event.clientY,
      originX: pan.x,
      originY: pan.y,
    }
    svgRef.current?.setPointerCapture(event.pointerId)
    setSelectedId(null)
  }

  const onNodePointerDown = (event: ReactPointerEvent<SVGGElement>, node: GraphLayoutNode) => {
    if (event.button !== 0) {
      return
    }
    event.stopPropagation()
    event.preventDefault()
    const position = dragged[node.id] ?? { x: node.x, y: node.y }
    interaction.current = {
      mode: 'node',
      id: node.id,
      startX: event.clientX,
      startY: event.clientY,
      originX: position.x,
      originY: position.y,
    }
    svgRef.current?.setPointerCapture(event.pointerId)
    setSelectedId(node.id)
  }

  const onPointerMove = (event: ReactPointerEvent<SVGSVGElement>) => {
    const current = interaction.current
    if (!current || !svgRef.current) {
      return
    }
    const rect = svgRef.current.getBoundingClientRect()
    const scale = size.width / Math.max(1, rect.width)
    const dx = (event.clientX - current.startX) * scale
    const dy = (event.clientY - current.startY) * scale
    if (current.mode === 'pan') {
      setPan({ x: current.originX + dx, y: current.originY + dy })
      return
    }
    if (current.id) {
      setDragged((previous) => ({
        ...previous,
        [current.id!]: {
          x: clamp(current.originX + dx / zoom, 26, size.width - 26),
          y: clamp(current.originY + dy / zoom, 26, size.height - 26),
        },
      }))
    }
  }

  const finishInteraction = (event: ReactPointerEvent<SVGSVGElement>) => {
    interaction.current = null
    if (svgRef.current?.hasPointerCapture(event.pointerId)) {
      svgRef.current.releasePointerCapture(event.pointerId)
    }
  }

  const toggleType = (type: GraphNodeType) => {
    const next = new Set(activeTypes)
    if (next.has(type)) {
      next.delete(type)
    } else {
      next.add(type)
    }
    setActiveTypes(next)
    setDragged({})
    if (selectedId && nodeById.get(selectedId) && !next.has(nodeById.get(selectedId)!.type)) {
      setSelectedId(null)
    }
  }

  const showAll = () => {
    setActiveTypes(new Set(TYPE_ORDER))
    setDragged({})
  }

  const selectNode = (node: GraphNode) => {
    setSelectedId(node.id)
  }

  const onNodeKeyDown = (event: KeyboardEvent<SVGGElement>, node: GraphNode) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      selectNode(node)
    }
  }

  const nodeIsDimmed = (node: GraphNode): boolean => {
    if (searchActive && !matchesQuery(node, normalizedQuery)) {
      return true
    }
    return Boolean(activeNeighbors && node.id !== activeId && !activeNeighbors.has(node.id))
  }

  const edgeOpacity = (edge: GraphEdge): number => {
    const base = EDGE_STYLE[edge.type]?.opacity ?? 0.15
    if (activeId) {
      return edge.source === activeId || edge.target === activeId ? 0.9 : 0.035
    }
    if (searchActive) {
      const source = nodeById.get(edge.source)
      const target = nodeById.get(edge.target)
      return source && target && (matchesQuery(source, normalizedQuery) || matchesQuery(target, normalizedQuery))
        ? 0.42
        : 0.04
    }
    return base
  }

  const empty = visibleNodes.length === 0
  const noData = nodes.length === 0

  return (
    <section
      aria-label="Graphe de connaissance"
      className="min-w-0 rounded-2xl border border-line bg-paper p-4 sm:p-6"
    >
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-[10px] font-medium uppercase tracking-[0.22em] text-terracotta">Carte</p>
          <h2 className="mt-2 font-serif text-2xl sm:text-3xl">Le fil de tout.</h2>
          <p className="mt-2 max-w-xl text-sm leading-relaxed text-ink/55">
            Projets, tâches, moments, énergie et journal reliés dans une seule vue. Rien à ranger : le graphe
            se construit à partir de ce que tu organises déjà.
          </p>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-2">
          <p className="text-xs text-ink/45">
            {visibleNodes.length} nœud{visibleNodes.length > 1 ? 's' : ''} · {visibleEdges.length} lien
            {visibleEdges.length > 1 ? 's' : ''}
          </p>
          <button
            type="button"
            onClick={() => void load()}
            disabled={status === 'loading'}
            className="text-[11px] text-ink/45 underline-offset-4 hover:text-ink hover:underline disabled:opacity-50"
          >
            {status === 'loading' ? 'Chargement…' : 'Actualiser'}
          </button>
        </div>
      </header>

      <div className="mt-5 flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Filtrer le graphe par type">
          <button
            type="button"
            onClick={showAll}
            className="rounded-full border border-ink/15 px-2.5 py-1 text-[11px] text-ink/60 hover:border-ink/30 hover:text-ink"
          >
            Tout
          </button>
          {TYPE_ORDER.map((type) => {
            const active = activeTypes.has(type)
            const count = typeCounts.get(type) ?? 0
            return (
              <button
                key={type}
                type="button"
                aria-pressed={active}
                onClick={() => toggleType(type)}
                className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] ${
                  active
                    ? 'border-ink/20 bg-cream text-ink/75'
                    : 'border-transparent text-ink/30 hover:border-ink/10 hover:text-ink/55'
                }`}
              >
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ backgroundColor: TYPE_COLOR[type], opacity: active ? 1 : 0.35 }}
                  aria-hidden="true"
                />
                {TYPE_PLURAL[type]}
                <span className="tabular-nums text-ink/35">{count}</span>
              </button>
            )
          })}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <label className="relative min-w-[13rem] flex-1 sm:max-w-xs">
            <span className="sr-only">Chercher dans le graphe</span>
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Chercher un projet, un moment…"
              className="w-full rounded-full border border-ink/12 bg-wash/60 px-3 py-1.5 text-xs text-ink outline-none placeholder:text-ink/30 focus:border-terracotta/50"
            />
          </label>
          <p className="text-[11px] text-ink/35">Glisse pour explorer · molette pour zoomer</p>
        </div>
      </div>

      <div className="mt-4 grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1fr)_17rem]">
        <div ref={graphAreaRef} className="relative min-w-0 overflow-hidden rounded-2xl border border-line bg-paper">
          <svg
            ref={svgRef}
            viewBox={`0 0 ${size.width} ${size.height}`}
            role="group"
            aria-label="Graphe des relations entre projets, tâches, moments, énergie, blocs, journal et messages"
            className="block h-[62vh] min-h-[26rem] w-full touch-none select-none lg:h-[38rem]"
            onPointerMove={onPointerMove}
            onPointerUp={finishInteraction}
            onPointerCancel={finishInteraction}
          >
            <defs>
              <pattern id="calme-graph-grid" width="40" height="40" patternUnits="userSpaceOnUse">
                <path d="M 40 0 L 0 0 0 40" fill="none" stroke={COLOR.line} strokeWidth="0.7" opacity="0.55" />
              </pattern>
            </defs>
            <rect
              x="0"
              y="0"
              width={size.width}
              height={size.height}
              fill={COLOR.paper}
              onPointerDown={onBackgroundPointerDown}
            />
            <rect
              x="0"
              y="0"
              width={size.width}
              height={size.height}
              fill="url(#calme-graph-grid)"
              pointerEvents="none"
            />
            <g transform={`translate(${pan.x} ${pan.y}) scale(${zoom})`}>
              <g pointerEvents="none">
                {visibleEdges.map((edge) => {
                  const source = layoutById.get(edge.source)
                  const target = layoutById.get(edge.target)
                  if (!source || !target) {
                    return null
                  }
                  const position = (node: GraphLayoutNode) => dragged[node.id] ?? { x: node.x, y: node.y }
                  const from = position(source)
                  const to = position(target)
                  const style = EDGE_STYLE[edge.type] ?? { color: COLOR.ink, opacity: 0.15 }
                  return (
                    <line
                      key={edge.id}
                      x1={from.x}
                      y1={from.y}
                      x2={to.x}
                      y2={to.y}
                      stroke={style.color}
                      strokeWidth={activeId && (edge.source === activeId || edge.target === activeId) ? 1.4 : 1}
                      strokeOpacity={edgeOpacity(edge)}
                      strokeDasharray={style.dash}
                      strokeLinecap="round"
                    />
                  )
                })}
              </g>

              {layout.map((node) => {
                const position = dragged[node.id] ?? { x: node.x, y: node.y }
                const selected = selectedId === node.id
                const hovered = hoveredId === node.id
                const dimmed = nodeIsDimmed(node)
                const relatedToActive = Boolean(
                  activeId && (node.id === activeId || activeNeighbors?.has(node.id)),
                )
                const showLabel =
                  node.type === 'PROJECT' ||
                  selected ||
                  hovered ||
                  relatedToActive ||
                  (searchActive && matchesQuery(node, normalizedQuery)) ||
                  (zoom > 1.35 && node.type === 'BLOCK') ||
                  (zoom > 1.65 && node.type !== 'BLOCK')
                const label = shortLabel(node.label, node.type === 'PROJECT' ? 30 : 22)
                const labelAnchor =
                  position.x < 95 ? 'start' : position.x > size.width - 95 ? 'end' : 'middle'
                const labelX = labelAnchor === 'start' ? -node.r : labelAnchor === 'end' ? node.r : 0
                const opacity = dimmed ? 0.24 : 1
                return (
                  <g
                    key={node.id}
                    transform={`translate(${position.x} ${position.y})`}
                    opacity={opacity}
                    role="button"
                    tabIndex={0}
                    aria-label={`${TYPE_LABEL[node.type]} : ${node.label}`}
                    aria-pressed={selected}
                    className="cursor-grab outline-none focus-visible:opacity-100 active:cursor-grabbing"
                    onPointerDown={(event) => onNodePointerDown(event, node)}
                    onClick={(event) => {
                      event.stopPropagation()
                      selectNode(node)
                    }}
                    onPointerEnter={() => setHoveredId(node.id)}
                    onPointerLeave={() => setHoveredId((current) => (current === node.id ? null : current))}
                    onFocus={() => setHoveredId(node.id)}
                    onBlur={() => setHoveredId((current) => (current === node.id ? null : current))}
                    onKeyDown={(event) => onNodeKeyDown(event, node)}
                  >
                    <title>{`${TYPE_LABEL[node.type]} · ${node.label} — ${node.subtitle}`}</title>
                    {(selected || hovered) && (
                      <circle
                        r={node.r + 6}
                        fill="none"
                        stroke={COLOR.accent}
                        strokeWidth="1"
                        strokeOpacity="0.55"
                        pointerEvents="none"
                      />
                    )}
                    {node.type === 'PROJECT' && (
                      <>
                        <circle r={node.r} fill={COLOR.paper} stroke={COLOR.accent} strokeWidth="1.4" />
                        <circle r={3} fill={COLOR.accent} />
                      </>
                    )}
                    {node.type === 'TASK' && (
                      <circle
                        r={node.r}
                        fill={node.status === 'DONE' ? COLOR.sage : COLOR.ink}
                        stroke={COLOR.paper}
                        strokeWidth="1"
                      />
                    )}
                    {node.type === 'EVENT' && (
                      <rect
                        x={-node.r}
                        y={-node.r}
                        width={node.r * 2}
                        height={node.r * 2}
                        rx="1.5"
                        fill={node.status === 'FIXED' ? COLOR.sage : COLOR.accent}
                        transform="rotate(45)"
                      />
                    )}
                    {node.type === 'ENERGY' && (
                      <circle r={node.r} fill={COLOR.sage} stroke={COLOR.paper} strokeWidth="1" />
                    )}
                    {node.type === 'BLOCK' && (
                      <circle
                        r={node.r}
                        fill="none"
                        stroke={COLOR.ink}
                        strokeWidth="1"
                        strokeDasharray="3 4"
                        opacity="0.45"
                      />
                    )}
                    {node.type === 'ACTION' && (
                      <circle r={node.r} fill={COLOR.paper} stroke={COLOR.accent} strokeWidth="1" />
                    )}
                    {node.type === 'MESSAGE' && (
                      <rect
                        x={-node.r}
                        y={-node.r}
                        width={node.r * 2}
                        height={node.r * 2}
                        rx="1"
                        fill={COLOR.muted}
                        opacity="0.8"
                      />
                    )}
                    {showLabel && (
                      <text
                        x={labelX}
                        y={node.r + 13}
                        textAnchor={labelAnchor}
                        fontSize={node.type === 'PROJECT' ? 12 : node.type === 'TASK' || node.type === 'EVENT' ? 10 : 8.5}
                        fontFamily={node.type === 'PROJECT' ? "'Fraunces Variable', Georgia, serif" : 'system-ui, sans-serif'}
                        fontWeight={node.type === 'PROJECT' ? 600 : 500}
                        fill={COLOR.ink}
                        fillOpacity={dimmed ? 0.15 : node.type === 'PROJECT' ? 0.9 : 0.62}
                        pointerEvents="none"
                      >
                        {label}
                      </text>
                    )}
                  </g>
                )
              })}
            </g>
          </svg>

          {(status === 'loading' || status === 'error' || empty) && (
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-paper/80 p-6 backdrop-blur-[1px]">
              <div className="pointer-events-auto max-w-sm rounded-2xl border border-line bg-paper px-5 py-4 text-center">
                {status === 'loading' && (
                  <p className="text-sm text-ink/55">Le graphe se prépare…</p>
                )}
                {status === 'error' && (
                  <>
                    <p role="alert" className="text-sm text-terracotta">{loadError}</p>
                    <button
                      type="button"
                      onClick={() => void load()}
                      className="mt-3 rounded-full border border-ink/15 px-3 py-1.5 text-xs text-ink/65 hover:border-ink/30 hover:text-ink"
                    >
                      Réessayer
                    </button>
                  </>
                )}
                {status === 'ready' && empty && (
                  <>
                    <p className="text-sm text-ink/60">
                      {noData
                        ? 'Le graphe se remplira avec tes projets, tâches, moments et actions.'
                        : 'Aucun type sélectionné. Affiche une famille pour retrouver le fil.'}
                    </p>
                    {noData ? (
                      <p className="mt-2 text-xs leading-relaxed text-ink/40">
                        Commence par une phrase dans l’agent : le premier projet créera les premiers liens.
                      </p>
                    ) : (
                      <button
                        type="button"
                        onClick={showAll}
                        className="mt-3 rounded-full border border-ink/15 px-3 py-1.5 text-xs text-ink/65 hover:border-ink/30 hover:text-ink"
                      >
                        Tout afficher
                      </button>
                    )}
                  </>
                )}
              </div>
            </div>
          )}

          <div className="absolute bottom-3 right-3 flex items-center gap-1 rounded-full border border-line bg-paper/95 p-1">
            <button
              type="button"
              onClick={() => changeZoom(0.88)}
              aria-label="Dézoomer"
              className="flex h-7 w-7 items-center justify-center rounded-full text-sm text-ink/55 hover:bg-wash hover:text-ink"
            >
              −
            </button>
            <button
              type="button"
              onClick={resetView}
              aria-label="Recentrer le graphe"
              className="flex h-7 w-7 items-center justify-center rounded-full text-[10px] text-ink/45 hover:bg-wash hover:text-ink"
            >
              ⌂
            </button>
            <button
              type="button"
              onClick={() => changeZoom(1.12)}
              aria-label="Zoomer"
              className="flex h-7 w-7 items-center justify-center rounded-full text-sm text-ink/55 hover:bg-wash hover:text-ink"
            >
              +
            </button>
          </div>
        </div>

        <aside className="min-w-0 rounded-2xl border border-line bg-wash/35 p-4">
          {selectedNode ? (
            <div className="space-y-4">
              <div className="flex items-start justify-between gap-3">
                <p className="text-[10px] font-medium uppercase tracking-[0.2em] text-terracotta">
                  {TYPE_LABEL[selectedNode.type]}
                </p>
                <button
                  type="button"
                  onClick={() => setSelectedId(null)}
                  aria-label="Fermer le détail"
                  className="text-xs text-ink/35 hover:text-ink"
                >
                  Fermer
                </button>
              </div>
              <div>
                <h3 className="font-serif text-xl leading-tight">{selectedNode.label}</h3>
                <p className="mt-1 text-xs leading-relaxed text-ink/50">{selectedNode.subtitle}</p>
                <p className="mt-2 text-[10px] uppercase tracking-[0.14em] text-ink/35">
                  {STATUS_LABEL[selectedNode.status] ?? selectedNode.status}
                </p>
              </div>
              <div className="border-t border-line pt-3">
                <p className="text-[10px] font-medium uppercase tracking-[0.18em] text-ink/40">
                  {selectedConnections.length} lien{selectedConnections.length > 1 ? 's' : ''}
                </p>
                <ul className="mt-2 space-y-1.5">
                  {selectedConnections.slice(0, 9).map(({ edge, other }) => (
                    <li key={edge.id}>
                      <button
                        type="button"
                        onClick={() => setSelectedId(other.id)}
                        className="group flex w-full items-baseline justify-between gap-2 text-left"
                      >
                        <span className="truncate text-xs text-ink/65 group-hover:text-ink">{other.label}</span>
                        <span className="shrink-0 text-[10px] text-ink/30">{edge.label}</span>
                      </button>
                    </li>
                  ))}
                  {selectedConnections.length === 0 && (
                    <li className="text-xs leading-relaxed text-ink/40">Aucun lien pour le moment.</li>
                  )}
                  {selectedConnections.length > 9 && (
                    <li className="text-[10px] text-ink/35">+{selectedConnections.length - 9} autres liens</li>
                  )}
                </ul>
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              <div>
                <p className="text-[10px] font-medium uppercase tracking-[0.2em] text-ink/40">Légende</p>
                <p className="mt-2 text-xs leading-relaxed text-ink/45">
                  Clique un nœud pour voir ce qu’il relie. Les blocs en pointillés encadrent les dates.
                </p>
              </div>
              <ul className="space-y-2">
                {TYPE_ORDER.map((type) => (
                  <li key={type} className="flex items-center justify-between gap-2 text-xs">
                    <span className="flex items-center gap-2 text-ink/60">
                      <LegendMark type={type} />
                      {TYPE_LABEL[type]}
                    </span>
                    <span className="tabular-nums text-[10px] text-ink/30">{typeCounts.get(type) ?? 0}</span>
                  </li>
                ))}
              </ul>
              <div className="space-y-2 border-t border-line pt-3">
                <p className="flex items-center gap-2 text-[10px] text-ink/45">
                  <span className="inline-block h-px w-6 bg-ink/30" /> relation
                </p>
                <p className="flex items-center gap-2 text-[10px] text-ink/45">
                  <span className="inline-block h-px w-6 border-t border-dashed border-terracotta/60" /> dépendance / report
                </p>
              </div>
            </div>
          )}
        </aside>
      </div>
    </section>
  )
}
