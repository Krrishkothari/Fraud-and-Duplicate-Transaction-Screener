"""Writes random TxnScreen upload datasets: <name>_transactions.csv, <name>_blacklist.csv, <name>_labels.csv.

Usage, from the repository root: python3 data/generate_random_datasets.py OUT_DIR [SEED]
With the seed from data/manifest.json it rebuilds the files in data/ byte for byte.
"""
import json
import math
import random
import sys
import time
from dataclasses import dataclass, replace
from datetime import datetime, timedelta, timezone
from fractions import Fraction
from pathlib import Path

IST = timezone(timedelta(hours=5, minutes=30))
CATEGORIES = [("Grocery Store", 600, 0.6), ("Fuel Station", 1_500, 0.4), ("Restaurant", 900, 0.5),
              ("Pharmacy", 450, 0.6), ("Electronics Outlet", 15_000, 0.8), ("Clothing Store", 2_500, 0.7),
              ("Travel Agency", 12_000, 0.7), ("Utility Payment", 2_000, 0.5)]
HIGH_VALUE = ["Jewellery Store", "Electronics Outlet", "Travel Agency", "Wire Transfer Desk"]
NORMAL_CAP_PAISE = 120_000 * 100


@dataclass(frozen=True)
class Row:
    txn_id: str
    account: str
    paise: int
    epoch: int
    merchant: str
    duplicate: bool = False
    high: bool = False


def stamp(rng, start, days, night=False):
    if night:
        second = rng.randrange(5 * 3600)
    elif rng.random() < 0.85:
        second = 9 * 3600 + rng.randrange(13 * 3600)
    else:
        q = rng.randrange(11 * 3600)
        second = q if q < 9 * 3600 else q + 13 * 3600
    return start + rng.randrange(days) * 86_400 + second


def normal_row(rng, txn_id, account, start, days):
    n = 1 + rng.randrange(200)
    name, median, sigma = CATEGORIES[(n - 1) % len(CATEGORIES)]
    while True:
        paise = round(math.exp(math.log(median * 100) + sigma * rng.gauss(0, 1)))
        if paise < NORMAL_CAP_PAISE:
            break
    return Row(txn_id, account, max(100, paise), stamp(rng, start, days), f"{name} {n:03d}")


def high_row(rng, txn_id, account, start, days):
    rupees = math.exp(rng.uniform(math.log(200_000), math.log(5_000_000)))
    return Row(txn_id, account, round(rupees * 100), stamp(rng, start, days, rng.random() < 0.5),
               f"{rng.choice(HIGH_VALUE)} {1 + rng.randrange(200):03d}", high=True)


def build(rng, number, size, dup_rate, blacklist_share, high_rate):
    start = int(datetime(2026, rng.randint(1, 10), 1, tzinfo=IST).timestamp())
    days = rng.choice([7, 14, 30])
    accounts = [f"ACC{number * 1000 + i:06d}" for i in range(1, max(30, size // 20) + 1)]
    blacklisted = set(rng.sample(accounts, round(len(accounts) * blacklist_share)))
    weights = [rng.choice([1, 2, 3]) if a in blacklisted else 1 for a in accounts]
    dup_count = round(size * dup_rate)
    high_count = round(size * high_rate)
    prefix = f"T{number:02d}"

    originals = []
    for i in range(1, size - dup_count + 1):
        owner = rng.choices(accounts, weights)[0]
        make = high_row if i <= high_count else normal_row
        originals.append(make(rng, f"{prefix}{i:08d}", owner, start, days))
    copies = []
    for _ in range(dup_count):
        row = rng.choice(originals)
        delay = rng.randint(1, 120) if rng.random() < 0.7 else rng.randint(3_600, 3 * 86_400)
        copies.append(replace(row, epoch=row.epoch + delay, duplicate=True))

    rows = sorted(originals + copies, key=lambda r: (r.epoch, r.duplicate))
    never_seen = [f"ACC{number * 1000 + 900 + i:06d}" for i in range(1, rng.randint(2, 8))]
    return rows, sorted(blacklisted) + never_seen, blacklisted


def isolating_percentile(n, high_rows):
    target = n - high_rows
    for h in range(10_000, 0, -1):
        if math.ceil(Fraction(h, 100) * n / 100) == target:
            return h / 100
    raise ValueError


def write(path, lines):
    path.write_text("".join(line + "\n" for line in lines), encoding="utf-8")


def main():
    out = Path(sys.argv[1])
    seed = int(sys.argv[2]) if len(sys.argv) > 2 else int(time.time())
    rng = random.Random(seed)
    out.mkdir(parents=True, exist_ok=True)
    plans = [  # one clean file, then random mixes
        (0.0, 0.0, 0.0),
        (rng.uniform(0.05, 0.20), rng.uniform(0.0, 0.05), 0.0),
        (rng.uniform(0.0, 0.03), rng.uniform(0.15, 0.35), rng.uniform(0.0, 0.01)),
        (rng.uniform(0.0, 0.03), rng.uniform(0.0, 0.05), rng.uniform(0.02, 0.05)),
        (rng.uniform(0.05, 0.15), rng.uniform(0.10, 0.30), rng.uniform(0.01, 0.04)),
        (rng.uniform(0.0, 0.20), rng.uniform(0.0, 0.30), rng.uniform(0.0, 0.05)),
    ]
    rng.shuffle(plans)
    summary = []
    for number, (dup, bl, high) in enumerate(plans, start=1):
        size = rng.choice([500, 800, 1200, 2000, 3500, 5000])
        rows, blacklist, blacklisted = build(rng, number, size, dup, bl, high)
        name = f"random_{number:02d}"
        write(out / f"{name}_transactions.csv", ["transaction_id,account_id,amount,timestamp,merchant"] + [
            f"{r.txn_id},{r.account},{r.paise // 100}.{r.paise % 100:02d},"
            f"{datetime.fromtimestamp(r.epoch, IST).isoformat()},{r.merchant}" for r in rows])
        write(out / f"{name}_blacklist.csv", ["account_id"] + blacklist)
        write(out / f"{name}_labels.csv", ["line_number,transaction_id,duplicate,blacklisted,high_amount"] + [
            f"{i},{r.txn_id},{str(r.duplicate).lower()},{str(r.account in blacklisted).lower()},"
            f"{str(r.high).lower()}" for i, r in enumerate(rows, start=2)])
        high_rows = sum(r.high for r in rows)
        summary.append({"name": name, "rows": len(rows), "duplicate": sum(r.duplicate for r in rows),
                        "blacklisted": sum(r.account in blacklisted for r in rows), "highAmount": high_rows,
                        "percentile": isolating_percentile(len(rows), high_rows) if high_rows else 99.0})
    write(out / "manifest.json", [json.dumps({"seed": seed, "datasets": summary}, indent=2)])
    for s in summary:
        print(s)


if __name__ == "__main__":
    main()
