-- Default Monday-Friday week, used for anyone without a location or personal schedule.
INSERT INTO work_schedule (name, monday, tuesday, wednesday, thursday, friday, saturday, sunday, default_schedule)
VALUES ('Standard (Mon-Fri)', true, true, true, true, true, false, false, true);
