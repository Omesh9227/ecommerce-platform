// The same rules as the backend's, so most mistakes are caught before sending.

export const rules = {
  name: /^[\p{L} .'-]+$/u,
  phone: /^[0-9]{10,15}$/,
  mobile: /^[6-9][0-9]{9}$/,
  email: /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/,
  password: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,64}$/,
  pin: /^[1-9][0-9]{5}$/,
  upi: /^[A-Za-z0-9._-]{2,64}@[A-Za-z]{2,32}$/,
  cardNumber: /^[0-9 -]{12,23}$/,
  cvv: /^[0-9]{3,4}$/,
};

export type Errors<K extends string> = Partial<Record<K, string>>;

export function required(value: string | undefined | null, label: string): string | undefined {
  return value && value.trim() ? undefined : `Enter ${label}`;
}

/** Luhn check, so a typo in a card number is caught before paying. */
export function luhn(number: string): boolean {
  const digits = number.replace(/\D/g, '');
  if (digits.length < 12 || digits.length > 19) return false;
  let sum = 0;
  for (let i = 0; i < digits.length; i++) {
    let d = Number(digits[digits.length - 1 - i]);
    if (i % 2 === 1) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
  }
  return sum % 10 === 0;
}

/** "12/30" → { month: 12, year: 2030 }, or null. */
export function parseExpiry(value: string): { month: number; year: number } | null {
  const match = /^\s*(\d{1,2})\s*\/\s*(\d{2}|\d{4})\s*$/.exec(value);
  if (!match) return null;
  const month = Number(match[1]);
  const year = match[2].length === 2 ? 2000 + Number(match[2]) : Number(match[2]);
  if (month < 1 || month > 12) return null;
  return { month, year };
}

export function isExpiryPast(month: number, year: number, now = new Date()): boolean {
  return year < now.getFullYear() || (year === now.getFullYear() && month < now.getMonth() + 1);
}
