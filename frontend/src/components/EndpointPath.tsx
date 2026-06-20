import React, { useCallback, useState } from 'react';
import { Copy, Check } from 'lucide-react';

interface EndpointPathProps {
  path: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const SIZE_MAP: Record<string, string> = {
  sm: 'text-xs',
  md: 'text-sm',
  lg: 'text-lg',
};

const EndpointPath: React.FC<EndpointPathProps> = ({ path, size = 'md', className = '' }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(path).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }, [path]);

  const parts = path.split(/(\{[^}]+\})/g);

  return (
    <span className={`inline-flex items-center gap-1 font-mono group ${SIZE_MAP[size]} ${className}`}>
      {parts.map((part, i) => {
        if (part.startsWith('{') && part.endsWith('}')) {
          return (
            <span key={i} className="text-cyan-400 bg-cyan-400/10 px-1 rounded">
              {part}
            </span>
          );
        }
        return <span key={i} className="text-slate-100">{part}</span>;
      })}
      <button
        onClick={handleCopy}
        className="opacity-0 group-hover:opacity-100 transition-opacity p-0.5 rounded hover:bg-slate-700 ml-1"
        title="Copy path"
      >
        {copied ? (
          <Check size={14} className="text-emerald-400" />
        ) : (
          <Copy size={14} className="text-slate-400" />
        )}
      </button>
    </span>
  );
};

export default React.memo(EndpointPath);
