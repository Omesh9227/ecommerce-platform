import { describe, expect, it } from 'vitest';
import { readToken, isExpired } from '../auth/jwt';
import { fakeToken } from '../test/render';
import { date, money, percentOff, plural, priceParts } from './format';
import { isExpiryPast, luhn, parseExpiry, rules } from './validation';

describe('money', () => {
  it('uses Indian digit grouping', () => {
    expect(money(123456.5)).toBe('₹1,23,456.50');
    expect(money(null)).toBe('—');
  });
  it('splits a price into rupees and paise', () => {
    expect(priceParts(71999.1)).toEqual({ symbol: '₹', whole: '71,999', fraction: '10' });
    expect(priceParts(-500).whole).toBe('-500');
  });
  it('words', () => {
    expect(plural(1, 'item')).toBe('1 item');
    expect(plural(3, 'item')).toBe('3 items');
    expect(percentOff(10)).toBe('10% off');
    expect(percentOff(0)).toBeNull();
  });
  it('shows calendar dates without shifting the day', () => {
    expect(date('2026-10-29')).toContain('29');
    expect(date('2026-10-29')).toContain('Oct');
  });
});

describe('validation', () => {
  it('checks card numbers with Luhn', () => {
    expect(luhn('4242 4242 4242 4242')).toBe(true);
    expect(luhn('4000 0000 0000 0002')).toBe(true);
    expect(luhn('4242 4242 4242 4241')).toBe(false);
    expect(luhn('1234')).toBe(false);
  });
  it('reads expiry dates', () => {
    expect(parseExpiry('12/30')).toEqual({ month: 12, year: 2030 });
    expect(parseExpiry('1/2031')).toEqual({ month: 1, year: 2031 });
    expect(parseExpiry('13/30')).toBeNull();
    expect(parseExpiry('soon')).toBeNull();
    expect(isExpiryPast(9, 2026, new Date('2026-10-25'))).toBe(true);
    expect(isExpiryPast(10, 2026, new Date('2026-10-25'))).toBe(false);
  });
  it('uses the backend’s rules', () => {
    expect(rules.password.test('Password@123')).toBe(true);
    expect(rules.password.test('password123')).toBe(false);
    expect(rules.mobile.test('9876543210')).toBe(true);
    expect(rules.mobile.test('1234567890')).toBe(false);
    expect(rules.pin.test('560001')).toBe(true);
    expect(rules.pin.test('060001')).toBe(false);
    expect(rules.upi.test('omesh@okhdfc')).toBe(true);
    expect(rules.name.test('Anne-Marie O’Neil'.replace('’', "'"))).toBe(true);
  });
});

describe('jwt', () => {
  it('reads the claims user-service puts in the token', () => {
    const claims = readToken(fakeToken({ sub: 'abc', role: 'ADMIN' }))!;
    expect(claims.userId).toBe('abc');
    expect(claims.role).toBe('ADMIN');
    expect(isExpired(claims)).toBe(false);
  });
  it('rejects junk and treats tokens as expired 30 s early', () => {
    expect(readToken('not.a.token')).toBeNull();
    expect(readToken('abc')).toBeNull();
    const soon = readToken(fakeToken({ exp: Math.floor(Date.now() / 1000) + 20 }))!;
    expect(isExpired(soon)).toBe(true);
  });
});
