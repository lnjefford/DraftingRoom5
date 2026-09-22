"""Executable architectural specifications; not tests of an implemented Kotlin app."""
from copy import deepcopy
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
import json
import re
import sqlite3
import unittest
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
DATA = json.loads((HERE / "cases.json").read_text(encoding="utf-8"))
MAX_CENTS = 100_000_000_000_000


def native_money(raw):
    text = raw.strip()
    negative = text.startswith("(") and text.endswith(")")
    if negative:
        text = text[1:-1]
    if text.startswith("-") and not negative:
        negative, text = True, text[1:]
    if text.startswith("$"):
        text = text[1:]
    if not re.fullmatch(r"(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d+)?", text):
        raise ValueError("invalid_money")
    value = int((Decimal(text.replace(",", "")) * 100).quantize(Decimal(1), rounding=ROUND_HALF_UP))
    if value > MAX_CENTS:
        raise ValueError("money_out_of_range")
    return -value if negative else value


def validate_workbook_cells(book):
    # Cell-level contract after bounded ZIP/XML parsing, not an OOXML implementation.
    sheets = book["sheets"]
    required = DATA["workbook"]["sheets"]
    for name in ["2026 Consolidated Statement", "Inputs and Summary", "Detailed Projections"]:
        if name not in sheets:
            raise ValueError("missing_sheet")
        for cell, example in required[name].items():
            if cell not in sheets[name]:
                raise ValueError("missing_required_cell")
            value = sheets[name][cell]
            if isinstance(example, (int, float)):
                if isinstance(value, bool):
                    raise ValueError("invalid_number")
                try:
                    number = Decimal(str(value))
                except Exception as error:
                    raise ValueError("missing_cache_or_invalid_number") from error
                if not number.is_finite() or number < 0:
                    raise ValueError("invalid_number")
    consolidated = sheets["2026 Consolidated Statement"]
    for column in ["C", "E", "F", "G", "H", "I", "J", "L", "M"]:
        total = sum(Decimal(str(consolidated.get(f"{column}{r}", 0))) for r in range(12, 19))
        if total != Decimal(str(consolidated[f"{column}19"])):
            raise ValueError("inconsistent_total")
    years = [v for key, v in sheets["Detailed Projections"].items() if re.fullmatch(r"[A-Z]+15", key)]
    if len(set(years)) != len(years):
        raise ValueError("duplicate_year")


class FinancialContract(unittest.TestCase):
    def test_money_exact_and_invalid_boundaries(self):
        for row in DATA["money"]:
            with self.subTest(row=row):
                self.assertEqual(native_money(row["input"]), row["cents"])
        for raw in DATA["money_native_reject"]:
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                native_money(raw)
        self.assertEqual(native_money("1000000000000"), MAX_CENTS)

    def test_owned_equity_including_negative_and_half_cent(self):
        for row in DATA["property"]["equity"]:
            amount = Decimal(row["value_cents"] - row["mortgage_cents"]) * row["ownership_bps"] / 10000
            self.assertEqual(int(amount.quantize(Decimal(1), rounding=ROUND_HALF_UP)), row["expected_cents"])

    def test_aggregate_counts_each_origin_once(self):
        row = DATA["aggregate"]
        self.assertEqual(row["ordinary_cents"] + row["epic_vested_cents"] - row["epic_loan_cents"]
                         + row["property_equity_cents"], row["expected_total_cents"])

    def test_workbook_accepts_complete_synthetic_cells(self):
        validate_workbook_cells(DATA["workbook"])

    def test_workbook_rejects_each_documented_invalid_mutation(self):
        for mutation in DATA["workbook"]["native_reject_mutations"]:
            book = deepcopy(DATA["workbook"])
            sheet = book["sheets"]["2026 Consolidated Statement"]
            if mutation == "missing_required_sheet":
                del book["sheets"]["Inputs and Summary"]
            elif mutation == "missing_required_cell":
                del sheet["E6"]
            elif mutation == "formula_without_cache":
                sheet["E6"] = "=5+5"
            elif mutation == "nonfinite_required_cell":
                sheet["E6"] = "NaN"
            elif mutation == "inconsistent_total":
                sheet["E19"] = 61
            elif mutation == "duplicate_year":
                book["sheets"]["Detailed Projections"]["D15"] = 2026
            else:
                self.fail("unknown mutation")
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                validate_workbook_cells(book)


