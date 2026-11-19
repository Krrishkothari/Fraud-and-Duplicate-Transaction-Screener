// Diagrams for the Method page. Each one draws a small worked example from the text beside it.

const BITS = 16
/** Bits the example key sets: (h1 + i * h2) mod m for h1 = 2, h2 = 5, m = 16 and i = 0, 1, 2. */
const KEY_BITS = [2, 7, 12]
/** Bits already set by other keys, to show that bits are shared. */
const OTHER_BITS = [4, 9, 14]

/** A 16-bit filter after one key sets its three bits by double hashing. */
export function BloomDiagram() {
  return (
    <figure className="diagram">
      <div className="bit-diagram" aria-hidden="true">
        <div className="bit-row bit-probes">
          {KEY_BITS.map((bit, i) => (
            <span
              key={bit}
              className="bit-probe"
              style={{ gridColumn: bit + 1 }}
            >
              i = {i}
            </span>
          ))}
        </div>
        <div className="bit-row">
          {Array.from({ length: BITS }, (_, bit) => {
            const own = KEY_BITS.includes(bit)
            const other = OTHER_BITS.includes(bit)
            return (
              <span
                key={bit}
                className={
                  own ? 'bit bit-key' : other ? 'bit bit-other' : 'bit'
                }
              >
                {own || other ? 1 : 0}
              </span>
            )
          })}
        </div>
        <div className="bit-row bit-indexes">
          {Array.from({ length: BITS }, (_, bit) => (
            <span key={bit}>{bit}</span>
          ))}
        </div>
      </div>
      <figcaption>
        A key with <code>h1 = 2</code> and <code>h2 = 5</code> added to a filter
        with m = 16 and k = 3 sets bits 2, 7 and 12. The gray bits were set by
        other keys. A lookup reads the same three bits.
      </figcaption>
    </figure>
  )
}

interface TreeNode {
  id: number
  x: number
  y: number
  /** merged is a node the example query reads; covered is a leaf inside the queried range. */
  state?: 'merged' | 'covered'
}

const NODES: TreeNode[] = [
  { id: 1, x: 240, y: 28 },
  { id: 2, x: 120, y: 92 },
  { id: 3, x: 360, y: 92, state: 'merged' },
  { id: 4, x: 60, y: 156 },
  { id: 5, x: 180, y: 156, state: 'merged' },
  { id: 6, x: 300, y: 156, state: 'covered' },
  { id: 7, x: 420, y: 156, state: 'covered' },
]

const NODE_WIDTH = 48
const NODE_HEIGHT = 28

/** A segment tree over four buckets, held in an array, answering a query for buckets 1 to 3. */
export function SegmentTreeDiagram() {
  const byId = new Map(NODES.map((node) => [node.id, node]))
  return (
    <figure className="diagram">
      <svg
        className="tree-diagram"
        viewBox="0 0 480 212"
        role="img"
        aria-label="A tree with root node 1, its children 2 and 3, and leaves 4 to 7 for buckets 0 to 3. The query for buckets 1 to 3 reads leaf 5 and node 3."
      >
        {NODES.filter((node) => node.id > 1).map((node) => {
          const parent = byId.get(Math.floor(node.id / 2))!
          return (
            <line
              key={node.id}
              className="tree-edge"
              x1={parent.x}
              y1={parent.y + NODE_HEIGHT / 2}
              x2={node.x}
              y2={node.y - NODE_HEIGHT / 2}
            />
          )
        })}
        {NODES.map((node) => (
          <g key={node.id}>
            <rect
              className={`tree-node${node.state ? ` tree-node-${node.state}` : ''}`}
              x={node.x - NODE_WIDTH / 2}
              y={node.y - NODE_HEIGHT / 2}
              width={NODE_WIDTH}
              height={NODE_HEIGHT}
              rx={6}
            />
            <text className="tree-label" x={node.x} y={node.y} dy="0.35em">
              {node.id}
            </text>
          </g>
        ))}
        {NODES.filter((node) => node.id >= 4).map((node) => (
          <text
            key={node.id}
            className="tree-bucket"
            x={node.x}
            y={196}
            textAnchor="middle"
          >
            bucket {node.id - 4}
          </text>
        ))}
      </svg>
      <figcaption>
        Node i has children 2i and 2i + 1, and bucket b is leaf 4 + b. A query
        for buckets 1 to 3 merges only two nodes: leaf 5 and node 3, which
        already holds the totals of buckets 2 and 3.
      </figcaption>
    </figure>
  )
}

/** One Quickselect round: the range splits around a pivot and only the part holding rank k is kept. */
export function QuickselectDiagram() {
  return (
    <figure className="diagram">
      <div className="partition-diagram" aria-hidden="true">
        <div className="partition-bar">
          <span className="partition-part" style={{ flexGrow: 44 }}>
            less than the pivot
          </span>
          <span className="partition-part" style={{ flexGrow: 14 }}>
            equal
          </span>
          <span
            className="partition-part partition-kept"
            style={{ flexGrow: 42 }}
          >
            greater than the pivot
          </span>
        </div>
        <div className="partition-marker" style={{ left: '76%' }}>
          rank k
        </div>
      </div>
      <figcaption>
        Rank k falls in the greater part, so the next round searches only that
        part. When rank k falls in the equal part, the pivot is the answer.
      </figcaption>
    </figure>
  )
}
