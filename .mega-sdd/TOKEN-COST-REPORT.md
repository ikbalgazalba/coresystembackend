# Token cost report (cost-weighted)

- **Raw tokens:** 23.0M (22,968,476)
- **Cost-weighted:** 7.4M (7,446,170) cost-equivalent input tokens
- **Overstatement:** raw is **3.08x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 3,703,491 | 3,703,491 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 18,894,336 | 1,889,434 |
| output_tokens | x5.00 | 370,649 | 1,853,245 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| Explore | 3 | 2,057,109 | 1,449,090 | 19.5% |
| mega-sdd:bolt-implementer | 6 | 6,171,333 | 1,414,858 | 19.0% |
| mega-sdd:phase-advisor | 4 | 3,483,318 | 1,248,009 | 16.8% |
| mega-sdd:spec-reviewer | 7 | 2,374,344 | 935,087 | 12.6% |
| mega-sdd:execute-bolts | 28 | 3,691,489 | 719,223 | 9.7% |
| mega-sdd:security-reviewer | 4 | 959,262 | 424,801 | 5.7% |
| mega-sdd:detect-drift | 11 | 2,089,774 | 401,864 | 5.4% |
| mega-sdd:standards-reviewer | 2 | 716,349 | 351,183 | 4.7% |
| mega-sdd:code-quality-reviewer | 2 | 680,945 | 322,816 | 4.3% |
| mega-sdd:resolve-oq | 4 | 460,839 | 83,773 | 1.1% |
| mega-sdd:domain-extractor | 4 | 144,913 | 75,043 | 1.0% |
| mega-sdd:orchestrate-flow | 2 | 138,801 | 20,423 | 0.3% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
