import { Link } from 'react-router-dom'
import { ContactLink, LegalPage, LegalSection } from '../components/LegalPage'

function TermsPage() {
  return (
    <LegalPage
      title="Terms of use"
      lastUpdated="18 November 2026"
      intro="The rules for using TxnScreen. By using the site you agree to them."
    >
      <LegalSection title="What this is">
        <p>
          TxnScreen is an academic project built for the Advanced Algorithm
          course (CIA 3) at ATLAS uGDX School of Technology. It is not a
          commercial product or service.
        </p>
      </LegalSection>

      <LegalSection title="No warranty">
        <p>
          The site and its results are provided as is, without warranty of any
          kind. We do not promise that it is available, free of errors or
          suitable for any particular purpose.
        </p>
      </LegalSection>

      <LegalSection title="Results are screening aids">
        <p>
          Flags, thresholds and time window totals point to transactions worth a
          closer look. They are not decisions, and they are not financial, legal
          or compliance advice. A person should review a flagged transaction
          before acting on it.
        </p>
      </LegalSection>

      <LegalSection title="Acceptable use">
        <ul>
          <li>
            Upload synthetic or anonymised data only, never real personal,
            customer or card data.
          </li>
          <li>
            Do not try to overload, disrupt or break into the service, for
            example with automated bulk requests.
          </li>
          <li>Do not use the service for anything unlawful.</li>
        </ul>
      </LegalSection>

      <LegalSection title="Upload limits">
        <p>
          A transactions file may have at most 200,000 data rows and be at most
          20 MB, and generated sample data is limited to 200,000 rows. Larger
          requests are rejected. Runs are kept for at most 30 minutes, as the{' '}
          <Link to="/privacy">privacy policy</Link> explains.
        </p>
      </LegalSection>

      <LegalSection title="Changes">
        <p>
          We may change these terms or the service, or stop the service, at any
          time. The date at the top of this page shows when these terms last
          changed.
        </p>
      </LegalSection>

      <LegalSection title="Contact">
        <p>
          Questions about these terms: <ContactLink />.
        </p>
      </LegalSection>
    </LegalPage>
  )
}

export default TermsPage
