# Token cost report (cost-weighted)

- **Raw tokens:** 2.5M (2,549,267)
- **Cost-weighted:** 879.2K (879,165) cost-equivalent input tokens
- **Overstatement:** raw is **2.9x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 426,554 | 426,554 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 2,073,664 | 207,366 |
| output_tokens | x5.00 | 49,049 | 245,245 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:phase-advisor | 2 | 2,321,376 | 833,302 | 94.8% |
| mega-sdd:resolve-oq | 2 | 166,071 | 36,636 | 4.2% |
| mega-sdd:orchestrate-flow | 1 | 61,820 | 9,228 | 1.0% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
