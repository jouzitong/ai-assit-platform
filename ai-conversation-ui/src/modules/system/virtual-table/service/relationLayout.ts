import ELK from 'elkjs/lib/elk.bundled.js'
import type { ElkNode } from 'elkjs'

export type RelationLayoutMode = 'manual' | 'relation'

export interface RelationLayoutNode {
  id: string
  width: number
  height: number
}

export interface RelationLayoutEdge {
  id: string
  source: string
  target: string
}

export interface RelationLayoutPosition {
  x: number
  y: number
}

export interface RelationLayoutViewport {
  width: number
  height: number
}

const elk = new ELK()
const CANVAS_PADDING = 64
const NODE_SPACING = 80
const LAYER_SPACING = 160
const GRID_PADDING = 32
const GRID_COLUMN_SPACING = 48
const GRID_ROW_SPACING = 64

function gridBounds(nodes: RelationLayoutNode[], columns: number, startY: number) {
  let width = 0
  let height = startY
  for (let index = 0; index < nodes.length; index += columns) {
    const row = nodes.slice(index, index + columns)
    width = Math.max(
      width,
      row.reduce((total, node) => total + node.width, 0) + Math.max(0, row.length - 1) * GRID_COLUMN_SPACING,
    )
    height += Math.max(...row.map(node => node.height), 0)
    if (index + columns < nodes.length) height += GRID_ROW_SPACING
  }
  return {
    width: width + GRID_PADDING * 2,
    height: height + GRID_PADDING,
  }
}

function responsiveColumnCount(
  nodes: RelationLayoutNode[],
  viewport: RelationLayoutViewport,
  startY: number,
) {
  const maxNodeWidth = Math.max(...nodes.map(node => node.width), 0)
  if (!maxNodeWidth) return 1
  const fallbackWidth = GRID_PADDING * 2 + maxNodeWidth * 3 + GRID_COLUMN_SPACING * 2
  const viewportWidth = viewport.width > 0 ? viewport.width : fallbackWidth
  const viewportHeight = viewport.height > 0 ? viewport.height : 900
  const viewportAspect = viewportWidth / viewportHeight
  let bestColumns = 1
  let bestScale = 0
  let bestAspectDelta = Number.POSITIVE_INFINITY

  for (let columns = 1; columns <= nodes.length; columns += 1) {
    const bounds = gridBounds(nodes, columns, startY)
    const scale = Math.min(viewportWidth / bounds.width, viewportHeight / bounds.height)
    const aspectDelta = Math.abs(Math.log((bounds.width / bounds.height) / viewportAspect))
    if (scale > bestScale + 0.0001 || (Math.abs(scale - bestScale) <= 0.0001 && aspectDelta < bestAspectDelta)) {
      bestColumns = columns
      bestScale = scale
      bestAspectDelta = aspectDelta
    }
  }
  return bestColumns
}

export function calculateResponsiveGridLayout(
  nodes: RelationLayoutNode[],
  viewport: RelationLayoutViewport,
  startY = GRID_PADDING,
) {
  const positions = new Map<string, RelationLayoutPosition>()
  if (!nodes.length) return positions

  const columns = responsiveColumnCount(nodes, viewport, startY)
  let rowY = startY
  for (let index = 0; index < nodes.length; index += columns) {
    const row = nodes.slice(index, index + columns)
    let columnX = GRID_PADDING
    row.forEach((node) => {
      positions.set(node.id, { x: columnX, y: rowY })
      columnX += node.width + GRID_COLUMN_SPACING
    })
    rowY += Math.max(...row.map(node => node.height), 0) + GRID_ROW_SPACING
  }
  return positions
}

function layoutIsolatedNodes(
  nodes: RelationLayoutNode[],
  startY: number,
  positions: Map<string, RelationLayoutPosition>,
  viewport: RelationLayoutViewport,
) {
  calculateResponsiveGridLayout(nodes, viewport, startY)
    .forEach((position, id) => positions.set(id, position))
}

export async function calculateRelationLayout(
  nodes: RelationLayoutNode[],
  edges: RelationLayoutEdge[],
  viewport: RelationLayoutViewport = { width: 0, height: 0 },
) {
  const positions = new Map<string, RelationLayoutPosition>()
  if (!nodes.length) return positions

  const nodeIds = new Set(nodes.map(node => node.id))
  const validEdges = edges
    .filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target))
    .sort((left, right) => left.id.localeCompare(right.id))
  const connectedIds = new Set(validEdges.flatMap(edge => [edge.source, edge.target]))
  const connectedNodes = nodes
    .filter(node => connectedIds.has(node.id))
    .sort((left, right) => left.id.localeCompare(right.id))
  const isolatedNodes = nodes
    .filter(node => !connectedIds.has(node.id))
    .sort((left, right) => left.id.localeCompare(right.id))

  let isolatedStartY = CANVAS_PADDING
  if (connectedNodes.length) {
    const graph: ElkNode = {
      id: 'virtual-relation-root',
      layoutOptions: {
        'elk.algorithm': 'layered',
        'elk.direction': 'RIGHT',
        'elk.edgeRouting': 'ORTHOGONAL',
        'elk.spacing.nodeNode': String(NODE_SPACING),
        'elk.layered.spacing.nodeNodeBetweenLayers': String(LAYER_SPACING),
        'elk.layered.nodePlacement.strategy': 'NETWORK_SIMPLEX',
        'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP',
        'elk.separateConnectedComponents': 'true',
        'elk.randomSeed': '1',
        'elk.padding': `[top=${CANVAS_PADDING},left=${CANVAS_PADDING},bottom=${CANVAS_PADDING},right=${CANVAS_PADDING}]`,
      },
      children: connectedNodes.map(node => ({
        id: node.id,
        width: node.width,
        height: node.height,
      })),
      edges: validEdges.map(edge => ({
        id: edge.id,
        sources: [edge.source],
        targets: [edge.target],
      })),
    }
    const result = await elk.layout(graph)
    result.children?.forEach((node) => {
      positions.set(node.id, {
        x: node.x ?? CANVAS_PADDING,
        y: node.y ?? CANVAS_PADDING,
      })
    })
    isolatedStartY = Math.max(
      ...connectedNodes.map((node) => {
        const position = positions.get(node.id)
        return (position?.y ?? CANVAS_PADDING) + node.height
      }),
      CANVAS_PADDING,
    ) + LAYER_SPACING
  }

  layoutIsolatedNodes(isolatedNodes, isolatedStartY, positions, viewport)
  return positions
}
