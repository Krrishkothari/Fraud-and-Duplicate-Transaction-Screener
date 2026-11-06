import { useParams } from 'react-router-dom'

function RunPage() {
  const { id } = useParams()
  return <h1>Run {id}</h1>
}

export default RunPage
