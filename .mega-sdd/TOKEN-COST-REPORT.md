# Token cost report (cost-weighted)

- **Raw tokens:** 14.2M (14,247,954)
- **Cost-weighted:** 3.6M (3,580,168) cost-equivalent input tokens
- **Overstatement:** raw is **3.98x** the real cost (cache_read bills 0.1x; output 5x). Judge spend by the cost-weighted number.

| Token type | weight | raw | cost-weighted |
|---|---:|---:|---:|
| input_tokens | x1.00 | 1,354,539 | 1,354,539 |
| cache_creation_input_tokens | x1.25 | 0 | 0 |
| cache_read_input_tokens | x0.10 | 12,702,336 | 1,270,234 |
| output_tokens | x5.00 | 191,079 | 955,395 |

## By skill (cost-weighted, descending)

| Skill | turns | raw | cost-weighted | % of cost |
|---|---:|---:|---:|---:|
| mega-sdd:bolt-implementer | 6 | 6,171,333 | 1,414,858 | 39.5% |
| mega-sdd:phase-advisor | 2 | 2,321,376 | 833,302 | 23.3% |
| mega-sdd:spec-reviewer | 6 | 1,744,570 | 555,698 | 15.5% |
| mega-sdd:execute-bolts | 22 | 2,866,153 | 411,493 | 11.5% |
| mega-sdd:security-reviewer | 3 | 371,116 | 156,210 | 4.4% |
| mega-sdd:standards-reviewer | 1 | 242,779 | 80,900 | 2.3% |
| mega-sdd:code-quality-reviewer | 1 | 225,755 | 70,648 | 2.0% |
| mega-sdd:resolve-oq | 2 | 166,071 | 36,636 | 1.0% |
| mega-sdd:orchestrate-flow | 2 | 138,801 | 20,423 | 0.6% |

> Cost weights are Opus price ratios relative to 1 uncached input token (input x1, cache_creation x1.25, cache_read x0.1, output x5). The cost-weighted total is a price-faithful unit, not a raw count.
