# Token cost report (cost-weighted)

- **Raw tokens:** 19.9M (19,856,196)
- **Cost-weighted:** 6.4M (6,410,854) cost-equivalent input tokens
- **Overstatement:** raw is **3.1x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 3,282,842 | 3,282,842 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 16,273,216 | 1,627,322 |
| output_tokens | x5.00 | 300,138 | 1,500,690 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:bolt-implementer | 6 | 6,171,333 | 1,414,858 | 22.1% |
| Explore | 1 | 1,485,777 | 1,060,150 | 16.5% |
| mega-sdd:spec-reviewer | 7 | 2,374,344 | 935,087 | 14.6% |
| mega-sdd:phase-advisor | 2 | 2,321,376 | 833,302 | 13.0% |
| mega-sdd:execute-bolts | 28 | 3,691,489 | 719,223 | 11.2% |
| mega-sdd:security-reviewer | 4 | 959,262 | 424,801 | 6.6% |
| mega-sdd:standards-reviewer | 2 | 716,349 | 351,183 | 5.5% |
| mega-sdd:code-quality-reviewer | 2 | 680,945 | 322,816 | 5.0% |
| mega-sdd:detect-drift | 6 | 1,150,449 | 292,375 | 4.6% |
| mega-sdd:resolve-oq | 2 | 166,071 | 36,636 | 0.6% |
| mega-sdd:orchestrate-flow | 2 | 138,801 | 20,423 | 0.3% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
