INSERT INTO room_type (name, capacity, base_price) VALUES
  ('Standard', 2, 2500.00),
  ('Deluxe',   3, 3500.00),
  ('Family',   4, 4500.00),
  ('Villa',    4, 8000.00);

INSERT INTO room (room_number, room_type_id) VALUES
  ('101', 1), ('102', 1),
  ('201', 2), ('202', 2),
  ('301', 3),
  ('V1',  4);
