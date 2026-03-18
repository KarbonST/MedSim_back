CREATE TABLE clinic_room_templates (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(150) NOT NULL,
    sort_order INTEGER NOT NULL,
    base_income NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uq_clinic_room_templates_code UNIQUE (code)
);

CREATE TABLE clinic_room_problem_templates (
    id BIGSERIAL PRIMARY KEY,
    clinic_room_template_id BIGINT NOT NULL REFERENCES clinic_room_templates(id) ON DELETE CASCADE,
    problem_number INTEGER NOT NULL,
    title VARCHAR(300) NOT NULL,
    severity VARCHAR(30) NOT NULL,
    ignore_penalty NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uq_room_problem_template UNIQUE (clinic_room_template_id, problem_number)
);

CREATE TABLE session_economy_settings (
    id BIGSERIAL PRIMARY KEY,
    game_session_id BIGINT NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    starting_budget NUMERIC(12, 2) NOT NULL,
    stage_time_units INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_session_economy_settings_game_session UNIQUE (game_session_id)
);

CREATE TABLE team_economy_states (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES session_teams(id) ON DELETE CASCADE,
    current_balance NUMERIC(12, 2) NOT NULL,
    current_stage_time_units INTEGER NOT NULL,
    total_income NUMERIC(12, 2) NOT NULL,
    total_expenses NUMERIC(12, 2) NOT NULL,
    total_penalties NUMERIC(12, 2) NOT NULL,
    total_bonuses NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_team_economy_states_team UNIQUE (team_id)
);

