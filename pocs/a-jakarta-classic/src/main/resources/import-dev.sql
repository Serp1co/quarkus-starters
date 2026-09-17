-- Dev-mode seed data, loaded by %dev.quarkus.hibernate-orm.sql-load-script. Never reaches an environment.
INSERT INTO account (iban, holder, balance, created_at, version) VALUES ('IT60X0542811101000000123456', 'Mario Rossi', 2500.00, now(), 0);
INSERT INTO account (iban, holder, balance, created_at, version) VALUES ('IT35X0100003200000000123456', 'Anna Bianchi', 120.00, now(), 0);
INSERT INTO account (iban, holder, balance, created_at, version) VALUES ('IT03X0542811101000000777777', 'Tesoreria', 1000000.00, now(), 0);
