# Token cost report (cost-weighted)

- **Raw tokens:** 152.8M (152,782,584)
- **Cost-weighted:** 26.5M (26,480,074) cost-equivalent input tokens
- **Overstatement:** raw is **5.77x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 6,816,629 | 6,816,629 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 144,931,904 | 14,493,190 |
| output_tokens | x5.00 | 1,034,051 | 5,170,255 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:bolt-implementer | 18 | 104,712,055 | 16,544,117 | 62.5% |
| mega-sdd:detect-drift | 45 | 24,192,691 | 2,659,172 | 10.0% |
| mega-sdd:execute-bolts | 49 | 12,525,464 | 2,207,854 | 8.3% |
| Explore | 4 | 2,393,603 | 1,607,797 | 6.1% |
| mega-sdd:phase-advisor | 4 | 3,483,318 | 1,248,009 | 4.7% |
| mega-sdd:spec-reviewer | 7 | 2,374,344 | 935,087 | 3.5% |
| mega-sdd:security-reviewer | 4 | 959,262 | 424,801 | 1.6% |
| mega-sdd:standards-reviewer | 2 | 716,349 | 351,183 | 1.3% |
| mega-sdd:code-quality-reviewer | 2 | 680,945 | 322,816 | 1.2% |
| mega-sdd:resolve-oq | 4 | 460,839 | 83,773 | 0.3% |
| mega-sdd:domain-extractor | 4 | 144,913 | 75,043 | 0.3% |
| mega-sdd:orchestrate-flow | 2 | 138,801 | 20,423 | 0.1% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
