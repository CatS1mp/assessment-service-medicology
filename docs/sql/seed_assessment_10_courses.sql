-- Seed large assessment dataset aligned with learning-service seed.
-- 10 courses, each 2-3 sections, each section 2 lessons.
-- Each lesson has 1 assessment with multiple question types.

BEGIN;

CREATE OR REPLACE FUNCTION seeded_uuid(namespace_text text, seq_num bigint)
RETURNS uuid
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT (
        substr(md5(namespace_text || ':' || seq_num::text), 1, 8) || '-' ||
        substr(md5(namespace_text || ':' || seq_num::text), 9, 4) || '-' ||
        substr(md5(namespace_text || ':' || seq_num::text), 13, 4) || '-' ||
        substr(md5(namespace_text || ':' || seq_num::text), 17, 4) || '-' ||
        substr(md5(namespace_text || ':' || seq_num::text), 21, 12)
    )::uuid;
$$;

DO $$
DECLARE
    c_idx int;
    s_idx int;
    l_idx int;
    section_total int;
    lesson_key int;
    course_id_v uuid;
    section_id_v uuid;
    lesson_id_v uuid;
    assessment_id_v uuid;
    q_mcq_id uuid;
    q_fill_id uuid;
    q_short_id uuid;
    q_match_id uuid;
    q_order_id uuid;
    q_hotspot_id uuid;
