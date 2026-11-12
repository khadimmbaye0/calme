-- Mono-user local placeholder until the authentication strategy is decided
-- (docs/QUESTIONS.md #6). The email is intentionally non-real and the timezone
-- is neutral; both can be replaced when authentication is implemented.
insert into app_user (email, timezone)
select 'local@calme.invalid', 'UTC'
where not exists (select 1 from app_user);
