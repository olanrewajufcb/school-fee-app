import React, { useCallback, useState } from 'react';
import ReactJson from 'react-json-view';
import { Copy, Check } from 'lucide-react';

interface JsonViewerProps {
  data: unknown;
  className?: string;
  maxHeight?: string;
  key?: string | number;
}

const JsonViewer: React.FC<JsonViewerProps> = ({
  data,
  className = '',
  maxHeight = '400px',
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(() => {
    const text = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }, [data]);

  const jsonData = typeof data === 'string' ? (() => {
    try { return JSON.parse(data); } catch { return data; }
  })() : data;

  return (
    <div className={`relative bg-slate-950 border border-slate-700 rounded-md ${className}`}>
      <button
        onClick={handleCopy}
        className="absolute top-2 right-2 z-10 p-1.5 rounded hover:bg-slate-800 transition-colors"
        title="Copy JSON"
      >
        {copied ? (
          <Check size={14} className="text-emerald-400" />
        ) : (
          <Copy size={14} className="text-slate-400" />
        )}
      </button>
      <div className="overflow-auto" style={{ maxHeight }}>
        <ReactJson
          src={jsonData as object}
          theme="monokai"
          collapsed={2}
          collapseStringsAfterLength={80}
          enableClipboard={false}
          displayDataTypes={false}
          displayObjectSize={true}
          indentWidth={2}
          style={{
            backgroundColor: 'transparent',
            fontFamily: "'JetBrains Mono Variable', monospace",
            fontSize: '13px',
            padding: '16px',
          }}
        />
      </div>
    </div>
  );
};

export default React.memo(JsonViewer);
