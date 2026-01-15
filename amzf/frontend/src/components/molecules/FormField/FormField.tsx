import { forwardRef } from 'react';
import { Input, InputProps } from '../atoms/Input';
import './FormField.css';

/**
 * FormField component props (extends Input props)
 */
export type FormFieldProps = InputProps;

/**
 * FormField Component
 *
 * A wrapper around Input that provides consistent form field styling.
 * This is essentially an alias for Input with form-specific styling.
 *
 * @example
 * ```tsx
 * <FormField
 *   label="Email"
 *   type="email"
 *   placeholder="Enter your email"
 *   required
 *   error={errors.email}
 * />
 * ```
 */
export const FormField = forwardRef<HTMLInputElement, FormFieldProps>((props, ref) => {
  return <Input ref={ref} {...props} className={`form-field ${props.className || ''}`} />;
});

FormField.displayName = 'FormField';

export default FormField;
