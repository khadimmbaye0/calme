-- Seed the 14 alternance blocks for the local mono-user.
-- Availability defaults remain null in the database: the planner uses explicit,
-- documented defaults per block type until settings editing is implemented.
insert into block (user_id, type, start_date, end_date)
select u.id, seed.type, seed.start_date::date, seed.end_date::date
from app_user u
cross join (
    values
        ('SCHOOL', '2026-11-02', '2026-11-21'),
        ('COMPANY', '2026-11-23', '2026-12-06'),
        ('SCHOOL', '2026-12-07', '2026-12-12'),
        ('COMPANY', '2026-12-14', '2027-01-03'),
        ('SCHOOL', '2027-01-04', '2027-01-09'),
        ('COMPANY', '2027-01-11', '2027-01-24'),
        ('SCHOOL', '2027-01-25', '2027-01-30'),
        ('COMPANY', '2027-02-01', '2027-02-14'),
        ('SCHOOL', '2027-02-15', '2027-02-20'),
        ('COMPANY', '2027-02-22', '2027-03-07'),
        ('SCHOOL', '2027-03-08', '2027-03-13'),
        ('COMPANY', '2027-03-15', '2027-04-11'),
        ('SCHOOL', '2027-04-12', '2027-04-17'),
        ('COMPANY', '2027-04-19', '2027-06-30')
) as seed(type, start_date, end_date)
where lower(u.email) = lower('local@calme.invalid')
  and not exists (select 1 from block);
