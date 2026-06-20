import React from 'react';

interface MethodBadgeProps {
  method: string;
  size?: 'sm' | 'md' | 'lg';
}

const METHOD_STYLES: Record<string, { bg: string; text: string; border: string }> = {
  get: { bg: 'bg-blue-500/15', text: 'text-blue-500', border: 'border-blue-500/30' },
  post: { bg: 'bg-emerald-500/15', text: 'text-emerald-500', border: 'border-emerald-500/30' },
  put: { bg: 'bg-amber-500/15', text: 'text-amber-500', border: 'border-amber-500/30' },
  delete: { bg: 'bg-red-500/15', text: 'text-red-500', border: 'border-red-500/30' },
  patch: { bg: 'bg-purple-500/15', text: 'text-purple-500', border: 'border-purple-500/30' },
};

const SIZE_STYLES: Record<string, { padding: string; fontSize: string }> = {
  sm: { padding: 'px-1.5 py-0.5', fontSize: 'text-[11px]' },
  md: { padding: 'px-2 py-0.5', fontSize: 'text-xs' },
  lg: { padding: 'px-3 py-1', fontSize: 'text-xs' },
};

const MethodBadge: React.FC<MethodBadgeProps> = ({ method, size = 'md' }) => {
  const styles = METHOD_STYLES[method.toLowerCase()] || METHOD_STYLES.get;
  const sizeStyles = SIZE_STYLES[size];

  return (
    <span
      className={`inline-flex items-center justify-center font-mono font-bold uppercase rounded ${sizeStyles.padding} ${sizeStyles.fontSize} ${styles.bg} ${styles.text} border ${styles.border}`}
    >
      {method.toUpperCase()}
    </span>
  );
};

export default React.memo(MethodBadge);
