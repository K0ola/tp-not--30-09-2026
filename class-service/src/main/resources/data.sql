-- Jeu de donnees de demonstration (dates relatives pour rester dans le futur)
INSERT INTO fitness_classes (version, name, description, instructor, gym_location, category, level, duration_minutes, max_participants, current_participants, price, date_time, status) VALUES
(0, 'Yoga Vinyasa', 'Enchainement fluide de postures pour tous', 'Marie Dupont', 'Paris', 'YOGA', 'BEGINNER', 60, 15, 0, 25.00, DATEADD('DAY', 3, CURRENT_TIMESTAMP), 'SCHEDULED'),
(0, 'CrossFit WOD', 'Entrainement intensif du jour', 'Karim Benali', 'Lyon', 'CROSSFIT', 'ADVANCED', 45, 10, 0, 35.00, DATEADD('DAY', 4, CURRENT_TIMESTAMP), 'SCHEDULED'),
(0, 'Zumba Party', 'Cardio dansant sur rythmes latins', 'Sofia Martinez', 'Paris', 'ZUMBA', 'INTERMEDIATE', 60, 30, 0, 15.00, DATEADD('DAY', 5, CURRENT_TIMESTAMP), 'SCHEDULED'),
(0, 'Pilates Core', 'Renforcement profond des abdominaux', 'Marie Dupont', 'Marseille', 'PILATES', 'INTERMEDIATE', 45, 12, 0, 20.00, DATEADD('DAY', 6, CURRENT_TIMESTAMP), 'SCHEDULED'),
(0, 'Spinning Endurance', 'Seance de velo indoor longue distance', 'Lucas Petit', 'Lille', 'SPINNING', 'ADVANCED', 90, 20, 0, 60.00, DATEADD('DAY', 7, CURRENT_TIMESTAMP), 'SCHEDULED'),
(0, 'Boxe Debutant', 'Initiation aux techniques de boxe anglaise', 'Karim Benali', 'Paris', 'BOXING', 'BEGINNER', 60, 8, 0, 30.00, DATEADD('DAY', 8, CURRENT_TIMESTAMP), 'SCHEDULED');
