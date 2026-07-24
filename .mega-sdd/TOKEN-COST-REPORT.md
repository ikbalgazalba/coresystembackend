# Token cost report (cost-weighted)

- **Raw tokens:** 20.6M (20,593,009)
- **Cost-weighted:** 6.7M (6,748,325) cost-equivalent input tokens
- **Overstatement:** raw is **3.05x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 3,440,076 | 3,440,076 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 16,827,840 | 1,682,784 |
| output_tokens | x5.00 | 325,093 | 1,625,465 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:bolt-implementer | 6 | 6,171,333 | 1,414,858 | 21.0% |
| Explore | 2 | 1,854,015 | 1,338,233 | 19.8% |
| mega-sdd:spec-reviewer | 7 | 2,374,344 | 935,087 | 13.9% |
| mega-sdd:phase-advisor | 2 | 2,321,376 | 833,302 | 12.3% |
| mega-sdd:execute-bolts | 28 | 3,691,489 | 719,223 | 10.7% |
| mega-sdd:security-reviewer | 4 | 959,262 | 424,801 | 6.3% |
| mega-sdd:standards-reviewer | 2 | 716,349 | 351,183 | 5.2% |
| mega-sdd:code-quality-reviewer | 2 | 680,945 | 322,816 | 4.8% |
| mega-sdd:detect-drift | 7 | 1,224,256 | 304,626 | 4.5% |
| mega-sdd:resolve-oq | 4 | 460,839 | 83,773 | 1.2% |
| mega-sdd:orchestrate-flow | 2 | 138,801 | 20,423 | 0.3% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
