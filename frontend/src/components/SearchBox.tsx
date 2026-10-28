import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { search } from '../api/shop';
import { money } from '../lib/format';
import { useDebounced } from '../lib/useDebounced';

type Option =
  | { kind: 'search'; text: string }
  | { kind: 'product'; text: string; id: string; detail: string }
  | { kind: 'category'; text: string; id: string };

/** The search field with suggestions as you type (products, brands, categories, popular searches). */
export function SearchBox() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [text, setText] = useState(params.get('q') ?? '');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const listId = useId();
  const boxRef = useRef<HTMLFormElement>(null);
  const typed = useDebounced(text.trim(), 150);

  useEffect(() => setText(params.get('q') ?? ''), [params]);

  const suggestions = useQuery({
    queryKey: ['suggest', typed],
    queryFn: ({ signal }) => search.suggest(typed, signal),
    enabled: open,
    staleTime: 30_000,
  });

  const data = suggestions.data;
  const options: Option[] = data
    ? [
        ...data.searches.slice(0, 4).map((s): Option => ({ kind: 'search', text: s })),
        ...data.brands.slice(0, 2).map((b): Option => ({ kind: 'search', text: b })),
        ...data.categories.slice(0, 2).map((c): Option => ({ kind: 'category', text: c.name, id: c.id })),
        ...data.products.slice(0, 5).map(
          (p): Option => ({ kind: 'product', text: p.name, id: p.productId, detail: money(p.sellingPrice) }),
        ),
      ]
    : [];

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!boxRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  function go(option?: Option) {
    setOpen(false);
    setActive(-1);
    if (!option) {
      const q = text.trim();
      navigate(q ? `/search?q=${encodeURIComponent(q)}` : '/search');
    } else if (option.kind === 'product') {
      navigate(`/products/${option.id}`);
    } else if (option.kind === 'category') {
      navigate(`/search?categoryId=${option.id}`);
    } else {
      setText(option.text);
      navigate(`/search?q=${encodeURIComponent(option.text)}`);
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    go(active >= 0 ? options[active] : undefined);
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActive((i) => Math.min(i + 1, options.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((i) => Math.max(i - 1, -1));
    } else if (event.key === 'Escape') {
      setOpen(false);
      setActive(-1);
    }
  }

  const showList = open && options.length > 0;
  return (
    <form ref={boxRef} role="search" onSubmit={onSubmit} className="relative flex-1">
      <label htmlFor={`${listId}-input`} className="sr-only">
        Search products
      </label>
      <div className="flex overflow-hidden rounded-md bg-white focus-within:ring-[3px] focus-within:ring-marigold">
        <input
          id={`${listId}-input`}
          type="search"
          value={text}
          autoComplete="off"
          placeholder="Search phones, shoes, lamps…"
          onChange={(e) => {
            setText(e.target.value);
            setOpen(true);
            setActive(-1);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
          className="w-full bg-transparent px-4 py-2.5 text-[15px] text-ink outline-none placeholder:text-ink-faint"
        />
        <button type="submit" className="bg-marigold px-4 font-semibold text-ink hover:bg-marigold-deep">
          Search
        </button>
      </div>
      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute left-0 right-0 top-full z-30 mt-1 overflow-hidden rounded-md border border-line bg-white py-1 shadow-lg"
        >
          {options.map((option, i) => (
            <li
              key={`${option.kind}-${option.text}-${i}`}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              onMouseDown={(e) => {
                e.preventDefault();
                go(option);
              }}
              onMouseEnter={() => setActive(i)}
              className={`flex cursor-pointer items-center justify-between gap-3 px-4 py-2 text-sm ${i === active ? 'bg-mist' : ''}`}
            >
              <span className="truncate">
                {option.kind === 'category' ? (
                  <>
                    <span className="text-ink-soft">in </span>
                    {option.text}
                  </>
                ) : (
                  option.text
                )}
              </span>
              {option.kind === 'product' && <span className="shrink-0 text-ink-soft">{option.detail}</span>}
            </li>
          ))}
        </ul>
      )}
    </form>
  );
}
