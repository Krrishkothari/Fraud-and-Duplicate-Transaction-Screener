import { Link } from 'react-router-dom'
import { ContactLink, LegalPage, LegalSection } from '../components/LegalPage'

function PrivacyPage() {
  return (
    <LegalPage
      title="Privacy policy"
      lastUpdated="18 November 2026"
      intro="What happens to the files you give TxnScreen, and how long they are kept."
    >
      <LegalSection title="What we process">
        <p>
          When you upload a transactions file and a blacklist, or generate
          sample data, the server reads them in memory to screen the
          transactions and work out the results you see. Transactions you add on
          a run page are handled the same way.
        </p>
      </LegalSection>

      <LegalSection title="How long it is kept">
        <ul>
          <li>Each run is deleted 30 minutes after it is created.</li>
          <li>
            The server keeps at most 10 runs. When a new run is created and 10
            are already kept, the run that was used least recently is deleted.
          </li>
          <li>
            Every run is deleted when the server restarts or stops, including
            when the hosting provider stops it after a period without traffic.
          </li>
          <li>
            Nothing you upload is written to a database or to disk. The app has
            no database.
          </li>
        </ul>
      </LegalSection>

      <LegalSection title="Accounts, cookies and tracking">
        <p>
          There are no accounts and no sign-in. The site sets no cookies, keeps
          nothing in your browser&apos;s storage, and loads no analytics or
          tracking scripts. Its fonts are served from the site itself, not from
          another company.
        </p>
      </LegalSection>

      <LegalSection title="Logs">
        <p>
          The site and its API run on third-party hosting providers. Like most
          hosting providers, they may keep standard request logs, such as your
          IP address, the time of each request and the address requested. Their
          own privacy policies cover those logs. When a request fails with an
          unexpected error, our server also writes a technical error report to
          its log so the bug can be fixed.
        </p>
      </LegalSection>

      <LegalSection title="What to upload">
        <p>
          Upload synthetic or anonymised data only. Never upload real customer
          data, real account numbers or card details. The sample data option on
          the <Link to="/screen">Screen</Link> page creates realistic data you
          can use instead.
        </p>
      </LegalSection>

      <LegalSection title="Contact">
        <p>
          Questions about this policy: <ContactLink />. See also the{' '}
          <Link to="/terms">terms of use</Link>.
        </p>
      </LegalSection>
    </LegalPage>
  )
}

export default PrivacyPage
