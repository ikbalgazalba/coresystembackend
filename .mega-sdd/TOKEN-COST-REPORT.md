# Token cost report (cost-weighted)

- **Raw tokens:** 30.3M (30,275,379)
- **Cost-weighted:** 8.6M (8,608,126) cost-equivalent input tokens
- **Overstatement:** raw is **3.52x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 3,941,917 | 3,941,917 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 25,918,592 | 2,591,859 |
| output_tokens | x5.00 | 414,870 | 2,074,350 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:bolt-implementer | 11 | 8,675,748 | 2,027,876 | 23.6% |
| Explore | 3 | 2,057,109 | 1,449,090 | 16.8% |
| mega-sdd:execute-bolts | 41 | 8,493,977 | 1,268,162 | 14.7% |
| mega-sdd:phase-advisor | 4 | 3,483,318 | 1,248,009 | 14.5% |
| mega-sdd:spec-reviewer | 7 | 2,374,344 | 935,087 | 10.9% |
| mega-sdd:security-reviewer | 4 | 959,262 | 424,801 | 4.9% |
| mega-sdd:detect-drift | 11 | 2,089,774 | 401,864 | 4.7% |
| mega-sdd:standards-reviewer | 2 | 716,349 | 351,183 | 4.1% |
| mega-sdd:code-quality-reviewer | 2 | 680,945 | 322,816 | 3.8% |
| mega-sdd:resolve-oq | 4 | 460,839 | 83,773 | 1.0% |
| mega-sdd:domain-extractor | 4 | 144,913 | 75,043 | 0.9% |
| mega-sdd:orchestrate-flow | 2 | 138,801 | 20,423 | 0.2% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
