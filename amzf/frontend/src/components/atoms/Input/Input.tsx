import { InputHTMLAttributes, ReactNode, forwardRef } from 'react';
import './Input.css';

/**
 * Input component type
 */
export type InputType = 'text' | 'number' | 'email' | 'password' | 'search' | 'tel' | 'url';

/**
 * Input component size
 */
export type InputSize = 'sm' | 'md' | 'lg';

/**
 * Input validation state
 */
export type InputState = 'default' | 'error' | 'success' | 'warning';

/**
 * Input component props
 */
export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  /**
   * Input type
   * @default 'text'
   */
  type?: InputType;

  /**
   * Input size
   * @default 'md'
   */
  size?: InputSize;

  /**
   * Validation state
   * @default 'default'
   */
  state?: InputState;

  /**
   * Whether the input takes full width
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Icon to display at the start of the input
   */
  iconLeft?: ReactNode;

  /**
   * Icon to display at the end of the input
   */
  iconRight?: ReactNode;

  /**
   * Helper text to display below the input
   */
  helperText?: string;

  /**
   * Error message to display (sets state to 'error')
   */
  error?: string;

  /**
   * Success message to display (sets state to 'success')
   */
  success?: string;

  /**
   * Label for the input
   */
  label?: string;

  /**
   * Whether the field is required
   */
  required?: boolean;
}

/**
 * Input Component
 *
 * A versatile input component with validation states, icons, and helper text.
 * Supports multiple types for different data entry needs.
 *
 * @example
 * ```tsx
 * <Input
 *   label="Email"
 *   type="email"
 *   placeholder="Enter your email"
 *   required
 * />
 *
 * <Input
 *   label="Quantity"
 *   type="number"
 *   state="error"
 *   error="Quantity must be positive"
 * />
 * ```
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      type = 'text',
      size = 'md',
      state: stateProp = 'default',
      fullWidth = false,
      iconLeft,
      iconRight,
      helperText,
      error,
      success,
      label,
      required = false,
      disabled = false,
      className = '',
      id,
      ...props
    },
    ref
  ) => {
    // Determine state based on props
    const state = error ? 'error' : success ? 'success' : stateProp;
    const message = error || success || helperText;

    // Generate ID for aria-describedby
    const inputId = id || `input-${Math.random().toString(36).substr(2, 9)}`;
    const messageId = message ? `${inputId}-message` : undefined;

    const wrapperClassNames = [
      'input-wrapper',
      fullWidth && 'input-wrapper--full-width',
      disabled && 'input-wrapper--disabled',
    ]
      .filter(Boolean)
      .join(' ');

    const inputClassNames = [
      'input',
      `input--${size}`,
      `input--${state}`,
      iconLeft && 'input--has-icon-left',
      iconRight && 'input--has-icon-right',
      disabled && 'input--disabled',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div className={wrapperClassNames}>
        {label && (
          <label htmlFor={inputId} className="input__label">
            {label}
            {required && <span className="input__required">*</span>}
          </label>
        )}

        <div className="input__container">
          {iconLeft && <span className="input__icon input__icon--left">{iconLeft}</span>}

          <input
            ref={ref}
            type={type}
            id={inputId}
            className={inputClassNames}
            disabled={disabled}
            aria-invalid={state === 'error'}
            aria-describedby={messageId}
            aria-required={required}
            {...props}
          />

          {iconRight && <span className="input__icon input__icon--right">{iconRight}</span>}
        </div>

        {message && (
          <span id={messageId} className={`input__message input__message--${state}`}>
            {message}
          </span>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';

export default Input;
