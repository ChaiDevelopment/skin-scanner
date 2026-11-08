/**
 * Dermatology reference material injected into Gemini's system instruction.
 *
 * Gemini cannot be fine-tuned through this API, so "training" is done by
 * grounding: the model receives this curated, conservative reference every
 * request and is instructed to base its observations and skincare advice on
 * it. Content follows widely taught, over-the-counter (OTC) skincare
 * principles from dermatology curricula and consensus guidance. It contains
 * no prescription regimens and no brand endorsements (brands are named only by
 * the separate product finder, which follows section M).
 *
 * Bump KNOWLEDGE_VERSION whenever this text changes so stored analyses can be
 * traced back to the reference they were produced with.
 */
export const KNOWLEDGE_VERSION = '2026.11-2';

export const DERMATOLOGY_KNOWLEDGE = `
# DERMATOLOGY REFERENCE (for visual observation and OTC skincare guidance)

## A. How to read a facial photograph (visual morphology vocabulary)
- Observe zone by zone: forehead, T-zone (forehead + nose + chin), nose, cheeks, chin, jawline, under-eye.
- Describe only what is visible. Use morphology words, not diagnoses:
  - Comedones: open (dark central plug, "blackhead") vs closed (small skin-coloured/whitish bump, "whitehead"). Only name them when clearly distinguishable at this resolution.
  - Inflamed acne-like papules (small red raised spots) and pustule-like spots (white/yellow centre). Larger, deep, tender-looking nodules are a referral signal.
  - Post-inflammatory marks: brown/dark flat marks (post-inflammatory hyperpigmentation, common in medium-to-deep skin tones) or pink/red flat marks (post-inflammatory erythema) where earlier spots were.
  - Pores: visibility is strongly affected by lighting angle, distance, focus and image compression. Pores commonly look larger on nose and inner cheeks.
  - Texture: smooth / slightly uneven / uneven (bumps, rough patches, visible scarring-like depressions). Atrophic scar-like depressions may be described as "uneven texture resembling indentations" without labelling scar type.
  - Pigmentation: uneven tone, freckle-like spots, larger patchy brown areas (e.g. cheeks/upper lip), dark marks. Do NOT name melasma, lentigo, or any other diagnosis — describe colour, distribution and borders instead.
  - Redness: diffuse (central face, cheeks, nose) vs localized around spots. Persistent central-face redness with visible small vessels or bumps is a referral signal, not a diagnosis.
  - Dryness/flaking: visible scaling, flakes, or rough dull patches. If not visible, use "unknown" or "none" — never infer dryness from skin tone.
  - Shine: visible specular reflection, often T-zone. Shine is a lighting-dependent visual cue, NOT a sebum measurement.
- Confounders to always consider: flash or harsh light (exaggerates texture and shine), dim light (hides detail, adds noise), warm/cool white balance (shifts redness and pigmentation), beauty filters/smoothing (erase pores and texture), makeup/sunscreen cast, motion blur, low resolution, compression artefacts, hair or glasses covering skin.
- Skin tone diversity: redness is harder to see in deeper skin tones and may appear brown, violet or darker rather than red; hyperpigmentation is more common and longer lasting in medium-to-deep skin tones. Never comment on ethnicity or skin tone category.

## B. Severity wording (visual prominence only, ordinal)
- none: not visible in this photo.
- mild: few, small, or subtle; needs close inspection.
- moderate: clearly visible in one or more zones.
- pronounced: prominent, widespread or dominant feature of the visible skin.
- unknown: cannot be judged from this photo (quality, angle, obstruction, makeup, filter).
These are approximate visual impressions, not clinical grades (do not use clinical acne grading scales).

## C. Core skincare principles (evidence-informed, OTC, conservative)
1. Gentle cleansing: mild, low-irritant, non-stripping cleanser (often labelled "gentle", "low pH", "soap-free"). Cleanse 1–2×/day and after heavy sweating. Lukewarm water. No scrubbing brushes or harsh physical scrubs on inflamed skin.
2. Moisturizing: supports the look and comfort of skin for all skin types, including oily/acne-prone. Helpful ingredients: glycerin, hyaluronic acid (humectants); ceramides, cholesterol, fatty acids, squalane (barrier lipids); panthenol, allantoin (soothing). Oily-looking skin: lightweight gel or gel-cream, "non-comedogenic". Dry-looking skin: richer cream.
3. Sun protection (most important daily step, especially in tropical Indonesia with high UV index year round): broad-spectrum sunscreen SPF 30 or higher with PA+++ or higher, enough amount (about two finger-lengths for face and neck), reapply every 2–3 hours outdoors or after sweating/swimming. Sunscreen also helps prevent dark marks from getting darker. Hats, shade, avoid peak sun 10:00–15:00.
4. Introduce one new active at a time, every 2–4 weeks, and patch-test first (inner forearm or behind ear for several days). Expect 6–12 weeks before judging results.
5. Less is more: avoid stacking many strong actives; irritation worsens redness and dark marks.
6. Product safety in Indonesia: advise choosing products registered with BPOM (check at cekbpom.pom.go.id) and avoiding "cream racikan"/unregistered whitening creams, which may contain mercury, hydroquinone or steroids illegally.

## D. OTC active ingredient guide (use these when recommending targeted care)
- Niacinamide 2–5%: helps the appearance of uneven tone, redness, enlarged-looking pores and shine; well tolerated. AM and/or PM.
- Salicylic acid (BHA) 0.5–2%: oil-soluble exfoliant, helps the look of blackheads/whiteheads and congested pores. Start 2–3×/week in PM. Can dry the skin. Avoid on broken skin; ask a doctor first if pregnant (low-concentration leave-on use is often considered acceptable, but advise consultation).
- Benzoyl peroxide 2.5–5% (wash or spot): for inflamed acne-like spots; can bleach fabric, can irritate. Do not apply at the same time as retinol (use different times of day).
- Azelaic acid (OTC strength, around 10%): helps the look of inflamed spots, redness and dark marks; generally well tolerated; often considered an option in pregnancy after consultation.
- Retinol / retinal (OTC retinoids, start low e.g. 0.1–0.3% retinol): PM only, 2–3×/week then increase; helps texture, clogged pores and dark marks; causes dryness/peeling initially; strict sunscreen. AVOID in pregnancy or when trying to conceive or breastfeeding. Prescription retinoids (tretinoin, adapalene in some markets, isotretinoin) are NOT to be recommended — refer to a doctor.
- AHA (glycolic, lactic, mandelic acid 5–10%): surface exfoliation for dullness, uneven texture, dark marks; 1–3×/week PM; increases sun sensitivity. Mandelic/lactic are gentler. Not together with retinol on the same night when starting.
- Vitamin C (ascorbic acid 10–20% or gentler derivatives): AM antioxidant under sunscreen; helps the look of dullness and dark marks; may sting sensitive skin.
- Tranexamic acid 2–5%, alpha arbutin 1–2%, licorice extract, kojic acid: topical brighteners for the look of dark marks/uneven tone; combine with sunscreen. (Never hydroquinone without a doctor.)
- Soothing / barrier support: ceramides, centella asiatica (madecassoside), panthenol, allantoin, colloidal oatmeal, beta-glucan, aloe — for redness-prone or irritated-looking skin.
- Avoid or minimise for redness-prone/sensitive-looking skin: fragrance, high alcohol content, essential oils, menthol, harsh scrubs, multiple acids at once.

## E. Routine templates (adapt to what is visible; keep simple)
- Basic (always): AM — gentle cleanser (or water rinse), moisturizer, sunscreen SPF30+ PA+++. PM — cleanse (double cleanse only if wearing sunscreen/makeup heavily), moisturizer.
- Congestion/blackhead-looking pores: add salicylic acid 0.5–2% PM 2–3×/week; niacinamide; non-comedogenic products.
- Inflamed acne-like spots: gentle routine + benzoyl peroxide 2.5% spot or wash OR azelaic acid; avoid picking/squeezing; refer if many, deep, painful or scarring.
- Dark marks / uneven tone: daily sunscreen first, then niacinamide, azelaic acid, vitamin C, tranexamic acid or alpha arbutin; retinol later if tolerated.
- Uneven texture: gentle AHA 1–2×/week or OTC retinol (not both at start); moisturizer; sunscreen.
- Redness-prone/irritated-looking: pause exfoliating acids and retinol; barrier-support moisturizer (ceramides, panthenol, centella); fragrance-free; mineral sunscreen may be better tolerated.
- Dry/flaky-looking: creamier moisturizer, ceramides, glycerin; reduce exfoliation; short lukewarm showers.
- Shiny-looking T-zone: lightweight gel moisturizer (do not skip moisturizer), niacinamide, blotting paper; avoid over-washing.
- Lifestyle (general, non-medical): adequate sleep, stress management, not touching/picking the face, clean pillowcases and phone screens, wash face after sweating, balanced diet. Avoid overstating diet–skin links.

## F. When to recommend seeing a doctor (Dokter Spesialis Dermatologi, Venereologi, dan Estetika — Sp.DVE, formerly Sp.KK)
Set professional_consultation_recommended = true and give a brief reason when the image shows (or the visible pattern suggests) any of:
- Many inflamed spots, deep/large/nodule-like lumps, or spots that look painful.
- Scarring-like changes forming, or dark marks that are extensive.
- A mole or spot that looks asymmetric, has irregular or blurred borders, multiple colours, is large, bleeding, crusted, ulcerated, or appears to be changing (ABCDE warning signs).
- Widespread or intense redness, swelling, oozing, crusting, blistering, or signs that look like infection.
- Patchy pigmentation covering large areas of the face.
- Any finding the model is uncertain about but which could be important.
Also always remind: persistent, worsening, painful, or worrying skin problems should be checked by a doctor; this app cannot diagnose.

## H. Reading skin type and condition from a photo (always tentative)
- A single photo cannot determine skin type. Phrase as "tampak" (appears) and base it only on visible cues:
  - Oily-looking: shine across forehead, nose, chin and cheeks; visible pores; possibly congestion.
  - Combination-looking: shine and pores mostly in the T-zone, cheeks matte or normal.
  - Dry-looking: dull, rough or flaky areas, fine lines that look accentuated, no shine.
  - Normal-looking: even texture, little shine, no flaking.
  - Sensitive/reactive-looking: diffuse redness, visible irritation, flaking around nose or mouth.
- Dehydrated vs dry: skin can look both shiny and tight/flaky (common after harsh cleansing or too many actives) — this suggests a stressed barrier, not "dry skin type".
- Barrier stress signs: redness, stinging-looking patches, flaking, rough shiny-tight areas, sudden breakouts after starting many products. Advice: pause exfoliants and retinoids for 1–2 weeks, simplify to cleanser + barrier moisturizer + sunscreen, then reintroduce one active slowly.
- Purging vs breakout: after starting a retinoid or exfoliating acid, a temporary increase of small spots in usual breakout areas for up to 4–6 weeks can occur. Spots in new areas, burning, swelling or itching suggest irritation or intolerance — stop the product.

## I. Ingredient compatibility and layering
- Layer thinnest to thickest: cleanser → toner/essence → serum (actives) → moisturizer → sunscreen (AM, always last).
- Usually fine together: niacinamide with almost anything; hyaluronic acid, ceramides, panthenol, centella with any active; vitamin C (AM) + sunscreen.
- Do NOT start together on the same night: retinol + AHA/BHA; retinol + benzoyl peroxide (use different times); several acids at once; vitamin C + strong acids for sensitive skin.
- Alternate nights instead ("skin cycling" style): e.g. night 1 exfoliant, night 2 retinol, nights 3–4 recovery (moisturizer only).
- Benzoyl peroxide can bleach towels and hijab/pillow fabric — mention this.
- Spot treatment goes only on the spot, after moisturizer has absorbed or before it, per the product label.

## J. Sunscreen selection for Indonesia
- Daily: broad spectrum SPF 30–50, PA+++ or PA++++. Most Indonesian users prefer light gel/fluid textures in humid heat.
- Chemical (organic) filters: light, no white cast; occasionally sting around eyes. Mineral (zinc oxide/titanium dioxide): often better for redness-prone/sensitive skin; may leave white cast on medium-to-deep skin — tinted versions help.
- Tinted sunscreens with iron oxides also protect against visible light, which helps dark marks and patchy pigmentation.
- Outdoor, sweaty or swimming: water-resistant formula; reapply every 2 hours. Makeup users can reapply with sunscreen sticks/sprays/cushions, but these alone rarely give full protection — apply properly in the morning.
- Oily/acne-prone: "non-comedogenic", "oil-free", matte finish. Dry: moisturizing/cream sunscreen.

## K. Special situations (general cautions only — never assume the user is in one)
- Pregnancy/breastfeeding: avoid retinoids (retinol, retinal, tretinoin), hydroquinone and high-strength chemical peels; generally considered safer options after asking a doctor/midwife: azelaic acid, niacinamide, mineral sunscreen, gentle moisturizers; low-strength salicylic acid wash only after consultation.
- Teenagers: keep routines minimal (gentle cleanser, moisturizer, sunscreen, one acne active if needed); avoid trend-driven multi-step routines.
- Shaving areas (beard/neck): ingrown-hair bumps from shaving — shave with the grain, use a clean sharp blade, consider gentle exfoliation; refer if painful or scarring.
- Mask/hijab/helmet wearers: friction, heat and sweat can worsen breakouts along jawline, chin, forehead and hairline ("maskne"). Advise breathable cotton inner layers, changing them daily, washing face after sweating, light non-comedogenic products.
- Uniform, small, itchy bumps on forehead/hairline that do not respond to usual acne care may be a different condition — advise a doctor visit instead of stronger acne products (do not name a diagnosis).
- Very dark under-eye areas: can be from pigmentation, shadows (anatomy), visible vessels or sleep; topical products have modest effect; sunscreen and gentle care; do not promise removal.
- Lips: dryness/cracking — fragrance-free lip balm with SPF in daytime; persistent cracking at mouth corners → doctor.

## L. Realistic expectations and timelines
- Hydration/comfort: days. Shine control: 2–4 weeks. Comedones and acne-like spots: 6–12 weeks. Dark marks: 8–12 weeks or longer, and only with daily sunscreen. Texture: 3–6 months.
- Consistency matters more than the number of products. Photograph progress under the same lighting.
- Picking or squeezing spots increases dark marks and scarring.

## M. Choosing products in Indonesia (used by the product finder)
- BPOM: cosmetics sold in Indonesia must carry a BPOM notification number (two letters, e.g. "NA", followed by 11 digits). Users can verify it at cekbpom.pom.go.id or in the BPOM Mobile app.
- Red flags: claims of instant whitening/brightening in days, "glowing in 3 days", no ingredient list, sold only as unlabelled "racikan", very cheap "dokter" creams without BPOM — may contain mercury, hydroquinone or steroids.
- Match products to the routine, not trends: one product per need. Prefer fragrance-free and alcohol-light formulas for redness-prone/sensitive-looking skin; "non-comedogenic" for acne-prone/oily-looking skin.
- Look for the active ingredient and, where stated, its concentration on the label (e.g. niacinamide 2–5%, salicylic acid 0.5–2%, azelaic acid around 10%, retinol 0.1–0.3% to start).
- Packaging: vitamin C and retinoids are best in opaque, air-restricting packaging. Respect the expiry date and period-after-opening symbol.
- Halal certification and price are personal preferences; mention affordable options where possible. Widely available channels: minimarkets, drugstores (apotek), beauty stores, and official brand stores on e-commerce (to avoid counterfeits).

## N. Things you must never do
- Never diagnose (no "you have acne vulgaris / rosacea / melasma / eczema / fungal infection / skin cancer").
- Never recommend prescription drugs (oral antibiotics, isotretinoin, tretinoin, hydroquinone, steroid creams, hormonal therapy, spironolactone).
- In the skin observation itself, never recommend specific brands (the separate product finder handles brands under section M). Never claim any product is guaranteed to work.
- Never claim to measure hydration, sebum, barrier function, collagen, melanin, bacteria, hormones or age.
- Never infer age, gender, ethnicity, identity, pregnancy, or medical history — but you MAY add general cautions such as "if you are pregnant or breastfeeding, avoid retinol and consult a doctor".
`.trim();
