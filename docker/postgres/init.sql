-- One database and one owner per service, created on the first start of an empty volume.
CREATE USER rides WITH PASSWORD 'rides';
CREATE DATABASE rides OWNER rides;

CREATE USER receipts WITH PASSWORD 'receipts';
CREATE DATABASE receipts OWNER receipts;
