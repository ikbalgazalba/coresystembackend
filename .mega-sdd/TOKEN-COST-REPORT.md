# Token cost report (cost-weighted)

- **Raw tokens:** 2.3M (2,321,376)
- **Cost-weighted:** 833.3K (833,302) cost-equivalent input tokens
- **Overstatement:** raw is **2.79x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 422,617 | 422,617 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 1,853,696 | 185,370 |
| output_tokens | x5.00 | 45,063 | 225,315 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:phase-advisor | 2 | 2,321,376 | 833,302 | 100.0% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
