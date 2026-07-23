# Token cost report (cost-weighted)

- **Raw tokens:** 19.1M (19,058,838)
- **Cost-weighted:** 6.3M (6,313,840) cost-equivalent input tokens
- **Overstatement:** raw is **3.02x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 3,276,678 | 3,276,678 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 15,484,416 | 1,548,442 |
| output_tokens | x5.00 | 297,744 | 1,488,720 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:bolt-implementer | 6 | 6,171,333 | 1,414,858 | 22.4% |
| Explore | 1 | 1,485,777 | 1,060,150 | 16.8% |
| mega-sdd:spec-reviewer | 7 | 2,374,344 | 935,087 | 14.8% |
| mega-sdd:phase-advisor | 2 | 2,321,376 | 833,302 | 13.2% |
| mega-sdd:execute-bolts | 28 | 3,691,489 | 719,223 | 11.4% |
| mega-sdd:security-reviewer | 4 | 959,262 | 424,801 | 6.7% |
| mega-sdd:standards-reviewer | 2 | 716,349 | 351,183 | 5.6% |
| mega-sdd:code-quality-reviewer | 2 | 680,945 | 322,816 | 5.1% |
| mega-sdd:detect-drift | 2 | 353,091 | 195,361 | 3.1% |
| mega-sdd:resolve-oq | 2 | 166,071 | 36,636 | 0.6% |
| mega-sdd:orchestrate-flow | 2 | 138,801 | 20,423 | 0.3% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
