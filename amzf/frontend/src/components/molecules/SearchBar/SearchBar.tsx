import React, { useState, useRef, useEffect } from 'react';
import { Search, X } from 'lucide-react';
import { Input } from '../atoms/Input/Input';
import './SearchBar.css';

/**
 * SearchBar option type
 */
export interface SearchOption {
  value: string;
  label: string;
  description?: string;
}

/**
 * SearchBar component props
 */
export interface SearchBarProps {
  /**
   * Placeholder text
   * @default 'Search...'
   */
  placeholder?: string;

  /**
   * Search value
   */
  value?: string;

  /**
   * Change handler
   */
  onChange?: (value: string) => void;

  /**
   * Select handler when an option is selected
   */
  onSelect?: (option: SearchOption) => void;

  /**
   * Autocomplete options
   */
  options?: SearchOption[];

  /**
   * Whether to show autocomplete
   * @default true
   */
  showAutocomplete?: boolean;

  /**
   * Loading state
   * @default false
   */
  loading?: boolean;

  /**
   * Full width
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * SearchBar Component
 *
 * A search input with autocomplete functionality.
 * Combines Input atom with dropdown suggestions.
 *
 * @example
 * ```tsx
 * <SearchBar
 *   placeholder="Search symbols..."
 *   options={symbols}
 *   onSelect={handleSymbolSelect}
 * />
 * ```
 */
export const SearchBar: React.FC<SearchBarProps> = ({
  placeholder = 'Search...',
  value: controlledValue,
  onChange,
  onSelect,
  options = [],
  showAutocomplete = true,
  loading = false,
  fullWidth = false,
  className = '',
}) => {
  const [internalValue, setInternalValue] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [focusedIndex, setFocusedIndex] = useState(-1);
  const wrapperRef = useRef<HTMLDivElement>(null);

  const value = controlledValue !== undefined ? controlledValue : internalValue;

  // Filter options based on search value
  const filteredOptions = options.filter(
    (option) =>
      option.label.toLowerCase().includes(value.toLowerCase()) ||
      option.value.toLowerCase().includes(value.toLowerCase()) ||
      option.description?.toLowerCase().includes(value.toLowerCase())
  );

  const showDropdown = showAutocomplete && isOpen && value.length > 0 && filteredOptions.length > 0;

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setInternalValue(newValue);
    setIsOpen(true);
    setFocusedIndex(-1);
    if (onChange) {
      onChange(newValue);
    }
  };

  const handleClear = () => {
    setInternalValue('');
    setIsOpen(false);
    if (onChange) {
      onChange('');
    }
  };

  const handleSelectOption = (option: SearchOption) => {
    setInternalValue(option.label);
    setIsOpen(false);
    if (onSelect) {
      onSelect(option);
    }
    if (onChange) {
      onChange(option.label);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!showDropdown) return;

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setFocusedIndex((prev) => (prev < filteredOptions.length - 1 ? prev + 1 : prev));
        break;
      case 'ArrowUp':
        e.preventDefault();
        setFocusedIndex((prev) => (prev > 0 ? prev - 1 : 0));
        break;
      case 'Enter':
        e.preventDefault();
        if (focusedIndex >= 0 && focusedIndex < filteredOptions.length) {
          const selectedOption = filteredOptions[focusedIndex];
          if (selectedOption) {
            handleSelectOption(selectedOption);
          }
        }
        break;
      case 'Escape':
        setIsOpen(false);
        setFocusedIndex(-1);
        break;
    }
  };

  const classNames = ['searchbar', fullWidth && 'searchbar--full-width', className]
    .filter(Boolean)
    .join(' ');

  return (
    <div ref={wrapperRef} className={classNames}>
      <Input
        type="search"
        value={value}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        onFocus={() => setIsOpen(true)}
        placeholder={placeholder}
        fullWidth={fullWidth}
        iconLeft={<Search size={16} />}
        iconRight={
          value.length > 0 ? (
            <button
              type="button"
              className="searchbar__clear"
              onClick={handleClear}
              aria-label="Clear search"
            >
              <X size={16} />
            </button>
          ) : undefined
        }
      />

      {showDropdown && (
        <div className="searchbar__dropdown" role="listbox">
          {loading ? (
            <div className="searchbar__option searchbar__option--loading">Loading...</div>
          ) : (
            filteredOptions.map((option, index) => (
              <button
                key={option.value}
                type="button"
                className={`searchbar__option ${
                  index === focusedIndex ? 'searchbar__option--focused' : ''
                }`}
                onClick={() => handleSelectOption(option)}
                onMouseEnter={() => setFocusedIndex(index)}
                role="option"
                aria-selected={index === focusedIndex}
              >
                <div className="searchbar__option-label">{option.label}</div>
                {option.description && (
                  <div className="searchbar__option-description">{option.description}</div>
                )}
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default SearchBar;
