-- Sets the quantity of a product that is already in the cart (limits are checked by the caller).
--
-- KEYS[1]  cart hash
-- ARGV[1]  productId
-- ARGV[2]  new quantity (> 0)
-- ARGV[3]  cart time-to-live in seconds
--
-- Returns the new quantity, or -1 if the product is not in the cart.

local existing = redis.call('HGET', KEYS[1], ARGV[1])
if not existing then
    return -1
end

local line = cjson.decode(existing)
line['quantity'] = tonumber(ARGV[2])

redis.call('HSET', KEYS[1], ARGV[1], cjson.encode(line))
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
return tonumber(ARGV[2])
