package dev.draftingroom5.retirement.forecast

import kotlin.math.*

/** Reconciles actual flows, including discontinuous ACA eligibility, before publishing success. */
internal object AnnualFunding {
    data class Assessment(val tax: Double, val credit: Double, val magi: Double, val lossCarry: Double = 0.0)
    data class Result(val withdrawal: WithdrawalPolicy.Result, val assessment: Assessment,
        val surplus: Double, val unmet: Double)

    fun solve(balances: DoubleArray, age: Int, spending: Double, income: Double, rules: List<WithdrawalRule>,
        gainRatio: Double, magiBreaks: List<Double> = emptyList(),
        assess: (WithdrawalPolicy.Result) -> Assessment): Result {
        fun at(request: Double): Result {
            val w = WithdrawalPolicy.apply(balances, age, request, rules, gainRatio)
            val a = assess(w)
            val cash = income + (request - w.unmet) + a.credit - spending - a.tax
            return Result(w, a, max(cash,0.0), max(-cash,0.0))
        }
        val zero = at(0.0)
        if (zero.unmet <= .0001) return zero
        val capacity = balances.sum() - WithdrawalPolicy.apply(balances, age, balances.sum(), rules, gainRatio).balances.sum()
        if (capacity <= 0) return zero

        // Each withdrawal bucket is linear in MAGI except the (continuous) capital-loss deduction cap.
        // Subdivide at eligibility jumps; a root below the 400% cliff must not be skipped.
        val boundaries = sortedSetOf(0.0, capacity)
        var cumulative = 0.0
        val remaining = balances.copyOf()
        for (rule in rules) if (age in rule.minAge..rule.maxAge) {
            val amount = min(remaining[rule.bucket.ordinal],rule.annualCap?.let(::dollars) ?: Double.POSITIVE_INFINITY)
            cumulative += amount; remaining[rule.bucket.ordinal] -= amount
            boundaries += cumulative.coerceAtMost(capacity)
        }
        if (magiBreaks.isNotEmpty()) {
            val segments = boundaries.toList()
            for ((start,end) in segments.zipWithNext()) {
                val startMagi = at(start).assessment.magi; val endMagi = at(end).assessment.magi
                for (target in magiBreaks) if (target > min(startMagi,endMagi) && target < max(startMagi,endMagi)) {
                    var lo=start; var hi=end
                    repeat(40) {
                        val mid=(lo+hi)/2
                        if ((at(mid).assessment.magi < target) == (startMagi < endMagi)) lo=mid else hi=mid
                    }
                    boundaries += lo; boundaries += hi
                }
            }
        }
        var best = zero
        for ((start,end) in boundaries.toList().zipWithNext()) {
            val left = at(start)
            if (left.unmet <= .0001) return left
            if (left.unmet < best.unmet) best=left
            val right=at(end)
            if (right.unmet < best.unmet) best=right
            if (right.unmet > .0001) continue
            var lo=start; var hi=end
            var loResult=left; var hiResult=right
            // Stay on the fully funded side of the root. No tolerance can hide an unpaid cent.
            for (iteration in 0 until 64) {
                if (hi-lo <= .00001 || hiResult.surplus <= .00001) break
                val fraction=loResult.unmet/(loResult.unmet+hiResult.surplus)
                // Secant interpolation is exact within a tax bracket. Periodic bisection guarantees progress.
                val mid=if (iteration % 4 == 3) (lo+hi)/2 else lo+(hi-lo)*fraction.coerceIn(.001,.999)
                val evaluated=at(mid)
                if (evaluated.unmet > 0.0) { lo=mid; loResult=evaluated } else { hi=mid; hiResult=evaluated }
            }
            return hiResult
        }
        // No fully funded solution: use the feasible withdrawal with the smallest actual shortfall.
        return best
    }
}
