# Token cost report (cost-weighted)

- **Raw tokens:** 27.2M (27,215,927)
- **Cost-weighted:** 8.3M (8,265,015) cost-equivalent input tokens
- **Overstatement:** raw is **3.29x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 3,932,711 | 3,932,711 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 22,874,240 | 2,287,424 |
| output_tokens | x5.00 | 408,976 | 2,044,880 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:bolt-implementer | 11 | 8,675,748 | 2,027,876 | 24.5% |
| Explore | 3 | 2,057,109 | 1,449,090 | 17.5% |
| mega-sdd:phase-advisor | 4 | 3,483,318 | 1,248,009 | 15.1% |
| mega-sdd:spec-reviewer | 7 | 2,374,344 | 935,087 | 11.3% |
| mega-sdd:execute-bolts | 33 | 5,434,525 | 925,051 | 11.2% |
| mega-sdd:security-reviewer | 4 | 959,262 | 424,801 | 5.1% |
| mega-sdd:detect-drift | 11 | 2,089,774 | 401,864 | 4.9% |
| mega-sdd:standards-reviewer | 2 | 716,349 | 351,183 | 4.2% |
| mega-sdd:code-quality-reviewer | 2 | 680,945 | 322,816 | 3.9% |
| mega-sdd:resolve-oq | 4 | 460,839 | 83,773 | 1.0% |
| mega-sdd:domain-extractor | 4 | 144,913 | 75,043 | 0.9% |
| mega-sdd:orchestrate-flow | 2 | 138,801 | 20,423 | 0.2% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
