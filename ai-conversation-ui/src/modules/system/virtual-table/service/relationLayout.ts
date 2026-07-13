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

const elk = new ELK()
const CANVAS_PADDING = 64
const NODE_SPACING = 80
const LAYER_SPACING = 160
const ISOLATED_COLUMNS = 4

function layoutIsolatedNodes(
  nodes: RelationLayoutNode[],
  startY: number,
  positions: Map<string, RelationLayoutPosition>,
) {
  let rowY = startY
  for (let index = 0; index < nodes.length; index += ISOLATED_COLUMNS) {
    const row = nodes.slice(index, index + ISOLATED_COLUMNS)
    let columnX = CANVAS_PADDING
    row.forEach((node) => {
      positions.set(node.id, { x: columnX, y: rowY })
      columnX += node.width + NODE_SPACING
    })
    rowY += Math.max(...row.map(node => node.height), 0) + NODE_SPACING
  }
}

export async function calculateRelationLayout(
  nodes: RelationLayoutNode[],
  edges: RelationLayoutEdge[],
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

  layoutIsolatedNodes(isolatedNodes, isolatedStartY, positions)
  return positions
}
