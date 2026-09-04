CREATE TABLE IF NOT EXISTS property (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    supplier               VARCHAR(16)  NOT NULL,
    supplier_property_code VARCHAR(64)  NOT NULL,
    property_name          VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_property_supplier_code UNIQUE (supplier, supplier_property_code)
);

CREATE TABLE IF NOT EXISTS room (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    property_id        BIGINT       NOT NULL,
    supplier_room_code VARCHAR(64)  NOT NULL,
    room_name          VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_room_property FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT uq_room_property_code UNIQUE (property_id, supplier_room_code)
);
