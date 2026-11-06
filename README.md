# TxnScreen

TxnScreen is a fraud and duplicate transaction screener for fraud analysts. It reads transaction records from CSV files, checks them for duplicates and suspicious activity, and presents the flagged results in a web interface. The detection algorithms are our own Java code, and a Bloom filter hit is always confirmed against an exact store before a flag is raised. The repository is a monorepo with a Spring Boot backend in `backend/`, a React frontend in `frontend/`, sample data in `data/` and documentation in `docs/`.

## Team

- Krrish
- Hardik
- Vedant

## Setup

Setup instructions are added in a later commit.
