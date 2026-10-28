import { describe, expect, it } from 'vitest';
import { validateAddress } from './CheckoutPage';
import { formatCardNumber, formatExpiry, validatePayment } from './PayPage';
import { validateRegistration } from './RegisterPage';

const address = { fullName: 'Omesh Kumar', phone: '9876543210', line1: '12 MG Road', line2: '', city: 'Bengaluru', state: 'Karnataka', postalCode: '560001' };

describe('checkout address', () => {
  it('accepts a complete address', () => expect(validateAddress(address)).toEqual({}));
  it('names every missing or wrong field', () => {
    const errors = validateAddress({ ...address, fullName: ' ', phone: '12345', line1: '', city: '', state: '', postalCode: '012345' });
    expect(Object.keys(errors).sort()).toEqual(['city', 'fullName', 'line1', 'phone', 'postalCode', 'state']);
  });
});

describe('payment form', () => {
  const card = { number: '4242 4242 4242 4242', expiry: '12/30', cvv: '123', holderName: '', upiId: '' };
  it('accepts a valid card and a valid UPI ID', () => {
    expect(validatePayment('CARD', card)).toEqual({});
    expect(validatePayment('UPI', { ...card, upiId: 'omesh@okhdfc' })).toEqual({});
  });
  it('catches typos before paying', () => {
    expect(validatePayment('CARD', { ...card, number: '4242 4242 4242 4241', expiry: '01/20', cvv: '1' })).toEqual({
      number: 'Check the card number',
      expiry: 'This card has expired',
      cvv: '3 or 4 digits',
    });
    expect(validatePayment('UPI', { ...card, upiId: 'omesh' })).toEqual({ upiId: 'Enter a UPI ID like name@bank' });
  });
  it('formats as you type', () => {
    expect(formatCardNumber('4242424242424242')).toBe('4242 4242 4242 4242');
    expect(formatCardNumber('4242-42')).toBe('4242 42');
    expect(formatExpiry('1230')).toBe('12/30');
    expect(formatExpiry('1')).toBe('1');
  });
});

describe('registration', () => {
  const form = { firstName: 'Omesh', lastName: 'Kumar', email: 'omesh@example.com', phoneNumber: '9876543210', password: 'Password@123' };
  it('accepts what user-service accepts', () => expect(validateRegistration(form)).toEqual({}));
  it('explains the password rule', () => {
    expect(validateRegistration({ ...form, password: 'password' }).password).toMatch(/uppercase/);
    expect(validateRegistration({ ...form, email: 'omesh@', phoneNumber: '123' })).toMatchObject({ email: expect.any(String), phoneNumber: expect.any(String) });
  });
});
