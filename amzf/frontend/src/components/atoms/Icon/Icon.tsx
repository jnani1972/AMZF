import React from 'react';
import { LucideProps, icons } from 'lucide-react';
import './Icon.css';

export type IconName = keyof typeof icons;
export type IconSize = 'sm' | 'md' | 'lg' | 'xl';

export interface IconProps extends Omit<LucideProps, 'size'> {
  name: IconName;
  size?: IconSize | number;
}

const sizeMap: Record<IconSize, number> = {
  sm: 16,
  md: 20,
  lg: 24,
  xl: 32,
};

export const Icon: React.FC<IconProps> = ({ name, size = 'md', className = '', ...props }) => {
  const LucideIcon = icons[name];
  const iconSize = typeof size === 'string' ? sizeMap[size] : size;

  if (!LucideIcon) {
    console.warn(`Icon "${name}" not found`);
    return null;
  }

  return <LucideIcon size={iconSize} className={`icon ${className}`} {...props} />;
};

export default Icon;
