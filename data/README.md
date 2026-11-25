# Sample upload datasets

Six random datasets for the **Upload files** screen. For each dataset, upload `random_NN_transactions.csv`
as the Transactions CSV and `random_NN_blacklist.csv` as the Blacklist CSV. `random_NN_labels.csv` is the
ground truth for each row (`duplicate`, `blacklisted`, `high_amount`) and is not uploaded.

| Dataset | Rows | DUPLICATE | BLACKLISTED | Injected high amounts | Percentile |
| --- | ---: | ---: | ---: | ---: | ---: |
| `random_01` | 3,500 | 93 | 121 | 118 | 96.62 |
| `random_02` | 3,500 | 338 | 41 | 0 | 99 |
| `random_03` | 500 | 6 | 185 | 1 | 99.8 |
| `random_04` | 500 | 40 | 141 | 15 | 97 |
| `random_05` | 3,500 | 0 | 0 | 0 | 99 |
| `random_06` | 800 | 136 | 155 | 42 | 94.75 |

`random_05` is clean: no duplicates and no blacklisted accounts. Every row in every file is valid, so no
dataset has rejected rows.

Everyday amounts stay under 1.2 lakh rupees, and injected high amounts are from 2 lakh to 50 lakh rupees. A
run's `HIGH_AMOUNT` flags are the amounts above a percentile of that run's own amounts. With the percentile
in the table, those flags are exactly the injected rows. A file with no injected rows still has its top
slice flagged at P99.

`manifest.json` records the seed and these counts. To rebuild the files, or to make a new random batch with
a different seed, run this from the repository root:

```bash
python3 data/generate_random_datasets.py data 1795581722
python3 data/generate_random_datasets.py /path/to/new-batch
```

The script needs only the Python standard library.
