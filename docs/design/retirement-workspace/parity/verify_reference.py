"""Offline executable reference contract. Never opens personal finance data.

Run with Python containing numpy, pydantic, openpyxl; see README.md.
This is a reference adapter, not Android production code or a second tax engine.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from decimal import Decimal, ROUND_HALF_UP
import hashlib
from io import BytesIO
import json
from pathlib import Path
import sys
from unittest.mock import patch

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
checks = 0


def check(actual, expected, label):
    global checks
    if actual != expected:
        raise AssertionError(f"{label}: synthetic result {actual!r} != {expected!r}")
    checks += 1


def cents(value):
    return int((Decimal(str(value)) * 100).quantize(Decimal(1), rounding=ROUND_HALF_UP))


def deny(*args, **kwargs):
    raise RuntimeError("Reference contract forbids live network/config/database access")


def source_hashes(root):
    # Source-only allowlist: never enumerate data/, Epic/, Finance/ or .env files.
    files = sorted((root / "app").rglob("*.py"))
    files += sorted((root / "app/db").rglob("*.sql"))
    files += sorted((root / "tests").glob("test_*.py"))
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(files)}


def install_guard(root):
    # Defense against future reference changes accidentally reading personal inputs.
    def audit(event, args):
        if event in {"socket.connect", "socket.getaddrinfo", "sqlite3.connect"}:
            deny()
        if event == "open" and isinstance(args[0], (str, bytes)):
            path = Path(args[0]).resolve()
            if path.is_relative_to(root):
                rel = path.relative_to(root)
                source = rel.parts[0] in {"app", "tests"} and path.suffix in {".py", ".sql", ".pyc"}
                mode = args[1]
                if not source or (isinstance(mode, str) and any(c in mode for c in "wa+")):
                    deny()
    sys.addaudithook(audit)


def workbook_bytes(case):
    from openpyxl import Workbook
    book = Workbook()
    book.remove(book.active)
    for name, cells in case["sheets"].items():
        sheet = book.create_sheet(name)
        for address, value in cells.items():
            sheet[address] = value
    stream = BytesIO()
    book.save(stream)
    book.close()
    stream.seek(0)
    return stream


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--finance", type=Path, required=True)
    parser.add_argument("--write-source-manifest", action="store_true",
                        help="Explicitly re-baseline source hashes only, never expected outputs")
    args = parser.parse_args()
    root = args.finance.resolve()
    hashes = source_hashes(root)
    if args.write_source_manifest:
        (HERE / "source-manifest.json").write_text(json.dumps(hashes, indent=2) + "\n", encoding="utf-8")
    check(hashes, json.loads((HERE / "source-manifest.json").read_text(encoding="utf-8")), "reference source lock")
    install_guard(root)
    sys.path.insert(0, str(root))
    import numpy as np
    import app.config as config
    import app.db.session as db
    config._local_values = deny
    config.get_plaid_configuration = deny
    config.get_rentcast_api_key = deny
    db.get_connection = deny
    db.transaction = deny
    from app.money import parse_dollars_to_cents
    from app.integrations.plaid import _account_classification
    from app.integrations import rentcast
    from app.integrations.epic_workbook import read_workbook
    from app.models.accounts import AccountType, TaxTreatment
    from app.models.settings import FilingStatus
    from app.models.properties import PropertyWithMortgage, MortgageBase
    from app.sim import engine
    from app.sim.reference import ASSET_CLASSES_ALL
    from app.sim.amortization import monthly_payment, balance_after_months, schedule
    from app.sim.withdrawal_strategy import WithdrawalRule, WithdrawalStrategy, apply_strategy
    from app.tax import IncomeBreakdown
    from app.tax.federal import calculate_federal
    from app.tax.wisconsin import calculate_wisconsin
    from app.tax.aca import premium_tax_credit
    from app.tax.vectorized import federal_vec, state_vec
    data = json.loads((HERE / "cases.json").read_text(encoding="utf-8"))
    for row in data["money"]:
        check(parse_dollars_to_cents(row["input"]), row["cents"], "money")
    for row in data["classification"]:
        kind, tax = _account_classification(row["subtype"])
        check((kind.value, tax.value), (row["type"], row["tax"]), "classification")

    payload = data["property"]["payload"]
    @contextmanager
    def synthetic_response(*args, **kwargs):
        yield BytesIO(json.dumps(payload).encode())
    with patch.object(rentcast, "urlopen", synthetic_response):
        value = rentcast.fetch_value_estimate("Synthetic property", "synthetic-not-a-credential")
    for field, expected in data["property"]["expected"].items():
        check(getattr(value, field), expected, "RentCast " + field)
    # Missing ranges and invalid price are distinct from zero value.
    original = payload
    payload = {"price": 0}
    with patch.object(rentcast, "urlopen", synthetic_response):
        value = rentcast.fetch_value_estimate("Synthetic property", "synthetic-not-a-credential")
        check((value.estimate_cents, value.range_low_cents), (0, None), "RentCast zero/missing")
    payload = {"price": -1}
    with patch.object(rentcast, "urlopen", synthetic_response):
        try:
            rentcast.fetch_value_estimate("Synthetic property", "synthetic-not-a-credential")
        except rentcast.RentCastError:
            check(True, True, "RentCast rejects negative price")
        else:
            raise AssertionError("negative property price accepted")
    payload = original
    for row in data["property"]["equity"]:
        prop = PropertyWithMortgage(id=1, account_id=1, name="Synthetic property",
            current_value_cents=row["value_cents"], ownership_pct=row["ownership_bps"] / 100,
            mortgage=MortgageBase(principal_cents=row["mortgage_cents"], rate_bps=0,
                term_months=12, months_remaining=12, monthly_payment_cents=0))
        check(prop.equity_cents, row.get("reference_cents", row["expected_cents"]), "reference equity")

    for row in data["amortization"]:
        args3 = (row["principal_cents"] / 100, row["rate_bps"], row["term_months"])
        check(cents(monthly_payment(*args3)), row["payment_cents"], "mortgage payment")
        balance = balance_after_months(*args3, row["months"])
        check(cents(balance), row["balance_cents"], "mortgage balance")
        rows = schedule(*args3, months=row["months"])
        check(cents(rows[-1].balance), row["balance_cents"], "schedule closed-form parity")
        if "interest_cents" in row:
            check(cents(rows[0].interest), row["interest_cents"], "mortgage interest")
    for section, scalar, vector in [("federal", calculate_federal, federal_vec),
                                    ("wisconsin", calculate_wisconsin, state_vec)]:
        for row in data[section]:
            ordinary, gains = row["ordinary_cents"] / 100, row["ltcg_cents"] / 100
            filing = FilingStatus(row["filing"])
            check(cents(scalar(IncomeBreakdown(ordinary=ordinary, ltcg=gains), filing)), row["tax_cents"], section)
            args3 = (np.array([ordinary]), np.array([gains]), filing)
            result = vector(*args3) if section == "federal" else vector(*args3, "WI")
            check(cents(result[0]), row["tax_cents"], section + " vector")
    for row in data["aca"]:
        credit = premium_tax_credit(row["magi_cents"] / 100, row["premium_cents"] / 100,
                                    row["household"], row["extended"])
        check(cents(credit), row["credit_cents"], "ACA")
    for row in data["withdrawal"]:
        balances = np.zeros((1, len(engine.BUCKET_INDEX)))
        for bucket, value in row["balances_cents"].items():
            balances[0, engine.BUCKET_INDEX[bucket]] = value / 100
        result = apply_strategy(WithdrawalStrategy("Synthetic", [WithdrawalRule(**r) for r in row["rules"]]),
                                balances, row["age"], np.array([row["need_cents"] / 100]))
        for bucket, expected in row["remaining_cents"].items():
            check(cents(result.balances_after[0, engine.BUCKET_INDEX[bucket]]), expected, "withdrawal remaining")
        for field, key in [("ordinary","ordinary_cents"),("ltcg","ltcg_cents"),
                           ("tax_free","tax_free_cents"),("unmet_need","unmet_cents")]:
            check(cents(getattr(result, field)[0]), row[key], "withdrawal " + field)

    alloc = np.zeros(len(ASSET_CLASSES_ALL))
    alloc[ASSET_CLASSES_ALL.index("us_stocks")] = 1
    row = data["forecast"]["deterministic"]
    seed = engine.AccountSeed("Synthetic", AccountType.IRA_ROTH, TaxTreatment.ROTH,
                              row["starting_cents"] / 100, alloc)
    inputs = engine.ProjectionInputs(current_age=row["current_age"], retirement_age=row["retirement_age"],
        plan_end_age=row["end_age"], target_real_spending=0, accounts=[seed])
    result = engine.run_deterministic(inputs)
    check([cents(v) for v in result.total_by_path[0]], row["expected_path_cents"], "deterministic forecast")

    row = data["forecast"]["sequence"]
    tape = np.zeros((row["path_count"], 2, len(ASSET_CLASSES_ALL)))
    tape[:, :, ASSET_CLASSES_ALL.index("us_stocks")] = row["returns"]
    class FixedReturns:
        def multivariate_normal(self, mean, covariance, size):
            check(tuple(size), (row["path_count"], 2), "return tape shape")
            return tape.copy()
    inputs = engine.ProjectionInputs(current_age=row["current_age"], retirement_age=row["retirement_age"],
        plan_end_age=row["end_age"], target_real_spending=row["spending_cents"] / 100, accounts=[seed],
        withdrawal_strategy=WithdrawalStrategy.default_retire_at_50())
    with patch.object(engine.np.random, "default_rng", return_value=FixedReturns()):
        result = engine.run_monte_carlo(inputs, n_paths=row["path_count"], seed=0)
    check([[cents(v) for v in path] for path in result.total_by_path], row["expected_paths_cents"], "sequence of returns")
    for percentile in [10, 50, 90]:
        check(cents(result.percentile(percentile)[-1]), row[f"terminal_p{percentile}_cents"], "forecast percentile")
    check(result.success_rate, row["success_numerator"] / row["path_count"], "forecast success")
    # Locked retirement assets can remain positive while the path fails.
    locked = engine.AccountSeed("Synthetic locked", AccountType.IRA_TRADITIONAL, TaxTreatment.TRADITIONAL, 1000, alloc)
    result = engine.run_deterministic(engine.ProjectionInputs(current_age=50, retirement_age=50,
        plan_end_age=51, target_real_spending=100, accounts=[locked],
        withdrawal_strategy=WithdrawalStrategy.default_retire_at_50()))
    check((result.success_rate, cents(result.unmet_spending_by_year[0, 0]), cents(result.total_by_path[0, -1])),
          (0.0, 10000, 106500), "locked asset failure")

    row = data["timing"]["income"]
    streams = [engine.IncomeStream(**stream) for stream in row["streams"]]
    income = engine._income_by_tax_kind(streams, np.array(row["ages"]))
    for values, field in zip(income, ["total_cents", "ordinary_cents", "ss_cents"]):
        check([cents(v) for v in values], row[field], "income timing " + field)
    row = data["timing"]["epic"]
    for inflation, prekey, cashkey in [(0,"pretax_cents","taxable_cents"),
                                      (row["inflation_rate"],"real_pretax_cents","real_taxable_cents")]:
        result = engine.run_deterministic(engine.ProjectionInputs(current_age=row["current_age"],
            retirement_age=row["retirement_age"], plan_end_age=row["end_age"], target_real_spending=0,
            base_calendar_year=row["base_year"], inflation_rate=inflation,
            epic_annual_values=[engine.EpicAnnualValue(**value) for value in row["annual"]],
            withdrawal_strategy=WithdrawalStrategy.default_retire_at_50()))
        check([cents(v) for v in result.epic_balance_by_year[0]], row[prekey], "Epic retirement transfer")
        check([cents(v) for v in result.balances_by_bucket[0,:,engine.BUCKET_INDEX["taxable"]]], row[cashkey], "Epic cash and inflation")
    row = data["timing"]["property"]
    # Empty accounts use the reference default taxable allocation after liquidation.
    result = engine.run_deterministic(engine.ProjectionInputs(current_age=row["current_age"],
        retirement_age=row["retirement_age"], plan_end_age=row["end_age"], target_real_spending=0,
        real_estate_positions=[engine.RealEstatePosition("Synthetic home", row["value_cents"] / 100,
            row["mortgage_cents"] / 100, 0, 0, 0, 0, ownership_pct=row["ownership_pct"])],
        withdrawal_strategy=WithdrawalStrategy.default_retire_at_50()))
    check([cents(v) for v in result.real_estate_equity_by_year[0]], row["equity_cents"], "property liquidation")
    # Reference cash allocation is 0.5% real return, applied AFTER property liquidation.
    check(cents(result.balances_by_bucket[0,-1,engine.BUCKET_INDEX["taxable"]]),
          row["taxable_after_growth_cents"], "property proceeds grow in transfer year")

    book = read_workbook(workbook_bytes(data["workbook"]), source_name="synthetic.xlsm")
    for fixture in ("values.xlsm", "cached.xlsm"):
        with (HERE / "shareworks" / fixture).open("rb") as source:
            golden = read_workbook(source, source_name="synthetic.xlsm")
        check(golden.to_json_safe(), book.to_json_safe(), "native golden workbook " + fixture)
    expected = data["workbook"]["expected"]
    actual = {"share_price_cents":cents(book.share_price),
        "vested_shares":format(Decimal(str(book.total_vested_shares)).normalize(), "f"),
        "unvested_shares":format(Decimal(str(book.total_unvested_shares)).normalize(), "f"),
        "loans_cents":cents(book.total_loans),
        "net_current_cents":cents(book.total_vested_value - book.total_loans),
        "projected_after_tax_cents":cents(book.projection.net_value_after_tax),
        "annual_year":book.annual_projection[0].year,
        "annual_after_tax_cents":cents(book.annual_projection[0].after_tax_value)}
    for field, value in actual.items():
        check(value, expected[field], "workbook " + field)
    check(abs(book.historical_growth_volatility_pct - expected["historical_volatility_pct"]) < 1e-12, True, "workbook volatility")
    # Document the reference gap explicitly; native contract rejects this mutation.
    bad = json.loads(json.dumps(data["workbook"]))
    del bad["sheets"]["2026 Consolidated Statement"]["E6"]
    check(read_workbook(workbook_bytes(bad), source_name="synthetic.xlsm").share_price, 0.0, "reference missing-cell gap")
    print(f"PASS: {checks} reference assertions; no network, personal files, or database access")


if __name__ == "__main__":
    main()