BEGIN
    FOR c_idx IN 1..10 LOOP
        course_id_v := seeded_uuid('course', c_idx);
        section_total := CASE WHEN mod(c_idx, 2) = 0 THEN 3 ELSE 2 END;

        FOR s_idx IN 1..section_total LOOP
            section_id_v := seeded_uuid('section', c_idx * 10 + s_idx);

            FOR l_idx IN 1..2 LOOP
                lesson_key := c_idx * 100 + s_idx * 10 + l_idx;
                lesson_id_v := seeded_uuid('lesson', lesson_key);
                assessment_id_v := seeded_uuid('assessment', lesson_key);

                q_mcq_id := seeded_uuid('question-mcq', lesson_key);
                q_fill_id := seeded_uuid('question-fill', lesson_key);
                q_short_id := seeded_uuid('question-short', lesson_key);
                q_match_id := seeded_uuid('question-match', lesson_key);
                q_order_id := seeded_uuid('question-order', lesson_key);
                q_hotspot_id := seeded_uuid('question-hotspot', lesson_key);

                INSERT INTO assessments (
                    id,
                    title,
                    description,
                    course_id,
                    section_id,
                    lesson_id,
                    pass_score,
                    time_limit_minutes,
                    status,
                    active,
                    created_at,
                    updated_at
                ) VALUES (
                    assessment_id_v,
                    format('Assessment C%s-S%s-L%s', c_idx, s_idx, l_idx),
                    format('Auto-seeded assessment for Course %s Section %s Lesson %s', c_idx, s_idx, l_idx),
                    course_id_v,
                    section_id_v,
                    lesson_id_v,
                    18,
                    25,
                    'PUBLISHED',
                    true,
                    now(),
                    now()
                )
                ON CONFLICT (id) DO UPDATE SET
                    title = EXCLUDED.title,
                    description = EXCLUDED.description,
                    course_id = EXCLUDED.course_id,
                    section_id = EXCLUDED.section_id,
                    lesson_id = EXCLUDED.lesson_id,
                    pass_score = EXCLUDED.pass_score,
                    time_limit_minutes = EXCLUDED.time_limit_minutes,
                    status = EXCLUDED.status,
                    active = EXCLUDED.active,
                    updated_at = now();

                INSERT INTO questions (id, assessment_id, content, explanation, type, display_order, points, active, payload, answer_key, version)
                VALUES
                (
                    q_mcq_id,
                    assessment_id_v,
                    format('C%s-S%s-L%s: First action for unconscious patient?', c_idx, s_idx, l_idx),
                    'Check responsiveness first.',
                    'SINGLE_CHOICE',
                    1,
                    5,
                    true,
                    '{"prompt":"What is the first action when finding an unconscious patient?","options":[{"key":"a","label":"Check responsiveness"},{"key":"b","label":"Give water"},{"key":"c","label":"Wait 10 minutes"}]}',
                    '{"correct":"Check responsiveness"}',
                    1
                ),
                (
                    q_fill_id,
                    assessment_id_v,
                    format('C%s-S%s-L%s: Fill ABC priority', c_idx, s_idx, l_idx),
                    'Airway is first.',
                    'FILL_IN_THE_BLANKS',
                    2,
                    5,
                    true,
                    '{"prompt":"Complete: A stands for ___ in ABC","template":"A stands for ___ in ABC"}',
                    '{"correct":"airway"}',
                    1
                ),
                (
                    q_short_id,
                    assessment_id_v,
                    format('C%s-S%s-L%s: Why airway assessment matters?', c_idx, s_idx, l_idx),
                    'Ensure oxygen flow and detect obstruction.',
                    'SHORT_ANSWER',
                    3,
                    5,
                    true,
                    '{"prompt":"Explain why airway assessment is important.","rubric":{"mustMention":["oxygen","obstruction","priority"]}}',
                    '{"reference":"Airway assessment ensures oxygen can reach lungs and detects obstruction early."}',
                    1
                ),
                (
                    q_match_id,
                    assessment_id_v,
                    format('C%s-S%s-L%s: Match emergency terms', c_idx, s_idx, l_idx),
                    'Match term to meaning.',
                    'MATCHING',
                    4,
                    5,
                    true,
                    '{"prompt":"Match each term with its definition.","pairs":[{"left":"Airway","right":"Breathing passage"},{"left":"Pulse","right":"Heart beats per minute"}]}',
                    '{"correct":[{"left":"Airway","right":"Breathing passage"},{"left":"Pulse","right":"Heart beats per minute"}]}',
                    1
                ),
                (
                    q_order_id,
                    assessment_id_v,
                    format('C%s-S%s-L%s: Order first-response steps', c_idx, s_idx, l_idx),
                    'Use stable ordering keys.',
                    'ORDERING',
                    5,
                    5,
                    true,
                    '{"prompt":"Order the steps.","items":[{"stableKey":"k1","text":"Ensure scene safety"},{"stableKey":"k2","text":"Check responsiveness"},{"stableKey":"k3","text":"Call for help"},{"stableKey":"k4","text":"Open airway"}]}',
                    '{"correctOrderKeys":["k1","k2","k3","k4"]}',
                    1
                ),
                (
                    q_hotspot_id,
                    assessment_id_v,
                    format('C%s-S%s-L%s: Identify airway region', c_idx, s_idx, l_idx),
                    'Choose the correct hotspot.',
                    'HOTSPOT_IMAGE',
                    6,
                    5,
                    true,
                    '{"prompt":"Select airway-related region.","imageUrl":"https://upload.wikimedia.org/wikipedia/commons/thumb/8/8f/Upper_and_lower_respiratory_tract-en.svg/1200px-Upper_and_lower_respiratory_tract-en.svg.png","hotspots":[{"label":"Head","id":"h1"},{"label":"Neck/Airway","id":"h2"},{"label":"Arm","id":"h3"}]}',
                    '{"correct":"Neck/Airway"}',
                    1
                )
                ON CONFLICT (id) DO UPDATE SET
                    assessment_id = EXCLUDED.assessment_id,
                    content = EXCLUDED.content,
                    explanation = EXCLUDED.explanation,
                    type = EXCLUDED.type,
                    display_order = EXCLUDED.display_order,
                    points = EXCLUDED.points,
                    active = EXCLUDED.active,
                    payload = EXCLUDED.payload,
                    answer_key = EXCLUDED.answer_key,
                    version = EXCLUDED.version;
            END LOOP;
        END LOOP;
    END LOOP;
END $$;

COMMIT;
