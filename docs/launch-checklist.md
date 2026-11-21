# Launch checklist

The build plan's launch checklist, with the state of each item on 19 Nov 2026 at commit `84be4b7`. An
item is ticked only when it was checked, and the evidence column says how. Items that need the live site
stay open until the custom domain (H12) is set up and someone checks them there.

| Done | Item | Evidence or what is missing |
| :---: | --- | --- |
| [ ] | Custom domain connected, HTTPS active on the apex, www redirects to apex | Not done: H12. No domain is set up yet |
| [ ] | `api.` subdomain connected to Render with an active certificate | Not done: H12 |
| [ ] | Favicon (SVG, 32 px PNG, 180 px Apple touch icon) shows in the browser tab and on mobile home screen | In the repository: all three files are in `frontend/public/` and linked in `index.html`. Not yet checked on the live site or a phone |
| [ ] | No "Made with" or "Built with AI" badge, platform watermark, default Vite title or default icon anywhere | In the repository: the title is "TxnScreen", no Vite or React demo assets remain, and no badge text exists. Not yet checked on the live site, where a hosting badge would appear |
| [ ] | Privacy policy page live and linked from the footer, every statement true | Linked from the footer, and every statement matches the code: in-memory runs, 30 minutes, 10 runs, deleted on restart, no cookies, only the theme choice in browser storage (corrected 21 Nov 2026), no analytics, Vercel and Render named. Not live yet |
| [ ] | Terms and conditions page live and linked from the footer | Linked from the footer. Not live yet |
| [x] | No emoji anywhere in the UI, README or report | Every tracked text file scanned, none found |
| [x] | No em dash character anywhere in the UI copy | Every tracked text file scanned for em and en dashes, none found |
| [x] | No invented metrics, counters, reviews, testimonials, user logos or stock/AI images | The Home, Method and Screen pages were checked: their only fixed number is the real 200,000 row upload limit, and the only images are the favicon and the README screenshots of the app |
| [ ] | Every number in the UI traces to an API response or `results.json` | Partly checked: the Benchmarks page reads `results.json` at build time with an empty state if it is missing, and the static pages have no invented numbers. The demo values in `docs/demo-script.md` come from API responses. Still to do: click through the running app and compare each panel with the API |
| [x] | All tests pass in CI on `main`; at least 7 tests per algorithm | CI passed on `3b78cd9` and `6422e76`. Bloom filter: 16 algorithm tests (`BloomFilterTest`, `BloomMathTest`, `MurmurHash3Test`). Segment tree: 16 (`SegmentTreeTest`, `TimeWindowIndexTest`). Quickselect: 11 (`RandomizedQuickselectTest`). API integration tests on top |
| [x] | Benchmarks run at 10K, 100K and 1M, committed with machine details | `docs/benchmarks/results.json` and `docs/benchmarks/machine.md`, commit `a6c6ba6` |
| [ ] | Upload of `edge_cases.csv` on the live site shows 5 rejected rows with correct reasons | Checked locally on 19 Nov 2026: 12 valid, 5 rejected with the right reasons, 2 duplicates. Not yet checked on the live site |
| [ ] | README setup followed successfully on a clean machine by someone who did not write it | Not done yet. Needs a teammate |
| [x] | Report PDF is 5 to 8 pages and includes individual contributions | `docs/report/TxnScreen_Technical_Report.pdf`, with contributions in section 8 |
| [ ] | Each person has 15 commits on `main` under their own GitHub account | Krrish 20, Hardik 16, Vedant 14, not counting merge commits. Vedant is one short because V13 and V15 were made by Krrish |

## Also open

- **Lighthouse** has not been run on the deployed site, which V14 required. Run it on Home, Screen and a
  run page once the site is live.
- **Page titles** do not change per route: every page shows "TxnScreen" in the tab. V13's check asked for
  a title per page.
