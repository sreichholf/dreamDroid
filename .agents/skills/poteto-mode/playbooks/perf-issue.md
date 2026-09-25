# Performance issue

1. Define the user-visible metric and capture a baseline under controlled conditions.
2. Profile or instrument the actual slow path. Do not optimize from source inspection alone when measurement is possible.
3. Name the dominant cost and form one hypothesis that predicts a measurable improvement.
4. Apply the smallest change that tests the hypothesis.
5. Re-measure against the same baseline. Reject changes that merely move cost or regress another required metric.
6. Report before/after numbers, environment, traces used, and remaining bottlenecks.
