UPDATE clinic_room_problem_templates
SET stage_number = CASE
    WHEN problem_number BETWEEN 1 AND 10 THEN 1
    WHEN problem_number BETWEEN 11 AND 20 THEN 2
    WHEN problem_number BETWEEN 21 AND 29 THEN 3
    ELSE 4
END;
