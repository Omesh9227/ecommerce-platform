-- Adds quantity to one product in a cart, atomically (Redis runs a script without interruption,
-- so two requests for the same cart can never both pass the limit checks).
--
-- KEYS[1]  cart hash   (field = productId, value = JSON line)
-- ARGV[1]  productId
-- ARGV[2]  line JSON with the product snapshot (its quantity is replaced here)
-- ARGV[3]  quantity to add (> 0)
-- ARGV[4]  max quantity allowed for this product (per-item limit or stock, whichever is lower)
-- ARGV[5]  max number of different products in a cart
-- ARGV[6]  cart time-to-live in seconds
--
-- Returns the new quantity, -1 if the cart already holds the max number of products,
-- or -2 if the new quantity would be over ARGV[4].

local existing = redis.call('HGET', KEYS[1], ARGV[1])
local current = 0
local addedAt = nil

if existing then
    local old = cjson.decode(existing)
    current = tonumber(old['quantity']) or 0
    addedAt = old['addedAt']
elseif redis.call('HLEN', KEYS[1]) >= tonumber(ARGV[5]) then
    return -1
end

local newQuantity = current + tonumber(ARGV[3])
if newQuantity > tonumber(ARGV[4]) then
    return -2
end

local line = cjson.decode(ARGV[2])
line['quantity'] = newQuantity
if addedAt then
    line['addedAt'] = addedAt -- keep the original position in the cart
end

redis.call('HSET', KEYS[1], ARGV[1], cjson.encode(line))
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[6]))
return newQuantity
