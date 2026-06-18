DELETE FROM user_active_items
WHERE item_type = 'DEFEAT_EFFECT';

DELETE FROM user_items
WHERE item_id IN (
    SELECT id FROM items WHERE item_type = 'DEFEAT_EFFECT'
);

DELETE FROM items
WHERE item_type = 'DEFEAT_EFFECT';
