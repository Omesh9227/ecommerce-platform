import type { Address } from '../api/types';
import { storage } from './storage';

const KEY = 'cartwheel.address';

/** The last address the customer used on this device, to fill the next checkout. */
export function savedAddress(): Partial<Address> | null {
  try {
    const raw = storage.get(KEY);
    return raw ? (JSON.parse(raw) as Partial<Address>) : null;
  } catch {
    return null;
  }
}

export function saveAddress(address: Address) {
  storage.set(KEY, JSON.stringify(address));
}

export const INDIAN_STATES = [
  'Andaman and Nicobar Islands', 'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chandigarh', 'Chhattisgarh',
  'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jammu and Kashmir',
  'Jharkhand', 'Karnataka', 'Kerala', 'Ladakh', 'Lakshadweep', 'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya',
  'Mizoram', 'Nagaland', 'Odisha', 'Puducherry', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura',
  'Uttar Pradesh', 'Uttarakhand', 'West Bengal',
];
