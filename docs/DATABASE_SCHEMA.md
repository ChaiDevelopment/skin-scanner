# Database schema (Cloud Firestore + Storage)

```
users/{uid}
users/{uid}/skin_analyses/{analysisId}
users/{uid}/preferences/{preferenceId}
Storage: users/{uid}/scans/{analysisId}.jpg      (only when the user opted in)
```

## users/{uid}
Created by the app at registration and by the backend on the first analysis (transaction, merge-safe).
| Field | Type | Notes |
|---|---|---|
| uid | string | equals the document id |
| displayName | string | ≤ 80 chars |
| email | string \| null | |
| createdAt | timestamp | server timestamp |
| updatedAt | timestamp | server timestamp |

## users/{uid}/skin_analyses/{analysisId}
Written **only by the backend** after Zod validation of Gemini's output.
| Field | Type | Notes |
|---|---|---|
| analysisId | string (UUID v4) | equals the document id |
| uid | string | owner (also checked on read) |
| createdAt | timestamp | backend clock |
| status | `"completed"` | 422 results are never stored |
| analysisVersion | string | schema version, `"1.0"` |
| model | string | Gemini model used |
| knowledgeVersion | string | dermatology reference version |
| overallSummary | string | ≤ 1200 chars |
| imageQuality | map | `{status, lighting, faceVisibility, limitations[]}` |
| observations | array<map> | `{category, severity, confidence, observation, affectedArea}` ≤ 16 |
| skinAppearance | map | `{texture, visibleRedness, visibleDryness, pigmentation, visibleShine}` |
| recommendations | array<map> | `{category, title, description, keyIngredients[], routineTime, priority, cautions}` ≤ 10 |
| routine | map | `{morning[], evening[]}` |
| limitations | string[] | always contains the "not a medical diagnosis" disclaimer |
| professionalConsultationRecommended | bool | |
| professionalConsultationReason | string | |
| photoStorageEnabled | bool | default false |
| photoStoragePath | string \| null | `users/{uid}/scans/{analysisId}.jpg` |
| feedback | map \| null | `{helpful, accuracyRating, comment, createdAt}` |

**Index:** `createdAt DESC, analysisId DESC` (`firebase/firestore.indexes.json`) for stable cursor pagination.

## users/{uid}/preferences/privacy
| Field | Type |
|---|---|
| storePhotos | bool |
| updatedAt | timestamp |

The device also keeps its preferences locally in DataStore: onboarding done, consent, default photo storage, dev mode. These are excluded from Android backups.

## Never stored
* API keys or tokens.
* Raw uploads. Photos are kept in memory only and re-encoded; a copy is stored only with opt-in.
* Analyses that failed the quality check (422).
