-- A paid order can be cancelled before it ships: its sold stock is returned (Phase 6).
ALTER TABLE reservations DROP CONSTRAINT chk_reservation_status;
ALTER TABLE reservations ADD CONSTRAINT chk_reservation_status
    CHECK (status IN ('RESERVED', 'RELEASED', 'CONFIRMED', 'RETURNED'));

ALTER TABLE stock_movements DROP CONSTRAINT chk_movement_type;
ALTER TABLE stock_movements ADD CONSTRAINT chk_movement_type
    CHECK (movement_type IN ('ADJUSTMENT', 'RESTOCK', 'RESERVE', 'RELEASE', 'SALE', 'RETURN'));
