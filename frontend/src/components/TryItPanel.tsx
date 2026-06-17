import React, { useState, useEffect, useCallback } from 'react';
import {
  X,
  Play,
  Send,
  Lock,
  ChevronDown,
  ChevronRight,
  Loader2,
  RotateCcw,
  Trash2,
  History,
  Inbox,
} from 'lucide-react';
import type { Endpoint } from '@/types/openapi';
import { useStore } from '@/store/useStore';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import { generateExampleValue, resolveSchema } from '@/utils/openapiParser';
import MethodBadge from './MethodBadge';
import StatusBadge from './StatusBadge';
import JsonViewer from './JsonViewer';

interface TryItPanelProps {
  endpoint: Endpoint | null;
}

type ResponseTab = 'body' | 'headers' | 'raw';

const TryItPanel: React.FC<TryItPanelProps> = ({ endpoint }) => {
  const { tryItOpen, toggleTryIt, serverUrl, authToken, setAuthToken, queue, addToQueue, removeFromQueue, history, clearHistory } = useStore();
  const isOnline = useOnlineStatus();

  const [formState, setFormState] = useState({
    pathParams: {} as Record<string, string>,
    queryParams: {} as Record<string, string>,
    headers: {} as Record<string, string>,
    body: '',
  });
  const [response, setResponse] = useState<{
    status: number;
    statusText: string;
    headers: Record<string, string>;
    body: string;
    time: number;
    size: number;
  } | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [responseTab, setResponseTab] = useState<ResponseTab>('body');
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(['auth', 'path', 'body']));
  const [showHistory, setShowHistory] = useState(false);
  const [bodyValid, setBodyValid] = useState(true);
  const [bodyError, setBodyError] = useState('');

  // Initialize form when endpoint changes
  useEffect(() => {
    if (!endpoint) return;

    const newState = {
      pathParams: {} as Record<string, string>,
      queryParams: {} as Record<string, string>,
      headers: {} as Record<string, string>,
      body: '',
    };

    if (endpoint.operation.parameters) {
      for (const param of endpoint.operation.parameters) {
        if (param.in === 'path') {
          newState.pathParams[param.name] = '';
        } else if (param.in === 'query') {
          newState.queryParams[param.name] = '';
        }
      }
    }

    if (endpoint.operation.requestBody?.content?.['application/json']?.schema) {
      const schema = resolveSchema(endpoint.operation.requestBody.content['application/json'].schema);
      if (schema) {
        const example = generateExampleValue(schema);
        newState.body = JSON.stringify(example, null, 2);
      }
    }

    setFormState(newState);
    setResponse(null);
    setError(null);
  }, [endpoint]);

  // Validate JSON body
  useEffect(() => {
    if (!formState.body) {
      setBodyValid(true);
      setBodyError('');
      return;
    }
    try {
      JSON.parse(formState.body);
      setBodyValid(true);
      setBodyError('');
    } catch (e: unknown) {
      setBodyValid(false);
      setBodyError(e instanceof Error ? e.message : 'Invalid JSON');
    }
  }, [formState.body]);

  const toggleSection = useCallback((section: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(section)) next.delete(section);
      else next.add(section);
      return next;
    });
  }, []);

  const buildUrl = useCallback(() => {
    if (!endpoint) return '';
    let path = endpoint.path;
    for (const [key, value] of Object.entries(formState.pathParams)) {
      path = path.replace(`{${key}}`, encodeURIComponent(value));
    }
    const url = new URL(path, serverUrl);
    for (const [key, value] of Object.entries(formState.queryParams)) {
      if (value) url.searchParams.set(key, value);
    }
    return url.toString();
  }, [endpoint, serverUrl, formState.pathParams, formState.queryParams]);

  const sendRequest = useCallback(async () => {
    if (!endpoint) return;
    setIsLoading(true);
    setError(null);
    setResponse(null);

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...formState.headers,
    };
    if (authToken) {
      headers['Authorization'] = `Bearer ${authToken}`;
    }

    const url = buildUrl();
    const body = ['post', 'put', 'patch'].includes(endpoint.method) && formState.body
      ? formState.body
      : undefined;

    const startTime = performance.now();
    try {
      const res = await fetch(url, {
        method: endpoint.method.toUpperCase(),
        headers,
        body,
      });
      const endTime = performance.now();
      const responseBody = await res.text();
      const responseHeaders: Record<string, string> = {};
      res.headers.forEach((v, k) => { responseHeaders[k] = v; });

      setResponse({
        status: res.status,
        statusText: res.statusText,
        headers: responseHeaders,
        body: responseBody,
        time: Math.round(endTime - startTime),
        size: new Blob([responseBody]).size,
      });

      // Add to history
      useStore.getState().addHistory({
        id: `${Date.now()}-${Math.random()}`,
        timestamp: Date.now(),
        endpointId: endpoint.id,
        method: endpoint.method,
        path: endpoint.path,
        status: res.status,
        time: Math.round(endTime - startTime),
      });
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : 'Network error';
      setError(errorMsg);

      if (!isOnline) {
        addToQueue({
          id: `${Date.now()}-${Math.random()}`,
          timestamp: Date.now(),
          endpointId: endpoint.id,
          method: endpoint.method,
          path: endpoint.path,
          serverUrl,
          headers,
          body: body || '',
          pathParams: formState.pathParams,
          queryParams: formState.queryParams,
          status: 'pending',
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [endpoint, formState, authToken, serverUrl, buildUrl, isOnline, addToQueue]);

  // Keyboard shortcut for send
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'Enter' && tryItOpen && endpoint) {
        sendRequest();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [tryItOpen, endpoint, sendRequest]);

  if (!tryItOpen) return null;

  const pathParamsList = endpoint?.operation.parameters?.filter((p) => p.in === 'path') || [];
  const queryParamsList = endpoint?.operation.parameters?.filter((p) => p.in === 'query') || [];
  const hasBody = ['post', 'put', 'patch'].includes(endpoint?.method || '') &&
    endpoint?.operation.requestBody;

  const formatJson = () => {
    try {
      const parsed = JSON.parse(formState.body);
      setFormState((prev) => ({ ...prev, body: JSON.stringify(parsed, null, 2) }));
    } catch { /* ignore */ }
  };

  return (
    <aside className="fixed right-0 top-12 h-[calc(100vh-48px)] w-full sm:w-[420px] bg-slate-900 border-l border-slate-700 overflow-y-auto z-40 shadow-2xl">
      {/* Panel Header */}
      <div className="sticky top-0 bg-slate-900 border-b border-slate-700 px-4 py-3 flex items-start justify-between z-10">
        <div>
          <div className="flex items-center gap-2">
            <Play size={18} className="text-emerald-400" />
            <h2 className="text-lg font-semibold text-slate-100">Try It</h2>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">Test this endpoint live</p>
        </div>
        <button
          onClick={toggleTryIt}
          className="p-1.5 rounded hover:bg-slate-800 transition-colors"
        >
          <X size={18} className="text-slate-400" />
        </button>
      </div>

      {!endpoint ? (
        <div className="flex flex-col items-center justify-center h-64 text-slate-500">
          <Send size={32} className="mb-3 opacity-50" />
          <p className="text-sm">Select an endpoint to try it</p>
        </div>
      ) : (
        <div className="p-4 space-y-4">
          {/* Endpoint Info */}
          <div className="flex items-center gap-2 pb-3 border-b border-slate-700/50">
            <MethodBadge method={endpoint.method} size="sm" />
            <span className="text-xs font-mono text-slate-300 truncate">{endpoint.path}</span>
          </div>

          {/* Server */}
          <div>
            <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-1.5">
              Server
            </label>
            <select
              value={serverUrl}
              onChange={(e) => useStore.getState().setServerUrl(e.target.value)}
              className="w-full h-9 px-3 bg-slate-800 border border-slate-700 rounded-md text-sm text-slate-200 focus:outline-none focus:border-blue-500"
            >
              <option value="http://localhost:8080">Local (8080)</option>
              <option value="http://localhost:8081">Docker (8081)</option>
            </select>
          </div>

          {/* Authentication */}
          <div className="border border-slate-700/50 rounded-lg overflow-hidden">
            <button
              onClick={() => toggleSection('auth')}
              className="w-full flex items-center gap-2 px-3 py-2.5 bg-slate-800/50 hover:bg-slate-800 transition-colors text-left"
            >
              {expandedSections.has('auth') ? <ChevronDown size={14} className="text-slate-400" /> : <ChevronRight size={14} className="text-slate-400" />}
              <Lock size={14} className="text-slate-400" />
              <span className="text-sm font-medium text-slate-200">Authentication</span>
              <span className="ml-auto text-[10px] bg-blue-500/10 text-blue-400 px-1.5 py-0.5 rounded">Bearer</span>
            </button>
            {expandedSections.has('auth') && (
              <div className="p-3">
                <textarea
                  value={authToken}
                  onChange={(e) => setAuthToken(e.target.value)}
                  placeholder="Bearer eyJhbGciOiJ..."
                  className="w-full h-16 px-3 py-2 bg-slate-950 border border-slate-700 rounded-md text-xs font-mono text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-blue-500 resize-none"
                />
              </div>
            )}
          </div>

          {/* Path Parameters */}
          {pathParamsList.length > 0 && (
            <div className="border border-slate-700/50 rounded-lg overflow-hidden">
              <button
                onClick={() => toggleSection('path')}
                className="w-full flex items-center gap-2 px-3 py-2.5 bg-slate-800/50 hover:bg-slate-800 transition-colors text-left"
              >
                {expandedSections.has('path') ? <ChevronDown size={14} className="text-slate-400" /> : <ChevronRight size={14} className="text-slate-400" />}
                <span className="text-sm font-medium text-slate-200">Path Parameters</span>
                <span className="ml-auto text-[11px] font-mono text-slate-500 bg-slate-800 px-1.5 py-0.5 rounded-full">{pathParamsList.length}</span>
              </button>
              {expandedSections.has('path') && (
                <div className="p-3 space-y-2.5">
                  {pathParamsList.map((param) => (
                    <div key={param.name}>
                      <label className="flex items-center gap-1.5 text-xs text-slate-300 mb-1">
                        <span className="font-mono text-cyan-400">{param.name}</span>
                        {param.required && <span className="text-red-400">*</span>}
                        {param.schema && 'format' in param.schema && param.schema.format === 'uuid' && (
                          <span className="text-[10px] text-slate-500 bg-slate-800 px-1 rounded">UUID</span>
                        )}
                      </label>
                      <input
                        type="text"
                        value={formState.pathParams[param.name] || ''}
                        onChange={(e) => setFormState((prev) => ({
                          ...prev,
                          pathParams: { ...prev.pathParams, [param.name]: e.target.value },
                        }))}
                        placeholder={param.schema && 'format' in param.schema && param.schema.format === 'uuid'
                          ? '550e8400-e29b-41d4-a716-446655440000'
                          : param.name
                        }
                        className="w-full h-9 px-3 bg-slate-950 border border-slate-700 rounded-md text-sm font-mono text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-blue-500 transition-colors"
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Query Parameters */}
          {queryParamsList.length > 0 && (
            <div className="border border-slate-700/50 rounded-lg overflow-hidden">
              <button
                onClick={() => toggleSection('query')}
                className="w-full flex items-center gap-2 px-3 py-2.5 bg-slate-800/50 hover:bg-slate-800 transition-colors text-left"
              >
                {expandedSections.has('query') ? <ChevronDown size={14} className="text-slate-400" /> : <ChevronRight size={14} className="text-slate-400" />}
                <span className="text-sm font-medium text-slate-200">Query Parameters</span>
                <span className="ml-auto text-[11px] font-mono text-slate-500 bg-slate-800 px-1.5 py-0.5 rounded-full">{queryParamsList.length}</span>
              </button>
              {expandedSections.has('query') && (
                <div className="p-3 space-y-2.5">
                  {queryParamsList.map((param) => (
                    <div key={param.name}>
                      <label className="flex items-center gap-1.5 text-xs text-slate-300 mb-1">
                        <span className="font-mono text-cyan-400">{param.name}</span>
                        {param.required && <span className="text-red-400">*</span>}
                      </label>
                      <input
                        type="text"
                        value={formState.queryParams[param.name] || ''}
                        onChange={(e) => setFormState((prev) => ({
                          ...prev,
                          queryParams: { ...prev.queryParams, [param.name]: e.target.value },
                        }))}
                        placeholder={param.description || param.name}
                        className="w-full h-9 px-3 bg-slate-950 border border-slate-700 rounded-md text-sm font-mono text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-blue-500 transition-colors"
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Request Body */}
          {hasBody && (
            <div className="border border-slate-700/50 rounded-lg overflow-hidden">
              <button
                onClick={() => toggleSection('body')}
                className="w-full flex items-center gap-2 px-3 py-2.5 bg-slate-800/50 hover:bg-slate-800 transition-colors text-left"
              >
                {expandedSections.has('body') ? <ChevronDown size={14} className="text-slate-400" /> : <ChevronRight size={14} className="text-slate-400" />}
                <span className="text-sm font-medium text-slate-200">Request Body</span>
                <span className="ml-auto text-[10px] bg-slate-800 text-slate-500 px-1.5 py-0.5 rounded">JSON</span>
              </button>
              {expandedSections.has('body') && (
                <div className="p-3">
                  <textarea
                    value={formState.body}
                    onChange={(e) => setFormState((prev) => ({ ...prev, body: e.target.value }))}
                    className="w-full min-h-[200px] px-3 py-2 bg-slate-950 border border-slate-700 rounded-md text-xs font-mono text-slate-200 leading-relaxed focus:outline-none focus:border-blue-500 resize-y"
                  />
                  <div className="flex items-center justify-between mt-2">
                    <div className="flex items-center gap-1.5">
                      {bodyValid ? (
                        <>
                          <span className="w-2 h-2 rounded-full bg-emerald-400" />
                          <span className="text-xs text-emerald-400">Valid JSON</span>
                        </>
                      ) : (
                        <>
                          <span className="w-2 h-2 rounded-full bg-red-400" />
                          <span className="text-xs text-red-400">{bodyError}</span>
                        </>
                      )}
                    </div>
                    <button
                      onClick={formatJson}
                      className="text-xs text-blue-400 hover:text-blue-300 transition-colors"
                    >
                      Format JSON
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Send Button */}
          <button
            onClick={sendRequest}
            disabled={isLoading || !bodyValid}
            className={`w-full h-11 flex items-center justify-center gap-2 rounded-lg font-semibold text-sm transition-all ${
              isLoading
                ? 'bg-amber-500 text-white cursor-wait'
                : !bodyValid
                ? 'bg-slate-700 text-slate-500 cursor-not-allowed'
                : 'bg-emerald-500 hover:bg-emerald-600 text-white shadow-sm'
            }`}
          >
            {isLoading ? (
              <>
                <Loader2 size={16} className="animate-spin" />
                Sending...
              </>
            ) : (
              <>
                <Send size={16} />
                Send Request
              </>
            )}
          </button>

          {/* Keyboard hint */}
          <p className="text-[11px] text-slate-600 text-center">Ctrl+Enter to send</p>

          {/* Error Banner */}
          {error && (
            <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-lg">
              <p className="text-sm text-red-400 font-medium mb-1">Request Failed</p>
              <p className="text-xs text-red-300">{error}</p>
              {!isOnline && (
                <p className="text-xs text-amber-400 mt-2">
                  You appear to be offline. Request has been queued.
                </p>
              )}
              <button
                onClick={sendRequest}
                className="mt-2 flex items-center gap-1 text-xs text-blue-400 hover:text-blue-300 transition-colors"
              >
                <RotateCcw size={12} />
                Retry
              </button>
            </div>
          )}

          {/* Response Section */}
          {response && (
            <div className="border border-slate-700/50 rounded-lg overflow-hidden">
              {/* Response Header */}
              <div className="flex items-center gap-3 px-3 py-2.5 bg-slate-800/50 border-b border-slate-700/50">
                <StatusBadge status={response.status} text={response.statusText} />
                <span className={`text-xs font-mono ${
                  response.time < 500 ? 'text-emerald-400' : response.time < 1500 ? 'text-amber-400' : 'text-red-400'
                }`}>
                  {response.time}ms
                </span>
                <span className="text-xs font-mono text-slate-500">
                  {(response.size / 1024).toFixed(1)} KB
                </span>
              </div>

              {/* Response Tabs */}
              <div className="flex border-b border-slate-700/50">
                {(['body', 'headers', 'raw'] as const).map((tab) => (
                  <button
                    key={tab}
                    onClick={() => setResponseTab(tab)}
                    className={`flex-1 py-2 text-xs font-medium uppercase tracking-wider transition-colors ${
                      responseTab === tab
                        ? 'text-blue-400 border-b-2 border-blue-400 bg-blue-500/5'
                        : 'text-slate-500 hover:text-slate-300 hover:bg-slate-800/30'
                    }`}
                  >
                    {tab}
                  </button>
                ))}
              </div>

              {/* Response Content */}
              <div className="p-3">
                {responseTab === 'body' && (
                  <JsonViewer data={response.body} maxHeight="300px" />
                )}
                {responseTab === 'headers' && (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left">
                      <tbody>
                        {Object.entries(response.headers).map(([key, value]) => (
                          <tr key={key} className="border-b border-slate-700/30">
                            <td className="py-1.5 px-2 font-mono text-xs text-cyan-400 whitespace-nowrap">{key}</td>
                            <td className="py-1.5 px-2 font-mono text-xs text-slate-300 break-all">{value}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                {responseTab === 'raw' && (
                  <pre className="text-xs font-mono text-slate-300 whitespace-pre-wrap break-all max-h-[300px] overflow-auto">
                    {response.body}
                  </pre>
                )}
              </div>
            </div>
          )}

          {/* Request History */}
          {history.length > 0 && (
            <div className="border border-slate-700/50 rounded-lg overflow-hidden">
              <button
                onClick={() => setShowHistory(!showHistory)}
                className="w-full flex items-center gap-2 px-3 py-2.5 bg-slate-800/50 hover:bg-slate-800 transition-colors text-left"
              >
                {showHistory ? <ChevronDown size={14} className="text-slate-400" /> : <ChevronRight size={14} className="text-slate-400" />}
                <History size={14} className="text-slate-400" />
                <span className="text-sm font-medium text-slate-200">Recent Requests</span>
                <span className="ml-auto text-[11px] font-mono text-slate-500 bg-slate-800 px-1.5 py-0.5 rounded-full">{history.length}</span>
              </button>
              {showHistory && (
                <div className="p-2 max-h-48 overflow-y-auto">
                  {history.slice(0, 10).map((item) => (
                    <div key={item.id} className="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-slate-800/50 transition-colors">
                      <MethodBadge method={item.method} size="sm" />
                      <span className="flex-1 text-xs font-mono text-slate-400 truncate">{item.path}</span>
                      <span className={`text-[10px] font-mono ${
                        item.status >= 200 && item.status < 300 ? 'text-emerald-400' : 'text-red-400'
                      }`}>
                        {item.status}
                      </span>
                    </div>
                  ))}
                  <button
                    onClick={clearHistory}
                    className="w-full mt-1 py-1 text-xs text-red-400 hover:text-red-300 transition-colors text-center"
                  >
                    Clear History
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Offline Queue */}
          {queue.length > 0 && (
            <div className="border border-amber-500/30 rounded-lg overflow-hidden">
              <div className="flex items-center gap-2 px-3 py-2.5 bg-amber-500/10">
                <Inbox size={14} className="text-amber-400" />
                <span className="text-sm font-medium text-amber-400">Offline Queue</span>
                <span className="ml-auto text-[11px] font-mono text-amber-400 bg-amber-500/20 px-1.5 py-0.5 rounded-full">{queue.length}</span>
              </div>
              <div className="p-2 max-h-48 overflow-y-auto">
                {queue.map((item) => (
                  <div key={item.id} className="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-slate-800/50 transition-colors">
                    <MethodBadge method={item.method} size="sm" />
                    <span className="flex-1 text-xs font-mono text-slate-400 truncate">{item.path}</span>
                    <span className="text-[10px] text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded">
                      {item.status}
                    </span>
                    <button
                      onClick={() => removeFromQueue(item.id)}
                      className="p-1 rounded hover:bg-slate-700 transition-colors"
                      title="Remove"
                    >
                      <Trash2 size={12} className="text-slate-500 hover:text-red-400" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </aside>
  );
};

export default TryItPanel;
