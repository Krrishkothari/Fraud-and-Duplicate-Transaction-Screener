import { useId, useRef, useState } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'
import { rovingIndex } from './rovingIndex'

export interface TabItem {
  id: string
  label: string
  /** Shown after the label, such as a count. */
  count?: string
  content: ReactNode
}

interface TabsProps {
  /** Names the tab list for screen readers. */
  label: string
  tabs: TabItem[]
}

/**
 * Tabs over panels of one page. A panel mounts the first time its tab opens and then stays mounted while
 * hidden, so what was typed or loaded in it survives a switch to another tab.
 */
function Tabs({ label, tabs }: TabsProps) {
  const baseId = useId()
  const list = useRef<HTMLDivElement>(null)
  const [active, setActive] = useState(tabs[0].id)
  const [opened, setOpened] = useState<ReadonlySet<string>>(
    () => new Set([tabs[0].id]),
  )

  function open(id: string) {
    setActive(id)
    setOpened((current) =>
      current.has(id) ? current : new Set([...current, id]),
    )
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const index = tabs.findIndex((tab) => tab.id === active)
    const next = rovingIndex(event.key, index, tabs.length, true)
    if (next === null) {
      return
    }
    event.preventDefault()
    open(tabs[next].id)
    list.current
      ?.querySelectorAll<HTMLButtonElement>('[role="tab"]')
      [next]?.focus()
  }

  return (
    <div className="tabs">
      <div
        ref={list}
        className="tab-list"
        role="tablist"
        aria-label={label}
        onKeyDown={handleKeyDown}
      >
        {tabs.map((tab) => {
          const selected = tab.id === active
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              id={`${baseId}-tab-${tab.id}`}
              aria-selected={selected}
              aria-controls={`${baseId}-panel-${tab.id}`}
              tabIndex={selected ? 0 : -1}
              className="tab"
              onClick={() => open(tab.id)}
            >
              {tab.label}
              {tab.count !== undefined && (
                <span className="tab-count mono">{tab.count}</span>
              )}
            </button>
          )
        })}
      </div>
      {tabs.map((tab) => (
        <div
          key={tab.id}
          role="tabpanel"
          id={`${baseId}-panel-${tab.id}`}
          aria-labelledby={`${baseId}-tab-${tab.id}`}
          className="tab-panel"
          hidden={tab.id !== active}
          tabIndex={0}
        >
          {opened.has(tab.id) && tab.content}
        </div>
      ))}
    </div>
  )
}

export default Tabs