CREATE TABLE team_room_states (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES session_teams(id) ON DELETE CASCADE,
    clinic_room_template_id BIGINT NOT NULL REFERENCES clinic_room_templates(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_team_room_state UNIQUE (team_id, clinic_room_template_id)
);

CREATE TABLE team_problem_states (
    id BIGSERIAL PRIMARY KEY,
    team_room_state_id BIGINT NOT NULL REFERENCES team_room_states(id) ON DELETE CASCADE,
    clinic_room_problem_template_id BIGINT NOT NULL REFERENCES clinic_room_problem_templates(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT uq_team_problem_state UNIQUE (team_room_state_id, clinic_room_problem_template_id)
);

CREATE INDEX idx_room_problem_templates_room
    ON clinic_room_problem_templates (clinic_room_template_id, problem_number);

CREATE INDEX idx_session_economy_settings_session
    ON session_economy_settings (game_session_id);

CREATE INDEX idx_team_economy_states_team
    ON team_economy_states (team_id);

CREATE INDEX idx_team_room_states_team
    ON team_room_states (team_id, clinic_room_template_id);

CREATE INDEX idx_team_problem_states_room
    ON team_problem_states (team_room_state_id, status);

INSERT INTO clinic_room_templates (code, name, sort_order, base_income)
VALUES
    ('XRAY', 'Рентген', 1, 3.00),
    ('ULTRASOUND', 'УЗИ', 2, 2.50),
    ('MRI', 'МРТ', 3, 3.00),
    ('EXAM_1', 'Смотровая №1', 4, 1.50),
    ('EXAM_2', 'Смотровая №2', 5, 1.50),
    ('GYNECOLOGY', 'Гинекологический кабинет', 6, 2.50),
    ('PROCEDURE', 'Процедурная / Перевязочная', 7, 2.50),
    ('REGISTRY_HALL', 'Коридор + Регистратура', 8, 2.00),
    ('WOMEN_TOILET', 'Туалет женский', 9, 1.00),
    ('MEN_TOILET', 'Туалет мужской', 10, 1.00);

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 1, 'Водяные разводы на потолке', 'SERIOUS', 0.35 FROM clinic_room_templates WHERE code = 'XRAY';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 2, 'Деффект аппарата (искрение)', 'CRITICAL', 0.55 FROM clinic_room_templates WHERE code = 'XRAY';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 3, 'Испорченные свинцовые накидки', 'SERIOUS', 0.30 FROM clinic_room_templates WHERE code = 'XRAY';

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 4, 'Сломанные рольставни на окнах', 'SERIOUS', 0.25 FROM clinic_room_templates WHERE code = 'ULTRASOUND';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 5, 'Неправильные ведра для отходов', 'SERIOUS', 0.20 FROM clinic_room_templates WHERE code = 'ULTRASOUND';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 6, 'Трещины на поверхностях', 'MINOR', 0.15 FROM clinic_room_templates WHERE code = 'ULTRASOUND';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 7, 'Надорваны кушетки', 'SERIOUS', 0.30 FROM clinic_room_templates WHERE code = 'ULTRASOUND';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 8, 'Нестабильная работа ламп', 'SERIOUS', 0.25 FROM clinic_room_templates WHERE code = 'ULTRASOUND';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 9, 'Мятые, грязные или надорванные простыни', 'MINOR', 0.12 FROM clinic_room_templates WHERE code = 'ULTRASOUND';

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 10, 'Инвалидная коляска создаёт помехи', 'SERIOUS', 0.35 FROM clinic_room_templates WHERE code = 'MRI';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 11, 'Водяные разводы на потолке над оборудованием', 'SERIOUS', 0.30 FROM clinic_room_templates WHERE code = 'MRI';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 12, 'Электронные устройства вызывают помехи', 'CRITICAL', 0.55 FROM clinic_room_templates WHERE code = 'MRI';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 13, 'Катушка лежит прямо на аппарате', 'SERIOUS', 0.28 FROM clinic_room_templates WHERE code = 'MRI';

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 14, 'Лекарства просрочены или отсутствуют', 'CRITICAL', 0.45 FROM clinic_room_templates WHERE code = 'EXAM_1';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 15, 'Градусник без футляра', 'MINOR', 0.10 FROM clinic_room_templates WHERE code = 'EXAM_1';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 16, 'Ведра без маркировок', 'MINOR', 0.12 FROM clinic_room_templates WHERE code = 'EXAM_1';

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 17, 'Лампы не работают', 'SERIOUS', 0.20 FROM clinic_room_templates WHERE code = 'EXAM_2';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 18, 'Искрится УФ-лампа', 'CRITICAL', 0.40 FROM clinic_room_templates WHERE code = 'EXAM_2';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 19, 'Закончились перчатки', 'SERIOUS', 0.18 FROM clinic_room_templates WHERE code = 'EXAM_2';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 20, 'Нерабочий ПК врача, синий экран', 'SERIOUS', 0.28 FROM clinic_room_templates WHERE code = 'EXAM_2';

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 21, 'Ведра для отходов без крышки', 'SERIOUS', 0.16 FROM clinic_room_templates WHERE code = 'GYNECOLOGY';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 22, 'Сломана сидушка стула или отсутствует спинка', 'MINOR', 0.10 FROM clinic_room_templates WHERE code = 'GYNECOLOGY';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 23, 'Сломанный кран, нет горячей воды', 'MINOR', 0.12 FROM clinic_room_templates WHERE code = 'GYNECOLOGY';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 24, 'Закончился антисептик', 'SERIOUS', 0.18 FROM clinic_room_templates WHERE code = 'GYNECOLOGY';

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 25, 'Хлам на полу', 'MINOR', 0.08 FROM clinic_room_templates WHERE code = 'PROCEDURE';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 26, 'Трещина в стене', 'SERIOUS', 0.20 FROM clinic_room_templates WHERE code = 'PROCEDURE';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 27, 'Личные вещи лежат рядом с перевязочными материалами', 'SERIOUS', 0.16 FROM clinic_room_templates WHERE code = 'PROCEDURE';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 28, 'Нужна повторная санитарная проверка помещения', 'SERIOUS', 0.22 FROM clinic_room_templates WHERE code = 'PROCEDURE';

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 29, 'Регистратор не работает', 'CRITICAL', 0.35 FROM clinic_room_templates WHERE code = 'REGISTRY_HALL';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 30, 'Отсутствует план пожарной безопасности', 'SERIOUS', 0.22 FROM clinic_room_templates WHERE code = 'REGISTRY_HALL';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 31, 'Отсутствует рециркулятор', 'SERIOUS', 0.24 FROM clinic_room_templates WHERE code = 'REGISTRY_HALL';

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 32, 'Отсутствует туалетная бумага', 'MINOR', 0.08 FROM clinic_room_templates WHERE code = 'WOMEN_TOILET';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 33, 'Протекает раковина', 'MINOR', 0.10 FROM clinic_room_templates WHERE code = 'WOMEN_TOILET';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 34, 'Сломанный кран, нет горячей воды', 'MINOR', 0.12 FROM clinic_room_templates WHERE code = 'WOMEN_TOILET';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 35, 'Замечена мышь или крыса', 'CRITICAL', 0.30 FROM clinic_room_templates WHERE code = 'WOMEN_TOILET';

INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 36, 'Отсутствует мыло', 'MINOR', 0.08 FROM clinic_room_templates WHERE code = 'MEN_TOILET';
INSERT INTO clinic_room_problem_templates (clinic_room_template_id, problem_number, title, severity, ignore_penalty)
SELECT id, 37, 'Протекает унитаз', 'SERIOUS', 0.14 FROM clinic_room_templates WHERE code = 'MEN_TOILET';
