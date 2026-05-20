# Java Factorial Error Debugging Findings

## Summary

The Java project failed unit tests while calculating factorials. The failure was caused by a recursive method that never stops calling itself, leading to a `StackOverflowError`.

## Key findings

- The failing tests were in `src/test/java/com/example/recursion/FactorialCalculatorTest.java`.
- The root cause was in `src/main/java/com/example/recursion/FactorialCalculator.java`.
- The failing line is the recursive call inside `calculateFactorial(int num)`.

## Relevant stack trace locations

- `FactorialCalculator.java:32` — this is the implementation line where recursion occurs.
- `FactorialCalculatorTest.java:20` — failed `factorialOfZeroShouldBeOne()`
- `FactorialCalculatorTest.java:33` — failed `factorialOfOneShouldBeOne()`
- `FactorialCalculatorTest.java:46` — failed `factorialOfFiveShouldBe120()`
- `FactorialCalculatorTest.java:59` — failed `factorialOfTenShouldBe3628800()`
- `FactorialCalculatorTest.java:70` — failed `factorialOfNegativeNumberShouldThrowException()`

## Likely causes

1. Missing base case in recursion.
2. Recursion never terminates because the function always calls itself.
3. Negative input is not validated, allowing invalid recursive paths.

## What to check in code

- Ensure `calculateFactorial` returns `1` for `0` or `1` before recursing.
- Add input validation for negative numbers and throw `IllegalArgumentException`.
- Confirm the recursive step uses `num - 1` only after the base case is handled.

## Debugging steps

1. Open `src/main/java/com/example/recursion/FactorialCalculator.java`.
2. Verify the recursive method has a base case:
   - `if (num == 0) return 1;`
   - or `if (num <= 1) return 1;`
3. Add a check for invalid negative values:
   - `if (num < 0) throw new IllegalArgumentException("Negative input not allowed");`
4. Confirm the recursive return is only reached when `num > 1`.
5. Re-run tests with `./gradlew test`.

## Explanation of `StackOverflowError`

A `StackOverflowError` happens when a method keeps calling itself without stopping. Each recursive call uses stack memory, and if the function never reaches a return condition, Java runs out of stack space.

## Recommendation

Fix `calculateFactorial` so that it has both:

- a stopping condition for `0` and `1`
- a negative-input guard clause

This should resolve the failing tests and prevent the stack overflow.
