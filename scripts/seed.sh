#!/usr/bin/env bash
# Loads a demo data set with realistic, multi-paragraph documents. Usage: scripts/seed.sh [base-url]
# Expects an empty database: the demo clients' emails must not exist yet.
set -euo pipefail
BASE="${1:-http://localhost:8080}"

post() { curl -sS -f -X POST "$BASE$1" -H 'Content-Type: application/json' -d "$2"; }
id_of() { sed -E 's/.*"id":"([^"]+)".*/\1/' | head -1; }
# JSON string escaping for plain text read from stdin: backslashes, quotes and line breaks
json_text() { sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | awk 'NR > 1 { printf "\\n" } { printf "%s", $0 }'; }
# add_document <client-id> <title>, content on stdin
add_document() {
  post "/clients/$1/documents" "{\"title\":\"$2\",\"content\":\"$(json_text)\"}" > /dev/null
}

john=$(post /clients '{"first_name":"John","last_name":"Doe","email":"john.doe@neviswealth.com","description":"Retired teacher, cautious investor interested in ESG funds","social_links":["https://linkedin.com/in/johndoe"]}' | id_of)
maria=$(post /clients '{"first_name":"Maria","last_name":"Garcia","email":"maria.garcia@example.com","description":"Entrepreneur who recently sold her software company"}' | id_of)

add_document "$john" "Scan 2024-03-14" <<'TEXT'
EDF Energy - Your electricity and gas bill
Bill date: 14 March 2024. Account number: 6719 2204 581. Billing period: 12 February 2024 to 11 March 2024.

Account holder: Mr John Doe
Supply address: 12 Baker Street, London NW1 6XE

Utility bill summary
Balance brought forward from your last bill: 0.00 GBP
Electricity used: 212 kWh at 24.50p per kWh = 51.94 GBP, plus a standing charge of 29 days at 60.10p = 17.43 GBP.
Gas used: 389 kWh at 6.04p per kWh = 23.50 GBP, plus a standing charge of 29 days at 31.43p = 9.11 GBP.
Subtotal: 101.98 GBP. VAT at 5 percent: 5.10 GBP. Direct Debit discount: -22.88 GBP.
Total amount due: 84.20 GBP, to be collected by Direct Debit on or after 28 March 2024.

Meter readings: electricity meter E14A 038712 read on 11 March 2024 (actual reading 41,288); gas meter G4 1129887 read on 11 March 2024 (estimated reading 7,914). If the estimated reading looks wrong, submit your own reading in the app so we can correct the bill.

Your tariff is EDF Simply Fixed until 30 November 2024. There is no exit fee if you switch in the last 49 days of the fix. Over the last 12 months you used 2,710 kWh of electricity and 9,840 kWh of gas, about 6 percent less than the same period a year earlier.
TEXT

add_document "$john" "Passport" <<'TEXT'
Certified copy of the passport of John Doe, received at onboarding on 2 February 2024.

United Kingdom of Great Britain and Northern Ireland - Passport
Surname: DOE. Given names: JOHN MICHAEL. Nationality: British Citizen.
Date of birth: 17 August 1964. Place of birth: Manchester. Sex: M.
Passport number: 533401234. Date of issue: 12 May 2019. Date of expiry: 12 May 2029. Authority: HMPO.

The copy was certified by solicitor Anna Patel of Patel and Co LLP on 30 January 2024, who confirmed that the photograph is a true likeness of the client. The photograph page is clear and complete; the machine readable zone was checked and matches the printed details. The document remains valid for more than six months, so no renewal reminder is needed before the next periodic review.
TEXT

add_document "$john" "Annual review 2024" <<'TEXT'
Annual review meeting with John Doe, held on 3 May 2024 at the London office. Present: John Doe, adviser Sarah Mills. Duration: 75 minutes.

Portfolio performance. The portfolio was valued at 642,300 pounds on 30 April 2024, up from 598,900 pounds a year earlier. After fees, it returned 7.2 percent over the year, ahead of its benchmark (6.1 percent). The global equity funds drove most of the gain; the UK gilt fund was flat. Fees for the year totalled 0.82 percent of assets, in line with the agreed charging structure.

