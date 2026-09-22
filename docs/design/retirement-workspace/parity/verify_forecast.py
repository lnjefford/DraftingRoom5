"""DR5-074 offline full-path golden adapter. Synthetic inputs only; no app startup.

--write initially records reviewed goldens; ordinary validation never rewrites them.
The fixed asset tape is NumPy's seeded MVN output, then shared verbatim with Kotlin.
"""
import argparse
import json
from pathlib import Path
import sys
from unittest.mock import patch
from verify_reference import install_guard, source_hashes, deny, cents

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--finance", type=Path, required=True)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    root = args.finance.resolve()
    assert source_hashes(root) == json.loads((HERE / "source-manifest.json").read_text())
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
    from app.sim import engine as e
    from app.models.accounts import AccountType, TaxTreatment
    from app.models.settings import FilingStatus
    from app.sim.withdrawal_strategy import WithdrawalStrategy, WithdrawalRule
    from app.sim.reference import real_mean_vector, real_stdev_vector
    from app.tax.federal import calculate_federal
    from app.tax.wisconsin import calculate_wisconsin
    from app.tax import IncomeBreakdown

    cases = json.loads((HERE / "forecast-cases.json").read_text())
    expected = {"cases": [], "tax_grid": []}
    types = [AccountType.IRA_TRADITIONAL, AccountType.IRA_ROTH, AccountType.HSA, AccountType.BROKERAGE_TAXABLE]
    taxes = [TaxTreatment.TRADITIONAL, TaxTreatment.ROTH, TaxTreatment.HSA, TaxTreatment.TAXABLE]
    for case in cases:
        accounts = [e.AccountSeed("Synthetic", types[a["bucket"]], taxes[a["bucket"]], a["cents"] / 100,
                    np.array(a["allocation"]), a["basis_cents"] / 100) for a in case["accounts"]]
        inputs = e.ProjectionInputs(current_age=case["age"], retirement_age=case["retire"], plan_end_age=case["end"],
            target_real_spending=case["spending_cents"] / 100, accounts=accounts,
            contrib_traditional=case["contributions"][0] / 100, contrib_roth=case["contributions"][1] / 100,
            contrib_hsa=case["contributions"][2] / 100, contrib_taxable=case["contributions"][3] / 100,
            income_streams=[e.IncomeStream("Synthetic", s["cents"] / 100, s["start"], s["end"], s["kind"]) for s in case["incomes"]],
            real_estate_positions=[e.RealEstatePosition("Synthetic", p["value"] / 100, p["mortgage"] / 100,
                p["appreciation_bps"] / 10000, p["rate_bps"], p["payment"] / 100, p["months"], p["ownership_bps"] / 100,
                p["low"] / 100 if "low" in p else None, p["high"] / 100 if "high" in p else None) for p in case["properties"]],
            epic_annual_values=[e.EpicAnnualValue(row[0], row[1] / 100, row[2] / 100) for row in case["epic"]],
            base_calendar_year=2026, inflation_rate=case["inflation_bps"] / 10000,
            epic_stock_volatility=case.get("epic_volatility", 0),
            filing_status=FilingStatus(case["filing"]), state=case["state"],
            aca_household_size=case["household"], aca_benchmark_premium_real=case["premium"] / 100,
            aca_extended_subsidy=case["extended"], liquidate_real_estate_at_retirement=case["sell_home"],
            withdrawal_strategy=WithdrawalStrategy.default_retire_at_50(case["medical"] / 100))
        if case["paths"] == 1:
            result = e.run_deterministic(inputs)
            tape = []
            epic_noise = []
            property_noise = []
        else:
            years = case["end"] - case["age"]
            noise_rng = np.random.default_rng(case["seed"] + 74)
            epic_noise = noise_rng.normal(size=(case["paths"], years))
            property_noise = noise_rng.normal(size=(len(case["properties"]), case["paths"]))
            normal_calls = []
            if case.get("epic_volatility", 0):
                normal_calls.extend(epic_noise[:, y] for y in range(years))
            normal_calls.extend(property_noise[index] for index, p in enumerate(case["properties"]) if "low" in p)
            tape_array = np.random.default_rng(case["seed"]).multivariate_normal(real_mean_vector(),
                e._build_full_covariance(real_stdev_vector()), size=(case["paths"], case["end"] - case["age"]))
            class Fixed:
                def normal(self, mean, sigma, size):
                    assert size == case["paths"]
                    return mean + sigma * normal_calls.pop(0)
                def multivariate_normal(self, mean, covariance, size):
                    assert size == (case["paths"], case["end"] - case["age"])
                    return tape_array.copy()
            with patch.object(e.np.random, "default_rng", return_value=Fixed()):
                result = e.run_monte_carlo(inputs, case["paths"], case["seed"])
            tape = tape_array.flatten().tolist()
            epic_noise = epic_noise.flatten().tolist()
            property_noise = property_noise.flatten().tolist()
        def rounded(array):
            return np.vectorize(cents)(array).tolist()
        expected["cases"].append(dict(name=case["name"], tape=tape, epic_noise=epic_noise, property_noise=property_noise,
            buckets=rounded(result.balances_by_bucket), totals=rounded(result.total_by_path),
            unmet=rounded(result.unmet_spending_by_year),
            epic=rounded(result.epic_balance_by_year) if result.epic_balance_by_year is not None else None,
            property=rounded(result.real_estate_equity_by_year) if result.real_estate_equity_by_year is not None else None,
            success=result.success_rate, depletion=result.depletion_ages.tolist(),
            p10=rounded(result.percentile(10)), p50=rounded(result.percentile(50)), p90=rounded(result.percentile(90))))
    rng = np.random.default_rng(74074)
    for filing in FilingStatus:
        for ordinary, gain, benefits in [(0, 0, 30000), (25000, 10000, 24000), (50000, 0, 30000)] + rng.uniform(0, 800000, (25, 3)).tolist():
            income = IncomeBreakdown(ordinary=ordinary, ltcg=gain)
            expected["tax_grid"].append(dict(filing=filing.value, ordinary=ordinary, gain=gain, benefits=benefits,
                federal=cents(calculate_federal(income, filing)), wi=cents(calculate_wisconsin(income, filing)),
                ss=cents(float(e._taxable_social_security(np.array([ordinary]), np.array([gain]), benefits, filing)[0]))))
    destination = HERE / "forecast-expected.json"
    if args.write:
        destination.write_text(json.dumps(expected, separators=(",", ":")) + "\n", encoding="utf-8")
    else:
        assert expected == json.loads(destination.read_text()), "Frozen full-engine reference results changed"
    print(f"PASS: {len(cases)} full-path reference cases, shared seeded tapes, 112 tax/SS grid rows; private-data/network guard enabled")


if __name__ == "__main__":
    main()
