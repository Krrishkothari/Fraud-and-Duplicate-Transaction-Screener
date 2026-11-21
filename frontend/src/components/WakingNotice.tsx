import Alert from './Alert'

/**
 * Shown while a request has been running for 3 seconds (SLOW_AFTER_MS), which on the free hosting tier
 * usually means the server is starting after a quiet spell. Remove it as soon as the request finishes.
 */
function WakingNotice() {
  return (
    <Alert tone="info">
      Waking up the server. The first request can take up to a minute.
    </Alert>
  )
}

export default WakingNotice