class TransactionContract(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.executescript((HERE / "transaction-contract.sql").read_text(encoding="utf-8"))
        self.db.execute("INSERT INTO accounts VALUES('synthetic', 'PLAID')")
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def accept(self, operation, revision, amount, day="2026-01-02", fail=False):
        # Reference transaction algorithm to transfer into Room DAO tests in DR5-069.
        with self.db:
            if self.db.execute("SELECT 1 FROM batches WHERE operation_id=?", (operation,)).fetchone():
                return False
            old = self.db.execute("SELECT accepted_revision FROM provider_state WHERE account_id='synthetic'").fetchone()
            if old and revision <= old[0]:
                raise ValueError("stale_response")
            self.db.execute("INSERT INTO batches VALUES(?,?)", (operation, revision))
            self.db.execute("INSERT INTO snapshots(account_id,as_of_date,amount_cents,operation_id) VALUES('synthetic',?,?,?)", (day, amount, operation))
            self.db.execute("DELETE FROM holdings WHERE account_id='synthetic'")
            self.db.execute("INSERT INTO holdings VALUES('synthetic','security',?,'2.5')", (operation,))
            if fail:
                raise RuntimeError("synthetic_failure_after_partial_write")
            self.db.execute("INSERT INTO provider_state VALUES('synthetic',?,?) ON CONFLICT(account_id) DO UPDATE SET accepted_revision=excluded.accepted_revision,accepted_at=excluded.accepted_at", (revision, day))
            self.db.execute("UPDATE generation SET value=value+1 WHERE id=1")
        return True

    def state(self):
        return {table:self.db.execute(f"SELECT * FROM {table}").fetchall()
                for table in ["generation","batches","snapshots","holdings","provider_state"]}

    def test_failure_rolls_back_balances_holdings_freshness_and_generation(self):
        self.accept("first", 1, 100)
        before = self.state()
        with self.assertRaises(RuntimeError):
            self.accept("failed", 2, 200, fail=True)
        self.assertEqual(self.state(), before)

    def test_retry_does_not_duplicate_and_same_day_change_appends(self):
        self.accept("first", 1, 100)
        before = self.state()
        self.assertFalse(self.accept("first", 1, 100))
        self.assertEqual(self.state(), before)
        self.accept("second", 2, 200)
        self.assertEqual(self.db.execute("SELECT amount_cents FROM current_balances").fetchone()[0], 200)
        self.assertEqual(len(self.state()["snapshots"]), 2)

    def test_late_response_cannot_undo_new_revision(self):
        self.accept("first", 3, 300)
        before = self.state()
        with self.assertRaises(ValueError):
            self.accept("late", 2, 200)
        self.assertEqual(self.state(), before)

    def test_backdated_snapshot_preserves_latest_value(self):
        self.accept("first", 1, 200)
        self.accept("older-date", 2, 100, day="2026-01-01")
        self.assertEqual(self.db.execute("SELECT amount_cents FROM current_balances").fetchone()[0], 200)

    def test_history_is_append_only(self):
        self.accept("first", 1, 200)
        for sql in ["UPDATE snapshots SET amount_cents=0", "DELETE FROM snapshots"]:
            with self.assertRaises(sqlite3.IntegrityError):
                self.db.execute(sql)

    def test_fractional_cent_storage_rejected(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.accept("fraction", 1, 1.25)
        self.assertEqual(self.state()["generation"], [(1,0)])

    def test_forecast_publication_must_match_current_generation(self):
        self.accept("first", 1, 100)
        captured = self.state()["generation"][0][1]
        self.accept("second", 2, 200)
        # This predicate is the coordinator's required publication precondition.
        self.assertNotEqual(captured, self.state()["generation"][0][1])


class NavigationAndBackupContract(unittest.TestCase):
    def test_every_route_has_a_finite_restorable_parent_chain(self):
        routes = json.loads((HERE / "routes.json").read_text(encoding="utf-8"))
        architecture = (HERE.parent / "ARCHITECTURE.md").read_text(encoding="utf-8")
        self.assertEqual({key for key,value in routes.items() if value is None}, {"Overview","Forecast","Assets"})
        for route in routes:
            self.assertIn(route, architecture)
            stack, cursor = [], route
            while cursor is not None:
                self.assertNotIn(cursor, stack)
                stack.append(cursor)
                cursor = routes[cursor]
            saved = json.dumps(list(reversed(stack)))
            restored = json.loads(saved)
            self.assertIn(restored[0], {"Overview","Forecast","Assets"})
            while len(restored) > 1:
                self.assertEqual(routes[restored.pop()], restored[-1])
        for forbidden in ["Strategy","DataSources","RetirementSettings","PropertiesOverview","Rental"]:
            self.assertNotIn(forbidden, routes)

    def test_existing_backup_allowlist_does_not_include_financial_storage(self):
        repo = HERE.parents[3]
        expected = {("file","current-backups/latest.json"),("file","current-backups/previous.json"),
                    ("sharedpref","current-backup-status.xml")}
        for name in ["backup_rules.xml", "data_extraction_rules.xml"]:
            root = ET.parse(repo / "app/src/main/res/xml" / name).getroot()
            containers = list(root) if name == "data_extraction_rules.xml" else [root]
            for container in containers:
                self.assertEqual({(node.attrib["domain"],node.attrib["path"]) for node in container.findall("include")}, expected)


if __name__ == "__main__":
    unittest.main(verbosity=2)