Asset allocation. Because John intends to stop working at 60, in three years, we agreed to reduce the equity allocation from 70 to 60 percent and move the difference into short-dated bonds and a cash buffer. The rebalance will be done over two months to spread the trading. John asked that the ESG screen stays in place; all replacement funds have been chosen from the approved sustainable fund list.

Retirement plan. John wants to live off his pension savings and a modest drawdown from the ISA. His current pension pot is 410,000 pounds. He will increase his monthly pension contribution from 600 to 800 pounds from June, using the remaining annual allowance. We modelled retirement income of 32,000 pounds a year from age 60; under the cautious scenario this remains sustainable until age 95. The State Pension is expected from age 67 and has not been included in the plan.

Protection and estate. John has no life cover since his mortgage was repaid in 2022. He will review whether he needs any, given that his daughter is financially independent. His will dates from 2015 and should be updated to reflect the sale of the holiday flat; John will contact his solicitor.

Actions. Adviser: rebalance the portfolio by 30 June 2024; send the updated retirement projection by 17 May 2024. Client: set up the higher pension contribution; book a meeting with the solicitor about the will. Next annual review: May 2025.
TEXT

add_document "$maria" "Tax return 2023" <<'TEXT'
Self assessment tax return, tax year 6 April 2022 to 5 April 2023, for Maria Garcia. Filed online with HMRC on 15 January 2024 by Clarke Accountancy Ltd on her behalf. UTR: 48201 77394.

Employment and director income. Salary from Garcia Software Ltd until the sale: 48,500 pounds, with 9,382 pounds of tax deducted under PAYE. Dividends from Garcia Software Ltd before completion: 120,000 pounds.

Capital gains. On 14 October 2022 Maria sold her entire shareholding in Garcia Software Ltd to Northwind Holdings plc for 1,350,000 pounds. Her base cost was 150,000 pounds, giving a gain of 1,200,000 pounds. Business Asset Disposal Relief was claimed on the first 1,000,000 pounds of lifetime gains, taxed at 10 percent; the remaining 200,000 pounds is taxed at 20 percent. Capital gains tax due: 140,000 pounds.

Other income. Bank interest of 3,210 pounds and rental income from one buy-to-let flat of 14,400 pounds, less allowable expenses of 3,950 pounds.

Summary of the calculation. Total income tax and capital gains tax due for the year: 228,000 pounds, after tax already deducted at source. A balancing payment of 211,400 pounds was due by 31 January 2024 and has been paid. No payments on account are required for 2023-24 because her income from the business has stopped, but the rental and investment income should be reviewed next year in case they are needed again.
TEXT

add_document "$maria" "Risk questionnaire" <<'TEXT'
Investment risk questionnaire and suitability assessment for Maria Garcia, completed on 20 February 2024 with adviser Tom Reid.

Objectives. Maria wants to grow the proceeds of her business sale over the long term and does not need regular income from the portfolio: her rental income and part-time consulting cover her living costs. She may want to draw a lump sum of about 200,000 pounds within five years to buy a second property, which will be kept in a separate cash and short-dated bond pot.

Time horizon. More than 15 years for the main portfolio.

Attitude to risk. Maria scored 8 out of 10 on the questionnaire, which corresponds to an adventurous risk profile. She said she could tolerate a fall of up to 25 percent in a single year without selling, and she described her reaction to the 2020 market fall as "an opportunity to buy". She understands that higher expected returns come with larger and longer falls in value.

Capacity for loss. High. Her essential spending is covered by income, she holds an emergency fund of 60,000 pounds in cash, and she has no debts.

Knowledge and experience. Maria has invested in shares and funds for over ten years and has experience of private company equity through her own business.

Recommendation. A portfolio of 90 percent global equities and 10 percent bonds is suitable for the main pot, reviewed annually or when her circumstances change, such as the property purchase. Concentration in technology stocks should be limited given her existing exposure to the sector through her career.
TEXT

echo "Seeded clients: John Doe ($john), Maria Garcia ($maria)"
