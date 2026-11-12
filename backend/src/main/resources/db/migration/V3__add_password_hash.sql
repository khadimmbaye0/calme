-- Local mono-user session authentication.
-- The password hash is derived from CALME_AUTH_PASSWORD at startup and is never
-- committed. The authentication strategy is session + BCrypt, without 2FA.
alter table app_user add column password_hash text;

comment on table app_user is
    'Compte local mono-utilisateur ; le hash BCrypt est dérivé de CALME_AUTH_PASSWORD au démarrage.';
comment on column app_user.password_hash is
    'Hash BCrypt local, jamais exposé par l''API.';
