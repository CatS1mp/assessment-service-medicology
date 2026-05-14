# AI Short Answer Grading (Placeholder Guide)

This service now has placeholder-ready integration points for backend AI grading.

## Environment Variables

Configure these variables before enabling real provider calls:

- `AI_PROVIDER` (default: `google-ai-studio`)
- `AI_API_KEY` (required for real AI calls)
- `AI_MODEL` (default: `gemini-2.5-flash`)
- `AI_ENDPOINT` (optional, for custom gateway/proxy endpoint)
- `AI_CONFIDENCE_THRESHOLD` (default: `0.8`)

## Current Behavior

- `SHORT_ANSWER` uses `AiShortAnswerGrader`.
- If confidence is `<= 0.70`, answer is auto-finalized as incorrect with score `0`.
- If confidence is in `(0.70, threshold)`, answer is set to `MANUAL_REVIEW`.
- If confidence is `>= threshold`, answer is `FINALIZED` and can receive integer `awardedPoints` in `[0..maxScore]`.
- Objective question types use `RuleGrader`.

## Prompt Rubric Template (for real provider implementation)

When you wire the real AI provider call, use a rubric-driven prompt:

1. Include question prompt and answer key reference.
2. Include learner answer exactly as submitted.
3. Ask model to return strict JSON:
   - `correct` (boolean)
   - `confidence` (0..1)
   - `awardedPoints` (integer 0..maxScore for partial-credit)
   - `explanation` (string)
   - `suggestedCorrectAnswers` (array, only if incorrect; max 3)
4. Reject non-JSON responses and fallback to `MANUAL_REVIEW`.

## Failure Policy

Set `MANUAL_REVIEW` if any of these happen:

- Provider timeout/error
- Invalid response schema
- Missing confidence field
- Confidence below threshold

This keeps grading deterministic and avoids unsafe auto-finalization.
