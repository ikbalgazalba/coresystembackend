# Token cost report (cost-weighted)

- **Raw tokens:** 31.1M (31,090,338)
- **Cost-weighted:** 8.7M (8,699,172) cost-equivalent input tokens
- **Overstatement:** raw is **3.57x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 3,942,488 | 3,942,488 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 26,731,136 | 2,673,114 |
| output_tokens | x5.00 | 416,714 | 2,083,570 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:bolt-implementer | 11 | 8,675,748 | 2,027,876 | 23.3% |
| Explore | 3 | 2,057,109 | 1,449,090 | 16.7% |
| mega-sdd:execute-bolts | 43 | 9,308,936 | 1,359,207 | 15.6% |
| mega-sdd:phase-advisor | 4 | 3,483,318 | 1,248,009 | 14.3% |
| mega-sdd:spec-reviewer | 7 | 2,374,344 | 935,087 | 10.7% |
| mega-sdd:security-reviewer | 4 | 959,262 | 424,801 | 4.9% |
| mega-sdd:detect-drift | 11 | 2,089,774 | 401,864 | 4.6% |
| mega-sdd:standards-reviewer | 2 | 716,349 | 351,183 | 4.0% |
| mega-sdd:code-quality-reviewer | 2 | 680,945 | 322,816 | 3.7% |
| mega-sdd:resolve-oq | 4 | 460,839 | 83,773 | 1.0% |
| mega-sdd:domain-extractor | 4 | 144,913 | 75,043 | 0.9% |
| mega-sdd:orchestrate-flow | 2 | 138,801 | 20,423 | 0.2% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
